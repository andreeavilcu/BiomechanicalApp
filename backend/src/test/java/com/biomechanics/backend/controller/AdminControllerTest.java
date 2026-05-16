package com.biomechanics.backend.controller;

import com.biomechanics.backend.model.dto.UserDTO;
import com.biomechanics.backend.model.dto.admin.SystemStatsDTO;
import com.biomechanics.backend.model.enums.UserRole;
import com.biomechanics.backend.service.AdminService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminController Tests")
class AdminControllerTest {

    @Mock
    private AdminService adminService;

    @InjectMocks
    private AdminController adminController;

    private UserDTO buildUserDTO(Long id, String email, UserRole role, boolean active) {
        return UserDTO.builder()
                .id(id)
                .email(email)
                .role(role)
                .isActive(active)
                .build();
    }

    @Nested
    @DisplayName("getAllUsers()")
    class GetAllUsers {

        @Test
        @DisplayName("200 OK with all users when role=null")
        void shouldReturnAllUsersWhenRoleIsNull() {
            List<UserDTO> users = List.of(
                    buildUserDTO(1L, "p@test.com", UserRole.PATIENT,    true),
                    buildUserDTO(2L, "s@test.com", UserRole.SPECIALIST, true)
            );
            when(adminService.getAllUsers()).thenReturn(users);

            ResponseEntity<List<UserDTO>> response = adminController.getAllUsers(null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(2);
            verify(adminService).getAllUsers();
            verify(adminService, never()).getUsersByRole(any());
        }

        @Test
        @DisplayName("200 OK with filtered users when role != null")
        void shouldReturnFilteredUsersWhenRoleProvided() {
            List<UserDTO> specialists = List.of(
                    buildUserDTO(2L, "s@test.com", UserRole.SPECIALIST, true)
            );
            when(adminService.getUsersByRole(UserRole.SPECIALIST)).thenReturn(specialists);

            ResponseEntity<List<UserDTO>> response = adminController.getAllUsers(UserRole.SPECIALIST);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
            assertThat(response.getBody().get(0).getRole()).isEqualTo(UserRole.SPECIALIST);
            verify(adminService).getUsersByRole(UserRole.SPECIALIST);
            verify(adminService, never()).getAllUsers();
        }

        @Test
        @DisplayName("Empty list is returned correctly (200 OK, empty body)")
        void shouldReturnEmptyListWhenNoUsers() {
            when(adminService.getAllUsers()).thenReturn(List.of());

            ResponseEntity<List<UserDTO>> response = adminController.getAllUsers(null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEmpty();
        }
    }

    @Nested
    @DisplayName("updateUserRole()")
    class UpdateUserRole {

        @Test
        @DisplayName("200 OK with updated user")
        void shouldReturnUpdatedUser() {
            UserDTO updated = buildUserDTO(1L, "p@test.com", UserRole.SPECIALIST, true);
            when(adminService.updateUserRole(1L, UserRole.SPECIALIST)).thenReturn(updated);

            ResponseEntity<UserDTO> response = adminController.updateUserRole(1L, UserRole.SPECIALIST);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getRole()).isEqualTo(UserRole.SPECIALIST);
            verify(adminService).updateUserRole(1L, UserRole.SPECIALIST);
        }

        @Test
        @DisplayName("RuntimeException from service is propagated")
        void shouldPropagateExceptionWhenUserNotFound() {
            when(adminService.updateUserRole(999L, UserRole.ADMIN))
                    .thenThrow(new RuntimeException("User not found: 999"));

            assertThatThrownBy(() -> adminController.updateUserRole(999L, UserRole.ADMIN))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("999");
        }

        @Test
        @DisplayName("Service receives exact userId and newRole")
        void shouldPassCorrectParametersToService() {
            UserDTO updated = buildUserDTO(5L, "x@test.com", UserRole.RESEARCHER, true);
            when(adminService.updateUserRole(5L, UserRole.RESEARCHER)).thenReturn(updated);

            adminController.updateUserRole(5L, UserRole.RESEARCHER);

            verify(adminService, times(1)).updateUserRole(5L, UserRole.RESEARCHER);
        }
    }

    @Nested
    @DisplayName("toggleUserStatus()")
    class ToggleUserStatus {

        @Test
        @DisplayName("200 OK on deactivating a user (active=false)")
        void shouldDeactivateUser() {
            UserDTO deactivated = buildUserDTO(1L, "p@test.com", UserRole.PATIENT, false);
            when(adminService.setUserActiveStatus(1L, false)).thenReturn(deactivated);

            ResponseEntity<UserDTO> response = adminController.toggleUserStatus(1L, false);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getIsActive()).isFalse();
            verify(adminService).setUserActiveStatus(1L, false);
        }

        @Test
        @DisplayName("200 OK on activating a user (active=true)")
        void shouldActivateUser() {
            UserDTO activated = buildUserDTO(2L, "inactiv@test.com", UserRole.PATIENT, true);
            when(adminService.setUserActiveStatus(2L, true)).thenReturn(activated);

            ResponseEntity<UserDTO> response = adminController.toggleUserStatus(2L, true);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getIsActive()).isTrue();
        }

        @Test
        @DisplayName("RuntimeException from service is propagated")
        void shouldPropagateException() {
            when(adminService.setUserActiveStatus(404L, true))
                    .thenThrow(new RuntimeException("User not found: 404"));

            assertThatThrownBy(() -> adminController.toggleUserStatus(404L, true))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("getSystemStats()")
    class GetSystemStats {

        @Test
        @DisplayName("200 OK with correct statistics")
        void shouldReturnStats() {
            SystemStatsDTO stats = SystemStatsDTO.builder()
                    .totalUsers(10L)
                    .totalPatients(7L)
                    .totalSpecialists(2L)
                    .totalResearchers(0L)
                    .totalAdmins(1L)
                    .activeUsers(9L)
                    .build();

            when(adminService.getSystemStats()).thenReturn(stats);

            ResponseEntity<SystemStatsDTO> response = adminController.getSystemStats();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getTotalUsers()).isEqualTo(10L);
            assertThat(response.getBody().getTotalPatients()).isEqualTo(7L);
            assertThat(response.getBody().getActiveUsers()).isEqualTo(9L);
            verify(adminService, times(1)).getSystemStats();
        }

        @Test
        @DisplayName("Stats with zero values are returned correctly")
        void shouldReturnZeroStats() {
            SystemStatsDTO emptyStats = SystemStatsDTO.builder()
                    .totalUsers(0L).totalPatients(0L).totalSpecialists(0L)
                    .totalResearchers(0L).totalAdmins(0L).activeUsers(0L)
                    .build();

            when(adminService.getSystemStats()).thenReturn(emptyStats);

            ResponseEntity<SystemStatsDTO> response = adminController.getSystemStats();

            assertThat(response.getBody().getTotalUsers()).isZero();
            assertThat(response.getBody().getActiveUsers()).isZero();
        }
    }
}
