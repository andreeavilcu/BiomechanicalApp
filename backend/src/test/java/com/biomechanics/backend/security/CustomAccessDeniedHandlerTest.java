package com.biomechanics.backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CustomAccessDeniedHandler Tests")
class CustomAccessDeniedHandlerTest {

    private CustomAccessDeniedHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CustomAccessDeniedHandler();
    }

    @Test
    @DisplayName("403 Forbidden with Content-Type application/json")
    void shouldReturn403WithJsonContentType() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("Access denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).contains("application/json");
    }

    @Test
    @DisplayName("JSON response contains error and path fields")
    void shouldWriteJsonBodyWithErrorAndPath() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/assign");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("Forbidden"));

        String body = response.getContentAsString();
        assertThat(body).contains("Access Denied");
        assertThat(body).contains("/api/admin/assign");
        assertThat(body).contains("403");
    }
}
