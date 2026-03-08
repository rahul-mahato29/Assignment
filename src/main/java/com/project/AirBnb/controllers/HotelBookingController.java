package com.project.AirBnb.controllers;

import com.project.AirBnb.dto.BookingDTO;
import com.project.AirBnb.dto.BookingRequest;
import com.project.AirBnb.dto.GuestDTO;
import com.project.AirBnb.entities.Booking;
import com.project.AirBnb.entities.enums.BookingStatus;
import com.project.AirBnb.services.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(path = "/bookings")
@RequiredArgsConstructor
public class HotelBookingController {

    private final BookingService bookingService;

    @PostMapping("/init")
    public ResponseEntity<BookingDTO> initialiseBooking(@Valid @RequestBody BookingRequest bookingRequest) {
        return ResponseEntity.ok(bookingService.initialiseBooking(bookingRequest));
    }

    @PostMapping("/{bookingId}/addGuests")
    public ResponseEntity<BookingDTO> addGuests(@PathVariable Long bookingId,
                                                @RequestBody List<GuestDTO> guestDTOList) {
        return ResponseEntity.ok(bookingService.addGuests(bookingId, guestDTOList));
    }

    @PostMapping("/{bookingId}/payment")
    public ResponseEntity<Map<String, String >> initiatePayment(@PathVariable Long bookingId) {
        String sessionUrl = bookingService.initialisePayment(bookingId);
        return ResponseEntity.ok(Map.of("sessionUrl", sessionUrl));
    }

    @PostMapping("/{bookingId}/cancel")
    public ResponseEntity<Void> cancelBooking(@PathVariable Long bookingId) {
        bookingService.cancelBooking(bookingId);
        return ResponseEntity.noContent().build();
    }

//    @PostMapping("/{bookingId}/demo/stale-risk")
//    public ResponseEntity<String> demoStaleRisk(@PathVariable Long bookingId,
//                                                @RequestParam(defaultValue = "CANCELLED") BookingStatus status) {
//        bookingService.updateBookingStatusWithStaleRisk(bookingId, status);
//        return ResponseEntity.ok("Stale-risk demo done. Check DB: amount should be OLD value.");
//    }
//
//    @PostMapping("/{bookingId}/demo/stale-fixed")
//    public ResponseEntity<String> demoStaleFixed(@PathVariable Long bookingId,
//                                                 @RequestParam(defaultValue = "CANCELLED") BookingStatus status) {
//        bookingService.updateBookingStatusStaleFixed(bookingId, status);
//        return ResponseEntity.ok("Stale-fixed demo done. Check DB: amount should be OLD + 0.01.");
//    }

    @PostMapping("/{bookingId}/flush")
    public ResponseEntity<Void> demoUnexpectedFlush(@PathVariable Long bookingId) {
        bookingService.demonstrateUnexpectedFlush(bookingId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{bookingId}/update-only")
    public ResponseEntity<Void> demoUpdateOnly(@PathVariable Long bookingId,
                                                              @RequestParam(defaultValue = "CANCELLED") BookingStatus status) {
        bookingService.updateBookingStatusOnly(bookingId, status);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/demo/list")
    public ResponseEntity<Map<String, String>> demoListOnly() {
        List<Booking> list = bookingService.getAllBookingsForDemo();
        return ResponseEntity.ok(Map.of("message", "List-only done. Check logs: only SELECT.", "count", String.valueOf(list.size())));
    }

    @GetMapping("/paginated")
    public ResponseEntity<Page<BookingDTO>> getBookingsPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "2") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(bookingService.getBookingsPage(pageable));
    }

    @GetMapping("/paginated/stable")
    public ResponseEntity<Page<BookingDTO>> getBookingsPaginatedStable(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "2") int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by("createdAt").descending().and(Sort.by("id")));
        return ResponseEntity.ok(bookingService.getBookingsPage(pageable));
    }
}
