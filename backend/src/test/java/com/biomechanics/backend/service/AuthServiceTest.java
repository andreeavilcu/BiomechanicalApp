package com.biomechanics.backend.service;

import com.biomechanics.backend.model.dto.auth.AuthResponseDTO;
import com.biomechanics.backend.model.dto.auth.LoginRequestDTO;
import com.biomechanics.backend.model.dto.auth.RegisterRequestDTO;
import com.biomechanics.backend.model.entity.User;
import com.biomechanics.backend.model.enums.Gender;
import com.biomechanics.backend.model.enums.UserRole;
import com.biomechanics.backend.repository.UserRepository;
import com.biomechanics.backend.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Tests")
class AuthServiceTest {

    @Mock private UserRepository        userRepository;
    @Mock private PasswordEncoder       passwordEncoder;
    @Mock private JwtService            jwtService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private UserDetailsService    userDetailsService;

    @InjectMocks
    private AuthService authService;

    private User buildUser(String email, UserRole role, boolean active) {
        User u = new User();
        u.setId(1L);
        u.setEmail(email);
        u.setPasswordHash("$2a$12$hashedpassword");
        u.setFirstName("Ion");
        u.setLastName("Popescu");
        u.setDateOfBirth(LocalDate.of(1990, 1, 15));
        u.setGender(Gender.MALE);
        u.setHeightCm(BigDecimal.valueOf(175));
        u.setRole(role);
        u.setIsActive(active);
        return u;
    }

    private RegisterRequestDTO buildRegisterRequest(String email) {
        RegisterRequestDTO req = new RegisterRequestDTO();
        req.setEmail(email);
        req.setPassword("Password123!");
        req.setFirstName("Ion");
        req.setLastName("Popescu");
        req.setDateOfBirth(LocalDate.of(1990, 1, 15));
        req.setGender(Gender.MALE);
        req.setHeightCm(BigDecimal.valueOf(175));
        return req;
    }

    private UserDetails mockUserDetails(String email) {
        return org.springframework.security.core.userdetails.User.builder()
                .username(email)
                .password("hashed")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_PATIENT")))
                .build();
    }


    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("Successful registration - returns AuthResponseDTO with correct data")
        void shouldRegisterSuccessfully() {
            RegisterRequestDTO req  = buildRegisterRequest("pacient@test.com");
            User savedUser          = buildUser("pacient@test.com", UserRole.PATIENT, true);
            UserDetails ud          = mockUserDetails("pacient@test.com");

            when(userRepository.existsByEmail("pacient@test.com")).thenReturn(false);
            when(passwordEncoder.encode("Password123!")).thenReturn("$2a$12$hashed");
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            when(userDetailsService.loadUserByUsername("pacient@test.com")).thenReturn(ud);
            when(jwtService.generateToken(ud, UserRole.PATIENT)).thenReturn("access-token");
            when(jwtService.generateRefreshToken(ud)).thenReturn("refresh-token");

            AuthResponseDTO response = authService.register(req);

            assertThat(response.getAccessToken()).isEqualTo("access-token");
            assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
            assertThat(response.getRole()).isEqualTo(UserRole.PATIENT);
            assertThat(response.getEmail()).isEqualTo("pacient@test.com");

            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Email already exists - throws IllegalArgumentException")
        void shouldThrowWhenEmailAlreadyExists() {
            RegisterRequestDTO req = buildRegisterRequest("existent@test.com");
            when(userRepository.existsByEmail("existent@test.com")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("existent@test.com");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Password is encoded before saving")
        void shouldEncodePasswordBeforeSaving() {
            RegisterRequestDTO req  = buildRegisterRequest("nou@test.com");
            User savedUser          = buildUser("nou@test.com", UserRole.PATIENT, true);
            UserDetails ud          = mockUserDetails("nou@test.com");

            when(userRepository.existsByEmail(any())).thenReturn(false);
            when(passwordEncoder.encode("Password123!")).thenReturn("$2a$12$encoded");
            when(userRepository.save(any())).thenReturn(savedUser);
            when(userDetailsService.loadUserByUsername(any())).thenReturn(ud);
            when(jwtService.generateToken(any(), any())).thenReturn("tok");
            when(jwtService.generateRefreshToken(any())).thenReturn("ref");

            authService.register(req);

            verify(passwordEncoder).encode("Password123!");
        }

        @Test
        @DisplayName("Default role for new user is PATIENT")
        void shouldAssignPatientRoleByDefault() {
            RegisterRequestDTO req = buildRegisterRequest("test@test.com");
            UserDetails ud         = mockUserDetails("test@test.com");

            when(userRepository.existsByEmail(any())).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn("hashed");
            when(userDetailsService.loadUserByUsername(any())).thenReturn(ud);
            when(jwtService.generateToken(any(), any())).thenReturn("tok");
            when(jwtService.generateRefreshToken(any())).thenReturn("ref");

            // Capture the saved User object to verify the role
            when(userRepository.save(argThat(u -> u.getRole() == UserRole.PATIENT)))
                    .thenAnswer(inv -> {
                        User u = inv.getArgument(0);
                        u.setId(1L);
                        return u;
                    });

            AuthResponseDTO resp = authService.register(req);
            assertThat(resp.getRole()).isEqualTo(UserRole.PATIENT);
        }
    }

    @Nested
    @DisplayName("login()")
    class Login {

        @Test
        @DisplayName("Successful authentication - returns valid tokens")
        void shouldLoginSuccessfully() {
            LoginRequestDTO req = new LoginRequestDTO();
            req.setEmail("pacient@test.com");
            req.setPassword("Password123!");

            User user      = buildUser("pacient@test.com", UserRole.PATIENT, true);
            UserDetails ud = mockUserDetails("pacient@test.com");

            when(userRepository.findByEmail("pacient@test.com")).thenReturn(Optional.of(user));
            when(userDetailsService.loadUserByUsername("pacient@test.com")).thenReturn(ud);
            when(jwtService.generateToken(ud, UserRole.PATIENT)).thenReturn("access-token");
            when(jwtService.generateRefreshToken(ud)).thenReturn("refresh-token");

            AuthResponseDTO response = authService.login(req);

            assertThat(response.getAccessToken()).isEqualTo("access-token");
            assertThat(response.getEmail()).isEqualTo("pacient@test.com");
        }

        @Test
        @DisplayName("Incorrect credentials - throws BadCredentialsException")
        void shouldThrowOnBadCredentials() {
            LoginRequestDTO req = new LoginRequestDTO();
            req.setEmail("pacient@test.com");
            req.setPassword("wrong");

            doThrow(new BadCredentialsException("Bad credentials"))
                    .when(authenticationManager)
                    .authenticate(any(UsernamePasswordAuthenticationToken.class));

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(BadCredentialsException.class);
        }

        @Test
        @DisplayName("Deactivated account - throws IllegalStateException")
        void shouldThrowWhenAccountIsDeactivated() {
            LoginRequestDTO req = new LoginRequestDTO();
            req.setEmail("inactiv@test.com");
            req.setPassword("Password123!");

            User inactiveUser = buildUser("inactiv@test.com", UserRole.PATIENT, false);
            when(userRepository.findByEmail("inactiv@test.com")).thenReturn(Optional.of(inactiveUser));

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("deactivated");
        }
    }

    @Nested
    @DisplayName("refreshToken()")
    class RefreshToken {

        @Test
        @DisplayName("Valid refresh - returns new access token")
        void shouldReturnNewAccessToken() {
            String refreshTok = "valid-refresh-token";
            User user          = buildUser("pacient@test.com", UserRole.PATIENT, true);
            UserDetails ud     = mockUserDetails("pacient@test.com");

            when(jwtService.extractUsername(refreshTok)).thenReturn("pacient@test.com");
            when(userRepository.findByEmail("pacient@test.com")).thenReturn(Optional.of(user));
            when(userDetailsService.loadUserByUsername("pacient@test.com")).thenReturn(ud);
            when(jwtService.isTokenValid(refreshTok, ud)).thenReturn(true);
            when(jwtService.generateToken(ud, UserRole.PATIENT)).thenReturn("new-access-token");

            AuthResponseDTO response = authService.refreshToken("Bearer " + refreshTok);

            assertThat(response.getAccessToken()).isEqualTo("new-access-token");
            assertThat(response.getRefreshToken()).isEqualTo(refreshTok);
        }

        @Test
        @DisplayName("Missing or invalid header - throws IllegalArgumentException")
        void shouldThrowOnMissingOrInvalidHeader() {
            assertThatThrownBy(() -> authService.refreshToken(null))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> authService.refreshToken("Token fara-prefix"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Expired or invalid refresh token - throws IllegalArgumentException")
        void shouldThrowOnExpiredRefreshToken() {
            String expiredTok = "expired-token";
            User user          = buildUser("pacient@test.com", UserRole.PATIENT, true);
            UserDetails ud     = mockUserDetails("pacient@test.com");

            when(jwtService.extractUsername(expiredTok)).thenReturn("pacient@test.com");
            when(userRepository.findByEmail("pacient@test.com")).thenReturn(Optional.of(user));
            when(userDetailsService.loadUserByUsername("pacient@test.com")).thenReturn(ud);
            when(jwtService.isTokenValid(expiredTok, ud)).thenReturn(false);

            assertThatThrownBy(() -> authService.refreshToken("Bearer " + expiredTok))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("expired");
        }
    }
}