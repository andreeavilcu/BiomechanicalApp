package com.biomechanics.backend.security;

import com.biomechanics.backend.model.enums.UserRole;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("JwtService Tests")
class JwtServiceTest {

    private JwtService jwtService;

    private static final String SECRET =
            "dGVzdHNlY3JldGtleXRlc3RzZWNyZXRrZXl0ZXN0c2VjcmV0a2V5dGVzdHNlY3JldGtleXRlc3Q=";
    private static final long ACCESS_EXPIRATION  = 3_600_000L;
    private static final long REFRESH_EXPIRATION = 86_400_000L;

    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey",        SECRET);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration",    ACCESS_EXPIRATION);
        ReflectionTestUtils.setField(jwtService, "refreshExpiration", REFRESH_EXPIRATION);

        userDetails = User.builder()
                .username("test@biomechanics.com")
                .password("hashed_password")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_PATIENT")))
                .build();
    }

    @Nested
    @DisplayName("generateToken()")
    class GenerateToken {

        @Test
        @DisplayName("Returns a non-null and non-blank token")
        void shouldReturnNonBlankToken() {
            String token = jwtService.generateToken(userDetails, UserRole.PATIENT);
            assertThat(token).isNotBlank();
        }

        @Test
        @DisplayName("Token contains exactly 3 JWT segments (header.payload.signature)")
        void shouldHaveThreeSegments() {
            String token = jwtService.generateToken(userDetails, UserRole.PATIENT);
            assertThat(token.split("\\.")).hasSize(3);
        }

        @Test
        @DisplayName("Username extracted from token matches user's username")
        void shouldExtractCorrectUsername() {
            String token = jwtService.generateToken(userDetails, UserRole.PATIENT);
            assertThat(jwtService.extractUsername(token)).isEqualTo("test@biomechanics.com");
        }

        @Test
        @DisplayName("The 'role' claim is correct for each role")
        void shouldEmbedCorrectRoleClaim() {
            for (UserRole role : UserRole.values()) {
                String token = jwtService.generateToken(userDetails, role);
                assertThat(jwtService.extractRole(token)).isEqualTo(role.name());
            }
        }

        @Test
        @DisplayName("Tokens with different roles are distinct")
        void shouldGenerateDifferentTokensForDifferentRoles() {
            String tokenPatient = jwtService.generateToken(userDetails, UserRole.PATIENT);
            String tokenAdmin   = jwtService.generateToken(userDetails, UserRole.ADMIN);
            assertThat(tokenPatient).isNotEqualTo(tokenAdmin);
        }

        @Test
        @DisplayName("Tokens for different users are distinct")
        void shouldGenerateDifferentTokensForDifferentUsers() {
            UserDetails otherUser = User.builder()
                    .username("alt@biomechanics.com")
                    .password("hashed")
                    .authorities(List.of(new SimpleGrantedAuthority("ROLE_PATIENT")))
                    .build();

            String token1 = jwtService.generateToken(userDetails, UserRole.PATIENT);
            String token2 = jwtService.generateToken(otherUser,   UserRole.PATIENT);

            assertThat(token1).isNotEqualTo(token2);
        }
    }

    @Nested
    @DisplayName("generateRefreshToken()")
    class GenerateRefreshToken {

        @Test
        @DisplayName("Refresh token contains the correct username")
        void shouldContainCorrectUsername() {
            String refreshToken = jwtService.generateRefreshToken(userDetails);
            assertThat(jwtService.extractUsername(refreshToken))
                    .isEqualTo("test@biomechanics.com");
        }

        @Test
        @DisplayName("Refresh token is valid according to isTokenValid()")
        void shouldBeValid() {
            String refreshToken = jwtService.generateRefreshToken(userDetails);
            assertThat(jwtService.isTokenValid(refreshToken, userDetails)).isTrue();
        }

        @Test
        @DisplayName("Refresh token does not contain the 'role' claim")
        void shouldNotContainRoleClaim() {
            String refreshToken = jwtService.generateRefreshToken(userDetails);
            assertThat(jwtService.extractRole(refreshToken)).isNull();
        }
    }

    @Nested
    @DisplayName("isTokenValid()")
    class IsTokenValid {

        @Test
        @DisplayName("Valid token returns true")
        void shouldReturnTrueForValidToken() {
            String token = jwtService.generateToken(userDetails, UserRole.PATIENT);
            assertThat(jwtService.isTokenValid(token, userDetails)).isTrue();
        }

        @Test
        @DisplayName("Token with different username returns false")
        void shouldReturnFalseForDifferentUser() {
            String token = jwtService.generateToken(userDetails, UserRole.PATIENT);

            UserDetails otherUser = User.builder()
                    .username("alt@biomechanics.com")
                    .password("pass")
                    .authorities(List.of(new SimpleGrantedAuthority("ROLE_PATIENT")))
                    .build();

            assertThat(jwtService.isTokenValid(token, otherUser)).isFalse();
        }

        @Test
        @DisplayName("Expired token throws ExpiredJwtException (JJWT behavior)")
        void shouldThrowExpiredJwtExceptionForExpiredToken() {
            ReflectionTestUtils.setField(jwtService, "jwtExpiration", -1000L);
            String expiredToken = jwtService.generateToken(userDetails, UserRole.PATIENT);
            ReflectionTestUtils.setField(jwtService, "jwtExpiration", ACCESS_EXPIRATION);

            assertThatThrownBy(() -> jwtService.isTokenValid(expiredToken, userDetails))
                    .isInstanceOf(ExpiredJwtException.class);
        }

        @Test
        @DisplayName("Tampered token (invalid signature) throws exception")
        void shouldRejectTamperedToken() {
            String token    = jwtService.generateToken(userDetails, UserRole.PATIENT);
            String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "invalidsignature";

            assertThatThrownBy(() -> jwtService.isTokenValid(tampered, userDetails))
                    .isInstanceOf(Exception.class);
        }
    }

    @Nested
    @DisplayName("extractUsername() / extractRole()")
    class ExtractClaims {

        @Test
        @DisplayName("extractUsername throws exception for completely invalid token")
        void shouldThrowForGibberishToken() {
            assertThatThrownBy(() -> jwtService.extractUsername("not.a.token"))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("ADMIN role is correctly encoded and decoded")
        void shouldHandleAdminRole() {
            String token = jwtService.generateToken(userDetails, UserRole.ADMIN);
            assertThat(jwtService.extractRole(token)).isEqualTo("ADMIN");
        }

        @Test
        @DisplayName("SPECIALIST role is correctly encoded and decoded")
        void shouldHandleSpecialistRole() {
            String token = jwtService.generateToken(userDetails, UserRole.SPECIALIST);
            assertThat(jwtService.extractRole(token)).isEqualTo("SPECIALIST");
        }

        @Test
        @DisplayName("RESEARCHER role is correctly encoded and decoded")
        void shouldHandleResearcherRole() {
            String token = jwtService.generateToken(userDetails, UserRole.RESEARCHER);
            assertThat(jwtService.extractRole(token)).isEqualTo("RESEARCHER");
        }
    }
}
