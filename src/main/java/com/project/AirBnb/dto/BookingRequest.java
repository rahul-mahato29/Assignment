package com.project.AirBnb.dto;

import lombok.Data;
import lombok.Value;

import java.time.LocalDate;

@Value
public class BookingRequest {
    Long hotelId;
    Long roomId;
    LocalDate checkInDate;
    LocalDate checkOutDate;
    Integer roomsCount;
}