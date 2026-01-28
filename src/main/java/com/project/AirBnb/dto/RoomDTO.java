package com.project.AirBnb.dto;

import lombok.Value;

import java.math.BigDecimal;

@Value
public class RoomDTO {
    Long id;
    String type;
    BigDecimal basePrice;
    String[] photos;
    String[] amenities;
    Integer totalCount;
    Integer capacity;
}
