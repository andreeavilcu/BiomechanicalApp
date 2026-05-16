package com.biomechanics.backend.service;

import com.biomechanics.backend.mapper.UserMapper;
import com.biomechanics.backend.model.dto.UserDTO;
import com.biomechanics.backend.model.entity.PatientSpecialistAssignment;
import com.biomechanics.backend.model.entity.User;
import com.biomechanics.backend.model.enums.AssignmentStatus;
import com.biomechanics.backend.model.enums.Gender;
import com.biomechanics.backend.model.enums.UserRole;
import com.biomechanics.backend.repository.PatientSpecialistAssignmentRepository;
import com.biomechanics.backend.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PatientService Tests")
class PatientServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PatientSpecialistAssignmentRepository assignmentRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UserMapper userMapper;

    @InjectMocks
    private PatientService patientService;

    private User buildUser(Long id, String email) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setFirstName("Ion");
        u.setLastName("Popescu");
        u.setDateOfBirth(LocalDate.of(1990, 5, 15));
        u.setGender(Gender.MALE);
        u.setHeightCm(BigDecimal.valueOf(175));
        u.setPasswordHash("$2a$12$hashed");
        u.setRole(UserRole.PATIENT);
        u.setIsActive(true);
        return u;
    }

    private UserDTO buildUserDTO(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .build();
    }

    @Nested
    @DisplayName("getProfile()")
    class GetProfile {

        @Test
        @DisplayName("Returns user profile DTO")
        void shouldReturnUserProfile() {
            User user = buildUser(1L, "pacient@test.com");
            UserDTO dto = buildUserDTO(user);

            when(userMapper.getUserByEmail("pacient@test.com")).thenReturn(user);
            when(userMapper.toDTO(user)).thenReturn(dto);

            UserDTO result = patientService.getProfile("pacient@test.com");

            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo("pacient@test.com");
            verify(userMapper).getUserByEmail("pacient@test.com");
        }

        @Test
        @DisplayName("Non-existent user propagates RuntimeException from mapper")
        void shouldPropagateExceptionForUnknownUser() {
            when(userMapper.getUserByEmail("necunoscut@test.com"))
                    .thenThrow(new RuntimeException("User not found"));

            assertThatThrownBy(() -> patientService.getProfile("necunoscut@test.com"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("not found");
        }
    }

    @Nested
    @DisplayName("updateProfile()")
    class UpdateProfile {

        @Test
        @DisplayName("Updates non-null fields and saves user")
        void shouldUpdateNonNullFields() {
            User user = buildUser(1L, "pacient@test.com");
            UserDTO request = UserDTO.builder()
                    .firstName("Andrei")
                    .lastName("Ionescu")
                    .heightCm(BigDecimal.valueOf(180))
                    .build();
            UserDTO expected = buildUserDTO(user);

            when(userMapper.getUserByEmail("pacient@test.com")).thenReturn(user);
            when(userRepository.save(user)).thenReturn(user);
            when(userMapper.toDTO(user)).thenReturn(expected);

            patientService.updateProfile("pacient@test.com", request);

            assertThat(user.getFirstName()).isEqualTo("Andrei");
            assertThat(user.getLastName()).isEqualTo("Ionescu");
            assertThat(user.getHeightCm()).isEqualTo(BigDecimal.valueOf(180));
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("Null fields in request do not overwrite existing values")
        void shouldIgnoreNullFields() {
            User user = buildUser(1L, "pacient@test.com");
            UserDTO request = UserDTO.builder().firstName("Nou").build();

            when(userMapper.getUserByEmail("pacient@test.com")).thenReturn(user);
            when(userRepository.save(user)).thenReturn(user);
            when(userMapper.toDTO(user)).thenReturn(buildUserDTO(user));

            patientService.updateProfile("pacient@test.com", request);

            assertThat(user.getLastName()).isEqualTo("Popescu");
            assertThat(user.getHeightCm()).isEqualTo(BigDecimal.valueOf(175));
        }

        @Test
        @DisplayName("Returns the DTO saved by the repository")
        void shouldReturnSavedDTO() {
            User user = buildUser(1L, "pacient@test.com");
            UserDTO updatedDTO = buildUserDTO(user);
            updatedDTO.setFirstName("Actualizat");

            when(userMapper.getUserByEmail("pacient@test.com")).thenReturn(user);
            when(userRepository.save(user)).thenReturn(user);
            when(userMapper.toDTO(user)).thenReturn(updatedDTO);

            UserDTO result = patientService.updateProfile("pacient@test.com",
                    UserDTO.builder().firstName("Actualizat").build());

            assertThat(result.getFirstName()).isEqualTo("Actualizat");
        }
    }

    @Nested
    @DisplayName("changePassword()")
    class ChangePassword {

        @Test
        @DisplayName("Correct password - new password is encoded and saved")
        void shouldChangePasswordSuccessfully() {
            User user = buildUser(1L, "pacient@test.com");

            when(userMapper.getUserByEmail("pacient@test.com")).thenReturn(user);
            when(passwordEncoder.matches("curent123", "$2a$12$hashed")).thenReturn(true);
            when(passwordEncoder.encode("noua12345")).thenReturn("$2a$12$newHashed");

            patientService.changePassword("pacient@test.com", "curent123", "noua12345");

            assertThat(user.getPasswordHash()).isEqualTo("$2a$12$newHashed");
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("Incorrect current password - throws BadCredentialsException")
        void shouldThrowOnWrongCurrentPassword() {
            User user = buildUser(1L, "pacient@test.com");

            when(userMapper.getUserByEmail("pacient@test.com")).thenReturn(user);
            when(passwordEncoder.matches(any(), any())).thenReturn(false);

            assertThatThrownBy(() ->
                    patientService.changePassword("pacient@test.com", "gresita", "noua12345"))
                    .isInstanceOf(BadCredentialsException.class);

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("New password too short (under 8 characters) - throws IllegalArgumentException")
        void shouldThrowWhenNewPasswordTooShort() {
            User user = buildUser(1L, "pacient@test.com");

            when(userMapper.getUserByEmail("pacient@test.com")).thenReturn(user);
            when(passwordEncoder.matches(any(), any())).thenReturn(true);

            assertThatThrownBy(() ->
                    patientService.changePassword("pacient@test.com", "curent123", "scurt"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("8 characters");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("New password null - throws IllegalArgumentException")
        void shouldThrowWhenNewPasswordIsNull() {
            User user = buildUser(1L, "pacient@test.com");

            when(userMapper.getUserByEmail("pacient@test.com")).thenReturn(user);
            when(passwordEncoder.matches(any(), any())).thenReturn(true);

            assertThatThrownBy(() ->
                    patientService.changePassword("pacient@test.com", "curent123", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("getAssignedSpecialists()")
    class GetAssignedSpecialists {

        @Test
        @DisplayName("Returns list of active specialists assigned to the patient")
        void shouldReturnAssignedSpecialists() {
            User patient = buildUser(1L, "pacient@test.com");
            User specialist = buildUser(2L, "spec@test.com");
            specialist.setRole(UserRole.SPECIALIST);

            PatientSpecialistAssignment assignment = new PatientSpecialistAssignment();
            assignment.setSpecialist(specialist);
            assignment.setPatient(patient);
            assignment.setStatus(AssignmentStatus.ACTIVE);

            UserDTO specDTO = UserDTO.builder().id(2L).email("spec@test.com").build();

            when(userMapper.getUserByEmail("pacient@test.com")).thenReturn(patient);
            when(assignmentRepository.findByPatientAndStatus(patient, AssignmentStatus.ACTIVE))
                    .thenReturn(List.of(assignment));
            when(userMapper.toDTO(specialist)).thenReturn(specDTO);

            List<UserDTO> result = patientService.getAssignedSpecialists("pacient@test.com");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getEmail()).isEqualTo("spec@test.com");
        }

        @Test
        @DisplayName("Patient with no assigned specialists returns empty list")
        void shouldReturnEmptyListWhenNoSpecialists() {
            User patient = buildUser(1L, "pacient@test.com");

            when(userMapper.getUserByEmail("pacient@test.com")).thenReturn(patient);
            when(assignmentRepository.findByPatientAndStatus(patient, AssignmentStatus.ACTIVE))
                    .thenReturn(List.of());

            assertThat(patientService.getAssignedSpecialists("pacient@test.com")).isEmpty();
        }

        @Test
        @DisplayName("All assigned specialists are returned, not just the first")
        void shouldReturnAllAssignedSpecialists() {
            User patient = buildUser(1L, "pac@test.com");
            User spec1 = buildUser(2L, "spec1@test.com");
            User spec2 = buildUser(3L, "spec2@test.com");

            PatientSpecialistAssignment a1 = new PatientSpecialistAssignment();
            a1.setSpecialist(spec1); a1.setPatient(patient); a1.setStatus(AssignmentStatus.ACTIVE);

            PatientSpecialistAssignment a2 = new PatientSpecialistAssignment();
            a2.setSpecialist(spec2); a2.setPatient(patient); a2.setStatus(AssignmentStatus.ACTIVE);

            when(userMapper.getUserByEmail("pac@test.com")).thenReturn(patient);
            when(assignmentRepository.findByPatientAndStatus(patient, AssignmentStatus.ACTIVE))
                    .thenReturn(List.of(a1, a2));
            when(userMapper.toDTO(spec1)).thenReturn(UserDTO.builder().id(2L).build());
            when(userMapper.toDTO(spec2)).thenReturn(UserDTO.builder().id(3L).build());

            assertThat(patientService.getAssignedSpecialists("pac@test.com")).hasSize(2);
        }
    }
}
