package com.biomechanics.backend.model.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequestDTO {

    @NotBlank(message = "Email is mandatory.")
    @Email
    private String email;

    @NotBlank(message = "Password is mandatory.")
    private String password;
}
