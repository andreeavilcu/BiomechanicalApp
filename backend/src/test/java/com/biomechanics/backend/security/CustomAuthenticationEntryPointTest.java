package com.biomechanics.backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CustomAuthenticationEntryPoint Tests")
class CustomAuthenticationEntryPointTest {

    private CustomAuthenticationEntryPoint entryPoint;

    @BeforeEach
    void setUp() {
        entryPoint = new CustomAuthenticationEntryPoint();
    }


    @Nested
    @DisplayName("Without Authorization header")
    class WithoutBearerToken {

        @Test
        @DisplayName("401 with authentication required message")
        void shouldReturn401WithAuthRequired() throws IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/scan/1");
            MockHttpServletResponse response = new MockHttpServletResponse();

            entryPoint.commence(request, response,
                    new InsufficientAuthenticationException("Authentication required"));

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentAsString()).contains("Authentication required");
        }

        @Test
        @DisplayName("Content-Type is application/json")
        void shouldSetJsonContentType() throws IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/scan/1");
            MockHttpServletResponse response = new MockHttpServletResponse();

            entryPoint.commence(request, response,
                    new InsufficientAuthenticationException("No auth"));

            assertThat(response.getContentType()).contains("application/json");
        }
    }

    @Nested
    @DisplayName("With Authorization Bearer header")
    class WithBearerToken {

        @Test
        @DisplayName("401 with invalid or expired token message")
        void shouldReturn401WithExpiredTokenMessage() throws IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/scan/1");
            request.addHeader("Authorization", "Bearer expired.token.here");
            MockHttpServletResponse response = new MockHttpServletResponse();

            entryPoint.commence(request, response,
                    new InsufficientAuthenticationException("JWT expired"));

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentAsString()).contains("Invalid or expired");
        }
    }
}
