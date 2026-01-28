package com.project.AirBnb.dto;

import com.project.AirBnb.entities.HotelContactInfo;
import com.project.AirBnb.entities.User;
import lombok.Value;

@Value
public class HotelDTO {
    Long id;
    String name;
    String city;
    String[] photos;
    String[] amenities;
    HotelContactInfo contactInfo;
    User owner;
    Boolean isActive;
}
