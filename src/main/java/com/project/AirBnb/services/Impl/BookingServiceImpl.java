package com.project.AirBnb.services.Impl;

import com.project.AirBnb.dto.BookingDTO;
import com.project.AirBnb.dto.BookingRequest;
import com.project.AirBnb.dto.GuestDTO;
import com.project.AirBnb.entities.*;
import com.project.AirBnb.entities.enums.BookingStatus;
import com.project.AirBnb.exceptions.*;
import com.project.AirBnb.repositories.*;
import com.project.AirBnb.services.BookingService;
import com.project.AirBnb.services.CheckoutService;
import com.project.AirBnb.strategy.Pricing.PricingService;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.param.RefundCreateParams;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Propagation; 
//import org.springframework.transaction.annotation.Transactional;   

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final InventoryRepository inventoryRepository;
    private final GuestRepository guestRepository;
    private final ModelMapper modelMapper;
    private final CheckoutService checkoutService;
    private final PricingService pricingService;

//    @Lazy
//    private final BookingService self;

    @Value("${frontend.url}")
    private String frontendUrl;

    @Override
    @Transactional
    public BookingDTO initialiseBooking(BookingRequest bookingRequest) {
        log.info("Initialising booking for hotel : {}, room : {}, date : {} to {}",bookingRequest.getHotelId(),
                bookingRequest.getRoomId(), bookingRequest.getCheckInDate(), bookingRequest.getCheckOutDate());

        validateBookingRequestDates(bookingRequest);

        Hotel hotel = hotelRepository
                .findById(bookingRequest.getHotelId())
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found with ID: "+bookingRequest.getHotelId()));

        Room room = roomRepository
                .findById(bookingRequest.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with ID: "+bookingRequest.getRoomId()));

        validateRoomBelongsToHotel(room, hotel);
        validateRoomsCount(room, bookingRequest.getRoomsCount());

        List<Inventory> inventoryList = inventoryRepository.findAndLockAvailableInventory(
                room.getId(),
                bookingRequest.getCheckInDate(),
                bookingRequest.getCheckOutDate(),
                bookingRequest.getRoomsCount()
        );

        long daysCount = ChronoUnit.DAYS.between(bookingRequest.getCheckInDate(), bookingRequest.getCheckOutDate())+1;

        if(inventoryList.size() != daysCount){
            log.warn("Room unavailable: hotelId={}, roomId={}, checkIn={}, checkOut={}, expectedDays={}, gotInventory={}",
                    bookingRequest.getHotelId(), bookingRequest.getRoomId(),
                    bookingRequest.getCheckInDate(), bookingRequest.getCheckOutDate(), daysCount, inventoryList.size());
            throw new RoomUnavailableException("Room is not available anymore");
        }

        log.info("Checking : {}", bookingRequest.getRoomsCount());

        // Reserve the room/ update the booked count of inventories
        inventoryRepository.initBooking(room.getId(), bookingRequest.getCheckInDate(),
                bookingRequest.getCheckOutDate(), bookingRequest.getRoomsCount());

        // calculate dynamic pricing
        BigDecimal priceForOneRoom = pricingService.calculateTotalPrice(inventoryList);
        BigDecimal totalPrice = priceForOneRoom.multiply(BigDecimal.valueOf(bookingRequest.getRoomsCount()));

        Booking booking = Booking.builder()
                .bookingStatus(BookingStatus.RESERVED)
                .hotel(hotel)
                .room(room)
                .checkInDate(bookingRequest.getCheckInDate())
                .checkOutDate(bookingRequest.getCheckOutDate())
                .user(getCurrentUser())
                .roomCount(bookingRequest.getRoomsCount())
                .amount(totalPrice)
                .build();

         booking = bookingRepository.save(booking);
//        booking = persistBooking(booking);
        return modelMapper.map(booking, BookingDTO.class);
    }


    @Override
    @Transactional
    public BookingDTO addGuests(Long bookingId, List<GuestDTO> guestDTOList) {
        log.info("Adding guests for booking id : {}", bookingId);

        Booking booking = bookingRepository
                .findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found for Id: "+bookingId));

        User user = getCurrentUser();
        if(!user.equals(booking.getUser())) {
            throw new UnAuthorisedException("Booking does not belong to this user with id : "+user.getId());
        }

        if(hasBookingExpired(booking)) {
            throw new BookingExpiredException("Booking has already expired");
        }

        //confirm booking state
        if(booking.getBookingStatus() != BookingStatus.RESERVED) {
            throw new InvalidBookingStateException("Booking is not under reserved state, cannot add guests");
        }

        //add each guest into the booking
        for(GuestDTO guestDTO: guestDTOList) {
            Guest guest = modelMapper.map(guestDTO, Guest.class);
            guest.setUser(getCurrentUser());
            guest = guestRepository.save(guest);

            booking.getGuests().add(guest);
        }

        booking.setBookingStatus(BookingStatus.GUESTS_ADDED);
        booking = bookingRepository.save(booking);

        return modelMapper.map(booking, BookingDTO.class);
    }

    @Override
    public String initialisePayment(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow(
                () -> new ResourceNotFoundException("Booking not found with id : "+ bookingId)
        );

        User user = getCurrentUser();
        if(!user.equals(booking.getUser())) {
            throw new UnAuthorisedException("Booking does not belong to this user with id : "+user.getId());
        }

        if(hasBookingExpired(booking)) {
            throw new BookingExpiredException("Booking has already expired");
        }

        //in frontend, you have to make this frontend-success and failure url
        String sessionUrl = checkoutService.getCheckoutSession(booking, frontendUrl+"/payments/success", frontendUrl+"/payments/failure");

        booking.setBookingStatus(BookingStatus.PAYMENTS_PENDING);
        bookingRepository.save(booking);

        return sessionUrl;
    }

    @Override
    @Transactional
    public void capturePayments(Event event) {
        if("checkout.session.completed".equals(event.getType())) {
            Session session = (Session) event.getDataObjectDeserializer().getObject().orElseThrow(() -> new PaymentProcessingException("Stripe session data not found in event"));
            if(session == null) return;

            String sessionId = session.getId();
            Booking booking = bookingRepository.findByPaymentSessionId(sessionId).orElseThrow(() ->
                    new ResourceNotFoundException("Booking not found for session ID : "+sessionId));

            booking.setBookingStatus(BookingStatus.CONFIRMED);
            bookingRepository.save(booking);

            inventoryRepository.findAndLockReservedInventory(booking.getRoom().getId(), booking.getCheckInDate(),
                    booking.getCheckOutDate(), booking.getRoomCount());

            inventoryRepository.confirmBooking(booking.getRoom().getId(), booking.getCheckInDate(),
                    booking.getCheckOutDate(), booking.getRoomCount());

            log.info("Successfully confirmed the booking for booking Id : {}", booking.getId());
        }
        else {
            log.warn("Unhandled event type : {}", event.getType());
        }
    }

    @Override
    @Transactional
    public void cancelBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow(
                () -> new ResourceNotFoundException("Booking not found with id : "+ bookingId)
        );

        User user = getCurrentUser();
        if(!user.equals(booking.getUser())) {
            throw new UnAuthorisedException("Booking does not belong to this user with id : "+user.getId());
        }

        if(booking.getBookingStatus() != BookingStatus.CONFIRMED) {
            throw new InvalidBookingStateException("Only Confirmed bookings can be cancelled");
        }

        booking.setBookingStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        inventoryRepository.findAndLockReservedInventory(booking.getRoom().getId(), booking.getCheckInDate(),
                booking.getCheckOutDate(), booking.getRoomCount());

        inventoryRepository.cancelBooking(booking.getRoom().getId(), booking.getCheckInDate(),
                booking.getCheckOutDate(), booking.getRoomCount());

        //handle the refund
        try {
            Session session = Session.retrieve(booking.getPaymentSessionId());
            RefundCreateParams refundParams = RefundCreateParams.builder()
                    .setPaymentIntent(session.getPaymentIntent())
                    .build();
        }
        catch (StripeException e) {
            throw new PaymentProcessingException("Failed to process refund for booking with ID : " + booking.getId(), e);
        }

    }

    public boolean hasBookingExpired(Booking booking) {
        return booking.getCreatedAt().plusMinutes(10).isBefore(LocalDateTime.now());
    }

//    public User getCurrentUser() {
//        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
//    }

    private void validateBookingRequestDates(BookingRequest request) {
        if (request.getCheckOutDate() == null || request.getCheckInDate() == null) {
            return;
        }
        if (!request.getCheckOutDate().isAfter(request.getCheckInDate())) {
            throw new InvalidBookingRequestException("Check-out date must be after check-in date");
        }
    }

    private void validateRoomBelongsToHotel(Room room, Hotel hotel) {
        if (room.getHotel() == null || !room.getHotel().getId().equals(hotel.getId())) {
            throw new InvalidBookingRequestException("Room does not belong to the specified hotel");
        }
    }

    private void validateRoomsCount(Room room, Integer roomsCount) {
        if (roomsCount != null && room.getTotalCount() != null && roomsCount > room.getTotalCount()) {
            throw new InvalidBookingRequestException(
                    "Rooms count (" + roomsCount + ") exceeds room capacity (" + room.getTotalCount() + ")");
        }
    }

    public User getCurrentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null || !(auth.getPrincipal() instanceof User)) {
            throw new UnAuthorisedException("Authentication required");
        }
        return (User) auth.getPrincipal();
    }

//    @Transactional
//    private Booking persistBooking(Booking booking) {
//        return bookingRepository.save(booking);
//    }


    //This method commits in its own transaction
//    @Override
//    @Transactional(propagation = Propagation.REQUIRES_NEW)
//    public void updateBookingAmountInNewTransaction(Long bookingId) {
//        Booking booking = bookingRepository.findById(bookingId)
//                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id : " + bookingId));
//        booking.setAmount(booking.getAmount().add(new BigDecimal("0.01")));
//        bookingRepository.save(booking);
//        log.info("Updated booking {} amount in new transaction : ", bookingId);
//    }
//
//    @Override
//    @Transactional
//    public void updateBookingStatusWithStaleRisk(Long bookingId, BookingStatus newStatus) {
//        Booking booking = bookingRepository.findById(bookingId)
//                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id : " + bookingId));
//
//        // updates same row in a NEW transaction and commits
//        self.updateBookingAmountInNewTransaction(bookingId);
//
//        // BUG: We still use the old reference (stale). Our save will overwrite the other writer's amount change.
//        booking.setBookingStatus(newStatus);
//        bookingRepository.save(booking);
//        log.info("Updated booking {} status (stale risk)", bookingId);
//    }
//
//    @Override
//    @Transactional
//    public void updateBookingStatusStaleFixed(Long bookingId, BookingStatus newStatus) {
//        // Let the other writer run first (in its own transaction) and commit
//        self.updateBookingAmountInNewTransaction(bookingId);
//
//        // FIX: Re-load the booking so we have current state, then update
//        Booking booking = bookingRepository.findById(bookingId)
//                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id : " + bookingId));
//        booking.setBookingStatus(newStatus);
//        bookingRepository.save(booking);
//        log.info("Updated booking {} status (fixed - re-loaded after other writer)", bookingId);
//    }

    @Transactional
    public void demonstrateUnexpectedFlush(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id : " + bookingId));

        booking.setBookingStatus(BookingStatus.CANCELLED);

        bookingRepository.findAll();
    }

    @Override
    @Transactional
    public void updateBookingStatusOnly(Long bookingId, BookingStatus status) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id : " + bookingId));
        booking.setBookingStatus(status);
        bookingRepository.save(booking);
        log.info("Updated booking {} status only (no query in this transaction)", bookingId);
    }

    @Override
    @Transactional
    public List<Booking> getAllBookingsForDemo() {
        return bookingRepository.findAll();
    }

    //No Sort is added here, the controller will pass a Pageable with no sort
    @Override
    public Page<BookingDTO> getBookingsPage(Pageable pageable) {
        return bookingRepository.findAll(pageable).map(booking -> modelMapper.map(booking, BookingDTO.class));
    }
}

