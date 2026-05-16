package com.biomechanics.backend.controller;

import com.biomechanics.backend.model.dto.UserDTO;
import com.biomechanics.backend.model.enums.Gender;
import com.biomechanics.backend.model.enums.UserRole;
import com.biomechanics.backend.service.PatientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PatientController Tests")
class PatientControllerTest {

    @Mock private PatientService patientService;
    @Mock private UserDetails    userDetails;

    @InjectMocks
    private PatientController patientController;

    private UserDTO baseProfile;

    @BeforeEach
    void setUp() {
        when(userDetails.getUsername()).thenReturn("pac@test.com");

        baseProfile = UserDTO.builder()
                .id(1L)
                .email("pac@test.com")
                .firstName("Ion")
                .lastName("Popescu")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .gender(Gender.MALE)
                .heightCm(BigDecimal.valueOf(175))
                .role(UserRole.PATIENT)
                .isActive(true)
                .build();
    }

    @Nested
    @DisplayName("getMyProfile()")
    class GetMyProfile {

        @Test
        @DisplayName("200 OK and correct DTO when profile found")
        void shouldReturn200WithProfile() {
            when(patientService.getProfile("pac@test.com")).thenReturn(baseProfile);

            ResponseEntity<UserDTO> response = patientController.getMyProfile(userDetails);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getEmail()).isEqualTo("pac@test.com");
        }

        @Test
        @DisplayName("Service is called with username from UserDetails")
        void shouldDelegateToServiceWithUsername() {
            when(patientService.getProfile("pac@test.com")).thenReturn(baseProfile);

            patientController.getMyProfile(userDetails);

            verify(patientService).getProfile("pac@test.com");
        }

        @Test
        @DisplayName("Exception from service is propagated")
        void shouldPropagateException() {
            when(patientService.getProfile(any()))
                    .thenThrow(new RuntimeException("User not found"));

            assertThatThrownBy(() -> patientController.getMyProfile(userDetails))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("updateMyProfile()")
    class UpdateMyProfile {

        @Test
        @DisplayName("200 OK and updated DTO on successful update")
        void shouldReturn200WithUpdatedProfile() {
            UserDTO updateRequest = UserDTO.builder().firstName("Andrei").build();
            UserDTO updated = UserDTO.builder().id(1L).email("pac@test.com")
                    .firstName("Andrei").build();

            when(patientService.updateProfile("pac@test.com", updateRequest)).thenReturn(updated);

            ResponseEntity<UserDTO> response = patientController
                    .updateMyProfile(updateRequest, userDetails);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getFirstName()).isEqualTo("Andrei");
        }

        @Test
        @DisplayName("Service receives correct email and request")
        void shouldPassEmailAndRequestToService() {
            UserDTO req = UserDTO.builder().firstName("Test").build();
            when(patientService.updateProfile(eq("pac@test.com"), eq(req))).thenReturn(baseProfile);

            patientController.updateMyProfile(req, userDetails);

            verify(patientService).updateProfile("pac@test.com", req);
        }
    }

    @Nested
    @DisplayName("changePassword()")
    class ChangePassword {

        private PatientController.ChangePasswordRequest buildReq(String current, String newPwd) {
            PatientController.ChangePasswordRequest req = new PatientController.ChangePasswordRequest();
            req.setCurrentPassword(current);
            req.setNewPassword(newPwd);
            return req;
        }

        @Test
        @DisplayName("204 No Content on successful password change")
        void shouldReturn204OnSuccess() {
            doNothing().when(patientService).changePassword(any(), any(), any());

            ResponseEntity<Void> response = patientController
                    .changePassword(buildReq("veche123", "noua12345"), userDetails);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }

        @Test
        @DisplayName("Service receives email, current password, and new password")
        void shouldDelegateToService() {
            doNothing().when(patientService).changePassword(any(), any(), any());

            patientController.changePassword(buildReq("veche123", "noua12345"), userDetails);

            verify(patientService).changePassword("pac@test.com", "veche123", "noua12345");
        }

        @Test
        @DisplayName("BadCredentialsException from service is propagated")
        void shouldPropagateBadCredentials() {
            doThrow(new BadCredentialsException("Incorrect password"))
                    .when(patientService).changePassword(any(), any(), any());

            assertThatThrownBy(() ->
                    patientController.changePassword(buildReq("gresit", "noua12345"), userDetails))
                    .isInstanceOf(BadCredentialsException.class);
        }
    }

    @Nested
    @DisplayName("getMySpecialists()")
    class GetMySpecialists {

        @Test
        @DisplayName("200 OK and list of specialists")
        void shouldReturn200WithSpecialists() {
            List<UserDTO> specialists = List.of(
                    UserDTO.builder().id(2L).email("spec@test.com").build()
            );
            when(patientService.getAssignedSpecialists("pac@test.com")).thenReturn(specialists);

            ResponseEntity<List<UserDTO>> response = patientController.getMySpecialists(userDetails);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
        }

        @Test
        @DisplayName("Empty list when no specialists assigned")
        void shouldReturn200WithEmptyList() {
            when(patientService.getAssignedSpecialists("pac@test.com")).thenReturn(List.of());

            ResponseEntity<List<UserDTO>> response = patientController.getMySpecialists(userDetails);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEmpty();
        }
    }
}
