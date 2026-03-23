package com.biomechanics.backend.controller;

import com.biomechanics.backend.model.dto.admin.SystemStatsDTO;
import com.biomechanics.backend.model.dto.UserDTO;
import com.biomechanics.backend.model.enums.UserRole;
import com.biomechanics.backend.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "System administration endpoints - ADMIN only")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {
    private final AdminService adminService;

    @GetMapping("/users")
    @Operation(summary = "Get all users", description = "Retrieves a list of all users in the system. Optionally, filter by role.")
    public ResponseEntity<List<UserDTO>> getAllUsers(
            @RequestParam(required = false) UserRole role
    ) {
        List<UserDTO> users = role != null
                ? adminService.getUsersByRole(role)
                : adminService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    @PutMapping("/users/{userId}/role")
    @Operation(summary = "Update user role", description = "Changes the role (e.g., PATIENT, SPECIALIST) of a specific user.")
    public ResponseEntity<UserDTO> updateUserRole(
            @PathVariable Long userId,
            @RequestParam UserRole newRole
    ) {
        UserDTO updatedUser = adminService.updateUserRole(userId, newRole);
        return ResponseEntity.ok(updatedUser);
    }

    @PutMapping("/users/{userId}/status")
    @Operation(summary = "Toggle user active status", description = "Activates or deactivates a user's account.")
    public ResponseEntity<UserDTO> toggleUserStatus(
            @PathVariable Long userId,
            @RequestParam boolean active
    ) {
        UserDTO updatedUser = adminService.setUserActiveStatus(userId, active);
        return ResponseEntity.ok(updatedUser);
    }

    @GetMapping("/stats")
    @Operation(summary = "Get system statistics", description = "Retrieves overall system statistics, such as total users and role distribution.")
    public ResponseEntity<SystemStatsDTO> getSystemStats() {
        return ResponseEntity.ok(adminService.getSystemStats());
    }
}
