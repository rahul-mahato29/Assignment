package com.project.AirBnb.services;

import com.project.AirBnb.dto.BookingDTO;
import com.project.AirBnb.dto.BookingRequest;
import com.project.AirBnb.dto.GuestDTO;
import com.project.AirBnb.entities.Booking;
import com.project.AirBnb.entities.enums.BookingStatus;
import com.stripe.model.Event;

import java.util.List;

public interface BookingService {
    BookingDTO initialiseBooking(BookingRequest bookingRequest);

    BookingDTO addGuests(Long bookingId, List<GuestDTO> guestDTOList);

    String initialisePayment(Long bookingId);

    void capturePayments(Event event);

    void cancelBooking(Long bookingId);

//    void updateBookingAmountInNewTransaction(Long bookingId);
//
//    void updateBookingStatusWithStaleRisk(Long bookingId, BookingStatus newStatus);
//
//    void updateBookingStatusStaleFixed(Long bookingId, BookingStatus newStatus);

    void demonstrateUnexpectedFlush(Long bookingId);

    void updateBookingStatusOnly(Long bookingId, BookingStatus status);

    List<Booking> getAllBookingsForDemo();
}
