package com.biomechanics.backend.controller;

import com.biomechanics.backend.model.dto.auth.AuthResponseDTO;
import com.biomechanics.backend.model.dto.auth.LoginRequestDTO;
import com.biomechanics.backend.model.dto.auth.RegisterRequestDTO;
import com.biomechanics.backend.model.enums.Gender;
import com.biomechanics.backend.model.enums.UserRole;
import com.biomechanics.backend.service.AuthService;
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

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController Tests")
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    private AuthResponseDTO authResponse;

    @BeforeEach
    void setUp() {
        authResponse = AuthResponseDTO.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .role(UserRole.PATIENT)
                .userId(1L)
                .email("pacient@test.com")
                .firstName("Ion")
                .lastName("Popescu")
                .build();
    }

    @Nested
    @DisplayName("register()")
    class Register {

        private RegisterRequestDTO buildRequest() {
            RegisterRequestDTO req = new RegisterRequestDTO();
            req.setEmail("nou@test.com");
            req.setPassword("Password123!");
            req.setFirstName("Ion");
            req.setLastName("Popescu");
            req.setDateOfBirth(LocalDate.of(1990, 1, 1));
            req.setGender(Gender.MALE);
            req.setHeightCm(BigDecimal.valueOf(175));
            return req;
        }

        @Test
        @DisplayName("201 Created and correct body on successful registration")
        void shouldReturn201OnSuccess() {
            when(authService.register(any(RegisterRequestDTO.class))).thenReturn(authResponse);

            ResponseEntity<AuthResponseDTO> response = authController.register(buildRequest());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getAccessToken()).isEqualTo("access-token");
            assertThat(response.getBody().getRole()).isEqualTo(UserRole.PATIENT);
        }

        @Test
        @DisplayName("register() service is called with the received request")
        void shouldDelegateToService() {
            RegisterRequestDTO req = buildRequest();
            when(authService.register(req)).thenReturn(authResponse);

            authController.register(req);

            verify(authService, times(1)).register(req);
        }

        @Test
        @DisplayName("Exception from service is propagated to caller")
        void shouldPropagateException() {
            when(authService.register(any()))
                    .thenThrow(new IllegalArgumentException("Email already exists."));

            assertThatThrownBy(() -> authController.register(buildRequest()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Email already exists.");
        }
    }

    @Nested
    @DisplayName("login()")
    class Login {

        private LoginRequestDTO buildRequest() {
            LoginRequestDTO req = new LoginRequestDTO();
            req.setEmail("pacient@test.com");
            req.setPassword("Password123!");
            return req;
        }

        @Test
        @DisplayName("200 OK and correct tokens on successful authentication")
        void shouldReturn200OnSuccess() {
            when(authService.login(any(LoginRequestDTO.class))).thenReturn(authResponse);

            ResponseEntity<AuthResponseDTO> response = authController.login(buildRequest());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getAccessToken()).isEqualTo("access-token");
            assertThat(response.getBody().getEmail()).isEqualTo("pacient@test.com");
        }

        @Test
        @DisplayName("login() service is called with the received request")
        void shouldDelegateToService() {
            LoginRequestDTO req = buildRequest();
            when(authService.login(req)).thenReturn(authResponse);

            authController.login(req);

            verify(authService, times(1)).login(req);
        }

        @Test
        @DisplayName("BadCredentialsException from service is propagated")
        void shouldPropagateBadCredentials() {
            when(authService.login(any()))
                    .thenThrow(new BadCredentialsException("Incorrect password."));

            assertThatThrownBy(() -> authController.login(buildRequest()))
                    .isInstanceOf(BadCredentialsException.class);
        }

        @Test
        @DisplayName("IllegalStateException (deactivated account) is propagated")
        void shouldPropagateDeactivatedAccount() {
            when(authService.login(any()))
                    .thenThrow(new IllegalStateException("Account has been deactivated."));

            assertThatThrownBy(() -> authController.login(buildRequest()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("deactivated");
        }
    }

    @Nested
    @DisplayName("refreshToken()")
    class RefreshToken {

        @Test
        @DisplayName("200 OK and new access token on valid refresh")
        void shouldReturn200OnValidRefresh() {
            AuthResponseDTO refreshed = AuthResponseDTO.builder()
                    .accessToken("new-access-token")
                    .refreshToken("same-refresh-token")
                    .role(UserRole.PATIENT)
                    .email("pacient@test.com")
                    .build();

            when(authService.refreshToken("Bearer valid-refresh-token")).thenReturn(refreshed);

            ResponseEntity<AuthResponseDTO> response =
                    authController.refreshToken("Bearer valid-refresh-token");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getAccessToken()).isEqualTo("new-access-token");
        }

        @Test
        @DisplayName("refreshToken() service receives the exact Authorization header")
        void shouldPassAuthHeaderToService() {
            when(authService.refreshToken(any())).thenReturn(authResponse);

            authController.refreshToken("Bearer my-token");

            verify(authService).refreshToken("Bearer my-token");
        }

        @Test
        @DisplayName("IllegalArgumentException (invalid token) is propagated")
        void shouldPropagateInvalidToken() {
            when(authService.refreshToken(any()))
                    .thenThrow(new IllegalArgumentException("Expired token."));

            assertThatThrownBy(() -> authController.refreshToken("Bearer bad-token"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
