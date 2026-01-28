package com.project.AirBnb.dto;

import com.project.AirBnb.entities.enums.BookingStatus;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

@Value
public class BookingDTO {

    Long id;
    BookingStatus bookingStatus;
    LocalDate checkInDate;
    LocalDate checkOutDate;
    Integer roomCount;
    Set<GuestDTO> guests;
    BigDecimal amount;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
