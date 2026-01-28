package com.project.AirBnb.dto;

import com.project.AirBnb.entities.enums.Role;
import lombok.Value;

import java.util.Set;

@Value
public class UserDTO {
    Long id;
    String name;
    String email;
    Set<Role> roles;
}
