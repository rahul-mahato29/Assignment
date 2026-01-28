package com.project.AirBnb.dto;

import com.project.AirBnb.entities.Hotel;
import lombok.Value;

@Value
public class HotelPriceDTO {
    Hotel hotel;
    Double price;
}
