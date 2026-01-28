package com.project.AirBnb.dto;

import lombok.Value;

@Value
public class LoginResponseDTO {
    Long id;
    String accessToken;
    String refreshToken;
}
