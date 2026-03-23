package com.biomechanics.backend.model.dto.auth;

import com.biomechanics.backend.model.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponseDTO {
    private String accessToken;
    private String refreshToken;

    private UserRole role;

    private Long userId;
    private String email;
    private String firstName;
    private String lastName;
}
