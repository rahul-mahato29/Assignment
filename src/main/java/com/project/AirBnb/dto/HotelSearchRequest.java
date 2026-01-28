package com.project.AirBnb.dto;

import lombok.Value;

import java.time.LocalDate;

@Value
public class HotelSearchRequest {
    String city;
    LocalDate startDate; //check-in time
    LocalDate endDate;   //check-out time
    Integer roomsCount;   //no-of-room use
    Integer page=0;
    Integer size=10;
}
