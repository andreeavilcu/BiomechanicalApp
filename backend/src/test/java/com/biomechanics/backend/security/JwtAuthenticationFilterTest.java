package com.biomechanics.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter Tests")
class JwtAuthenticationFilterTest {

    @Mock private JwtService jwtService;
    @Mock private UserDetailsService userDetailsService;
    @Mock private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("No Authorization header")
    class NoAuthHeader {

        @Test
        @DisplayName("Filter continues the chain without setting authentication")
        void shouldPassThroughWhenNoHeader() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(any(), any());
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("Non-Bearer header is ignored")
        void shouldPassThroughForNonBearerHeader() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
            request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(any(), any());
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }

    @Nested
    @DisplayName("Valid JWT")
    class ValidJwt {

        @Test
        @DisplayName("Authentication is set in SecurityContextHolder")
        void shouldSetAuthenticationForValidToken() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/scan");
            request.addHeader("Authorization", "Bearer valid.jwt.token");
            MockHttpServletResponse response = new MockHttpServletResponse();

            UserDetails userDetails = mock(UserDetails.class);
            when(jwtService.extractUsername("valid.jwt.token")).thenReturn("user@test.com");
            when(userDetailsService.loadUserByUsername("user@test.com")).thenReturn(userDetails);
            when(jwtService.isTokenValid("valid.jwt.token", userDetails)).thenReturn(true);
            when(jwtService.extractRole("valid.jwt.token")).thenReturn("PATIENT");

            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(any(), any());
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        }

        @Test
        @DisplayName("Valid token but invalidated by jwtService - authentication is not set")
        void shouldNotSetAuthWhenTokenNotValid() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/scan");
            request.addHeader("Authorization", "Bearer some.jwt.token");
            MockHttpServletResponse response = new MockHttpServletResponse();

            UserDetails userDetails = mock(UserDetails.class);
            when(jwtService.extractUsername("some.jwt.token")).thenReturn("user@test.com");
            when(userDetailsService.loadUserByUsername("user@test.com")).thenReturn(userDetails);
            when(jwtService.isTokenValid("some.jwt.token", userDetails)).thenReturn(false);

            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(any(), any());
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }

    @Nested
    @DisplayName("Invalid JWT")
    class InvalidJwt {

        @Test
        @DisplayName("Exception during parsing is caught and chain continues")
        void shouldPassThroughWhenJwtParsingFails() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/scan");
            request.addHeader("Authorization", "Bearer invalid.jwt.token");
            MockHttpServletResponse response = new MockHttpServletResponse();

            when(jwtService.extractUsername("invalid.jwt.token"))
                    .thenThrow(new RuntimeException("Malformed JWT"));

            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(any(), any());
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }
}
