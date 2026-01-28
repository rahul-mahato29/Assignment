package com.project.AirBnb.dto;

import lombok.Value;

import java.util.List;

@Value
public class HotelInfoDTO {
    HotelDTO hotel;
    List<RoomDTO> rooms;
}
