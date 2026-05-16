package com.biomechanics.backend.exception;

import com.biomechanics.backend.model.dto.ErrorResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalExceptionHandler Tests")
class GlobalExceptionHandlerTest {

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        when(request.getRequestURI()).thenReturn("/api/test");
    }

    @Nested
    @DisplayName("handleBadCredentials()")
    class BadCredentials {

        @Test
        @DisplayName("401 Unauthorized with failed authentication message")
        void shouldReturn401() {
            ResponseEntity<ErrorResponseDTO> response = handler.handleBadCredentials(
                    new BadCredentialsException("Bad credentials"), request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody().getError()).isEqualTo("Authentication Failed");
            assertThat(response.getBody().getMessage())
                    .contains("email or password");
        }

        @Test
        @DisplayName("Request path is included in the response")
        void shouldIncludeRequestPath() {
            ResponseEntity<ErrorResponseDTO> response = handler.handleBadCredentials(
                    new BadCredentialsException("Bad credentials"), request);

            assertThat(response.getBody().getPath()).isEqualTo("/api/test");
        }
    }

    @Nested
    @DisplayName("handleUserNotFound()")
    class UserNotFound {

        @Test
        @DisplayName("404 Not Found with exception message")
        void shouldReturn404() {
            ResponseEntity<ErrorResponseDTO> response = handler.handleUserNotFound(
                    new UsernameNotFoundException("User 'test@t.com' not found"), request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().getError()).isEqualTo("User Not Found");
            assertThat(response.getBody().getMessage()).contains("test@t.com");
        }
    }

    @Nested
    @DisplayName("handleAccessDenied()")
    class AccessDenied {

        @Test
        @DisplayName("403 Forbidden with access denied message")
        void shouldReturn403() {
            ResponseEntity<ErrorResponseDTO> response = handler.handleAccessDenied(
                    new AccessDeniedException("Forbidden"), request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody().getError()).isEqualTo("Access Denied");
            assertThat(response.getBody().getMessage()).contains("permission");
        }
    }

    @Nested
    @DisplayName("handleIllegalArgument()")
    class IllegalArgument {

        @Test
        @DisplayName("400 Bad Request with exception message")
        void shouldReturn400() {
            ResponseEntity<ErrorResponseDTO> response = handler.handleIllegalArgument(
                    new IllegalArgumentException("Invalid input parameter"), request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().getError()).isEqualTo("Invalid Request");
            assertThat(response.getBody().getMessage()).isEqualTo("Invalid input parameter");
        }
    }

    @Nested
    @DisplayName("handleIllegalState()")
    class IllegalState {

        @Test
        @DisplayName("409 Conflict with invalid state message")
        void shouldReturn409() {
            ResponseEntity<ErrorResponseDTO> response = handler.handleIllegalState(
                    new IllegalStateException("Operatiune invalida"), request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().getError()).isEqualTo("Invalid Operation");
            assertThat(response.getBody().getMessage()).isEqualTo("Operatiune invalida");
        }
    }

    @Nested
    @DisplayName("handleResourceNotFound()")
    class ResourceNotFound {

        @Test
        @DisplayName("404 Not Found with missing resource message")
        void shouldReturn404() {
            ResponseEntity<ErrorResponseDTO> response = handler.handleResourceNotFound(
                    new ResourceNotFoundException("ScanSession", 5L), request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().getError()).isEqualTo("Resource Not Found");
            assertThat(response.getBody().getMessage()).contains("5");
        }
    }

    @Nested
    @DisplayName("handleScanProcessing()")
    class ScanProcessing {

        @Test
        @DisplayName("422 Unprocessable Entity with processing message")
        void shouldReturn422() {
            ResponseEntity<ErrorResponseDTO> response = handler.handleScanProcessing(
                    new ScanProcessingException("Procesare esuat", "scan.ply", "PYTHON"), request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(response.getBody().getError()).isEqualTo("Scan Processing Failed");
        }
    }

    @Nested
    @DisplayName("handleRuntimeException()")
    class RuntimeExceptionTest {

        @Test
        @DisplayName("400 Bad Request for generic RuntimeException")
        void shouldReturn400() {
            ResponseEntity<ErrorResponseDTO> response = handler.handleRuntimeException(
                    new RuntimeException("Eroare runtime"), request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().getError()).isEqualTo("Request Failed");
            assertThat(response.getBody().getMessage()).isEqualTo("Eroare runtime");
        }
    }

    @Nested
    @DisplayName("handleGenericException()")
    class GenericException {

        @Test
        @DisplayName("500 Internal Server Error for unexpected exceptions")
        void shouldReturn500() {
            ResponseEntity<ErrorResponseDTO> response = handler.handleGenericException(
                    new Exception("Eroare neasteptata"), request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody().getError()).isEqualTo("Internal Server Error");
        }

        @Test
        @DisplayName("Timestamp is included in the response")
        void shouldIncludeTimestamp() {
            ResponseEntity<ErrorResponseDTO> response = handler.handleGenericException(
                    new Exception("test"), request);

            assertThat(response.getBody().getTimestamp()).isNotNull();
        }
    }

    @Nested
    @DisplayName("handleMaxUploadSize()")
    class MaxUploadSize {

        @Test
        @DisplayName("413 Payload Too Large when file exceeds limit")
        void shouldReturn413() {
            ResponseEntity<ErrorResponseDTO> response = handler.handleMaxUploadSize(
                    new MaxUploadSizeExceededException(10L * 1024 * 1024), request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
            assertThat(response.getBody().getError()).isEqualTo("File Too Large");
            assertThat(response.getBody().getMessage()).contains("200MB");
        }
    }

    @Nested
    @DisplayName("handleWebClientError()")
    class WebClientError {

        @Test
        @DisplayName("Status 400 - invalid format message")
        void shouldReturn503WithBadRequestMessage() {
            WebClientResponseException ex = mock(WebClientResponseException.class);
            when(ex.getStatusCode()).thenReturn(HttpStatus.BAD_REQUEST);
            when(ex.getResponseBodyAsString()).thenReturn("");

            ResponseEntity<ErrorResponseDTO> response = handler.handleWebClientError(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody().getMessage()).contains("Invalid scan file format");
        }

        @Test
        @DisplayName("Status 500 - AI processing failed message")
        void shouldReturn503WithInternalErrorMessage() {
            WebClientResponseException ex = mock(WebClientResponseException.class);
            when(ex.getStatusCode()).thenReturn(HttpStatus.INTERNAL_SERVER_ERROR);
            when(ex.getResponseBodyAsString()).thenReturn("");

            ResponseEntity<ErrorResponseDTO> response = handler.handleWebClientError(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody().getMessage()).contains("AI processing failed");
        }

        @Test
        @DisplayName("Status 503 - service unavailable message")
        void shouldReturn503WithUnavailableMessage() {
            WebClientResponseException ex = mock(WebClientResponseException.class);
            when(ex.getStatusCode()).thenReturn(HttpStatus.SERVICE_UNAVAILABLE);
            when(ex.getResponseBodyAsString()).thenReturn("");

            ResponseEntity<ErrorResponseDTO> response = handler.handleWebClientError(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody().getMessage()).contains("temporarily unavailable");
        }

        @Test
        @DisplayName("Other status (404) - generic communication error message")
        void shouldReturn503WithDefaultMessage() {
            WebClientResponseException ex = mock(WebClientResponseException.class);
            when(ex.getStatusCode()).thenReturn(HttpStatus.NOT_FOUND);
            when(ex.getResponseBodyAsString()).thenReturn("");

            ResponseEntity<ErrorResponseDTO> response = handler.handleWebClientError(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody().getMessage()).contains("Error communicating");
        }
    }
}
