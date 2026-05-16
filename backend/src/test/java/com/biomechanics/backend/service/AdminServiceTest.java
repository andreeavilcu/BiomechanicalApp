package com.biomechanics.backend.service;

import com.biomechanics.backend.mapper.UserMapper;
import com.biomechanics.backend.model.dto.UserDTO;
import com.biomechanics.backend.model.dto.admin.SystemStatsDTO;
import com.biomechanics.backend.model.entity.User;
import com.biomechanics.backend.model.enums.Gender;
import com.biomechanics.backend.model.enums.UserRole;
import com.biomechanics.backend.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminService Tests")
class AdminServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserMapper     userMapper;

    @InjectMocks
    private AdminService adminService;

    private User buildUser(Long id, String email, UserRole role, boolean active) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setPasswordHash("hashed");
        u.setFirstName("Test");
        u.setLastName("User");
        u.setDateOfBirth(LocalDate.of(1990, 5, 20));
        u.setGender(Gender.FEMALE);
        u.setHeightCm(BigDecimal.valueOf(165));
        u.setRole(role);
        u.setIsActive(active);
        return u;
    }

    private UserDTO toDTO(User u) {
        return UserDTO.builder()
                .id(u.getId())
                .email(u.getEmail())
                .role(u.getRole())
                .isActive(u.getIsActive())
                .build();
    }

    @Nested
    @DisplayName("getAllUsers()")
    class GetAllUsers {

        @Test
        @DisplayName("Complete list of users is returned correctly")
        void shouldReturnAllUsers() {
            User patient    = buildUser(1L, "p@test.com",  UserRole.PATIENT,    true);
            User specialist = buildUser(2L, "s@test.com",  UserRole.SPECIALIST, true);

            when(userRepository.findAll()).thenReturn(List.of(patient, specialist));
            when(userMapper.toDTO(patient)).thenReturn(toDTO(patient));
            when(userMapper.toDTO(specialist)).thenReturn(toDTO(specialist));

            List<UserDTO> result = adminService.getAllUsers();

            assertThat(result).hasSize(2);
            assertThat(result).extracting(UserDTO::getEmail)
                    .containsExactlyInAnyOrder("p@test.com", "s@test.com");
        }

        @Test
        @DisplayName("Empty list is returned when no users exist")
        void shouldReturnEmptyListWhenNoUsers() {
            when(userRepository.findAll()).thenReturn(List.of());
            assertThat(adminService.getAllUsers()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getUsersByRole()")
    class GetUsersByRole {

        @Test
        @DisplayName("Filtering by role returns only users with that role")
        void shouldReturnUsersWithSpecifiedRole() {
            User researcher = buildUser(3L, "r@test.com", UserRole.RESEARCHER, true);

            when(userRepository.findByRole(UserRole.RESEARCHER)).thenReturn(List.of(researcher));
            when(userMapper.toDTO(researcher)).thenReturn(toDTO(researcher));

            List<UserDTO> result = adminService.getUsersByRole(UserRole.RESEARCHER);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getRole()).isEqualTo(UserRole.RESEARCHER);
        }

        @Test
        @DisplayName("Role with no users returns empty list")
        void shouldReturnEmptyListForRoleWithNoUsers() {
            when(userRepository.findByRole(UserRole.ADMIN)).thenReturn(List.of());
            assertThat(adminService.getUsersByRole(UserRole.ADMIN)).isEmpty();
        }
    }

    @Nested
    @DisplayName("updateUserRole()")
    class UpdateUserRole {

        @Test
        @DisplayName("Role is updated and user saved")
        void shouldUpdateRoleSuccessfully() {
            User patient = buildUser(1L, "p@test.com", UserRole.PATIENT, true);

            when(userRepository.findById(1L)).thenReturn(Optional.of(patient));
            when(userRepository.save(patient)).thenReturn(patient);
            when(userMapper.toDTO(patient)).thenReturn(toDTO(patient));

            adminService.updateUserRole(1L, UserRole.SPECIALIST);

            assertThat(patient.getRole()).isEqualTo(UserRole.SPECIALIST);
            verify(userRepository).save(patient);
        }

        @Test
        @DisplayName("Non-existent user throws RuntimeException")
        void shouldThrowWhenUserNotFound() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminService.updateUserRole(999L, UserRole.ADMIN))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("999");
        }
    }

    @Nested
    @DisplayName("setUserActiveStatus()")
    class SetUserActiveStatus {

        @Test
        @DisplayName("Account is deactivated correctly (active = false)")
        void shouldDeactivateUser() {
            User user = buildUser(1L, "p@test.com", UserRole.PATIENT, true);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userRepository.save(user)).thenReturn(user);
            when(userMapper.toDTO(user)).thenReturn(toDTO(user));

            adminService.setUserActiveStatus(1L, false);

            assertThat(user.getIsActive()).isFalse();
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("Account is activated correctly (active = true)")
        void shouldActivateUser() {
            User user = buildUser(2L, "inactiv@test.com", UserRole.PATIENT, false);

            when(userRepository.findById(2L)).thenReturn(Optional.of(user));
            when(userRepository.save(user)).thenReturn(user);
            when(userMapper.toDTO(user)).thenReturn(toDTO(user));

            adminService.setUserActiveStatus(2L, true);

            assertThat(user.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("Non-existent user throws RuntimeException")
        void shouldThrowWhenUserNotFound() {
            when(userRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminService.setUserActiveStatus(404L, false))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("getSystemStats()")
    class GetSystemStats {

        @Test
        @DisplayName("System statistics are calculated correctly")
        void shouldCalculateStatsCorrectly() {
            List<User> users = List.of(
                    buildUser(1L, "p1@test.com", UserRole.PATIENT,    true),
                    buildUser(2L, "p2@test.com", UserRole.PATIENT,    false),
                    buildUser(3L, "s1@test.com", UserRole.SPECIALIST, true),
                    buildUser(4L, "r1@test.com", UserRole.RESEARCHER, true),
                    buildUser(5L, "a1@test.com", UserRole.ADMIN,      true)
            );

            when(userRepository.findAll()).thenReturn(users);

            SystemStatsDTO stats = adminService.getSystemStats();

            assertThat(stats.getTotalUsers()).isEqualTo(5L);
            assertThat(stats.getTotalPatients()).isEqualTo(2L);
            assertThat(stats.getTotalSpecialists()).isEqualTo(1L);
            assertThat(stats.getTotalResearchers()).isEqualTo(1L);
            assertThat(stats.getTotalAdmins()).isEqualTo(1L);
            assertThat(stats.getActiveUsers()).isEqualTo(4L);
        }

        @Test
        @DisplayName("Statistics with 0 users return all fields as 0")
        void shouldReturnZeroStatsWhenNoUsers() {
            when(userRepository.findAll()).thenReturn(List.of());

            SystemStatsDTO stats = adminService.getSystemStats();

            assertThat(stats.getTotalUsers()).isZero();
            assertThat(stats.getTotalPatients()).isZero();
            assertThat(stats.getActiveUsers()).isZero();
        }
    }
}
