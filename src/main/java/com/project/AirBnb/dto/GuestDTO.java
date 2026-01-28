package com.project.AirBnb.dto;

import com.project.AirBnb.entities.User;
import com.project.AirBnb.entities.enums.Gender;
import lombok.Value;

@Value
public class GuestDTO {

    Long id;
    String name;
    Gender gender;
    Integer age;
    User user;
}
