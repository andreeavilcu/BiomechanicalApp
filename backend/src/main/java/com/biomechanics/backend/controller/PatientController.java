package com.biomechanics.backend.controller;
import com.biomechanics.backend.model.dto.UserDTO;
import com.biomechanics.backend.service.PatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Patient", description = "Operations related to patient's own profile and settings")
@SecurityRequirement(name = "bearerAuth")
public class PatientController {
    private final PatientService patientService;

    @GetMapping("/profile")
    @Operation(summary = "Get own profile", description = "Retrieves the profile information of the currently authenticated user.")
    public ResponseEntity<UserDTO> getMyProfile(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UserDTO profile = patientService.getProfile(userDetails.getUsername());
        return ResponseEntity.ok(profile);
    }

    @PutMapping("/update_profile")
    @Operation(summary = "Update own profile", description = "Updates personal details (like name, height) for the currently authenticated user.")
    public ResponseEntity<UserDTO> updateMyProfile(
            @Valid @RequestBody UserDTO updateRequest,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UserDTO updated = patientService.updateProfile(
                userDetails.getUsername(), updateRequest
        );
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/update_password")
    @Operation(summary = "Change password", description = "Allows the authenticated user to change their account password.")
    public ResponseEntity<Void> changePassword(
            @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        patientService.changePassword(
                userDetails.getUsername(),
                request.getCurrentPassword(),
                request.getNewPassword()
        );
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/specialists")
    @Operation(summary = "Get assigned specialists", description = "Retrieves a list of all medical specialists currently assigned to the authenticated user.")
    public ResponseEntity<List<UserDTO>> getMySpecialists(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        List<UserDTO> specialists = patientService
                .getAssignedSpecialists(userDetails.getUsername());
        return ResponseEntity.ok(specialists);
    }

    @lombok.Data
    public static class ChangePasswordRequest {
        private String currentPassword;
        private String newPassword;
    }
}
