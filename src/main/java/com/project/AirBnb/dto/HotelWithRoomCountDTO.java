package com.project.AirBnb.dto;

import lombok.Value;

@Value
public class HotelWithRoomCountDTO {
    Long id;
    String name;
    String city;
    Long roomCount;
}
