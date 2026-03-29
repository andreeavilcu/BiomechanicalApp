package com.biomechanics.backend.exception;

import com.biomechanics.backend.model.dto.ErrorResponseDTO;
import com.biomechanics.backend.model.dto.ErrorResponseDTO.FieldErrorDTO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponseDTO> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        log.warn("Authentication failed for request {}: {}", request.getRequestURI(), ex.getMessage());

        return buildErrorResponse(
                HttpStatus.UNAUTHORIZED,
                "Authentication Failed",
                "Incorrect email or password.",
                request.getRequestURI()
        );
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleUserNotFound(UsernameNotFoundException ex, HttpServletRequest request) {
        log.warn("User not found for request {}: {}", request.getRequestURI(), ex.getMessage());

        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                "User Not Found",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDTO> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied for request {}: {}", request.getRequestURI(), ex.getMessage());

        return buildErrorResponse(
                HttpStatus.FORBIDDEN,
                "Access Denied",
                "You do not have permission to access this resource.",
                request.getRequestURI()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidationErrors(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldErrorDTO> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::mapFieldError)
                .collect(Collectors.toList());

        log.warn("Validation failed for request {}: {} errors",
                request.getRequestURI(), fieldErrors.size());

        ErrorResponseDTO response = ErrorResponseDTO.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message("One or more fields have invalid values.")
                .path(request.getRequestURI())
                .fieldErrors(fieldErrors)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDTO> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Invalid argument for request {}: {}", request.getRequestURI(), ex.getMessage());

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Invalid Request",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponseDTO> handleIllegalState(IllegalStateException ex, HttpServletRequest request) {
        log.warn("Invalid state for request {}: {}", request.getRequestURI(), ex.getMessage());

        return buildErrorResponse(
                HttpStatus.CONFLICT,
                "Invalid Operation",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponseDTO> handleMaxUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        log.warn("File too large for request {}: {}", request.getRequestURI(), ex.getMessage());

        return buildErrorResponse(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "File Too Large",
                "The uploaded file exceeds the maximum allowed size (200MB).",
                request.getRequestURI()
        );
    }

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ErrorResponseDTO> handleWebClientError(WebClientResponseException ex, HttpServletRequest request) {
        log.error("Python service error for request {}: status={}, body={}",
                request.getRequestURI(), ex.getStatusCode(), ex.getResponseBodyAsString());

        String message = switch (ex.getStatusCode().value()) {
            case 400 -> "Invalid scan file format or parameters.";
            case 500 -> "AI processing failed. Please try with a cleaner scan.";
            case 503 -> "AI service is temporarily unavailable. Please try again later.";
            default -> "Error communicating with AI service.";
        };

        return buildErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AI Service Error",
                message,
                request.getRequestURI()
        );
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found for request {}: {}", request.getRequestURI(), ex.getMessage());

        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                "Resource Not Found",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler(ScanProcessingException.class)
    public ResponseEntity<ErrorResponseDTO> handleScanProcessing(ScanProcessingException ex, HttpServletRequest request) {
        log.error("Scan processing failed for request {}: file={}, stage={}, error={}",
                request.getRequestURI(),
                ex.getScanFileName(),
                ex.getProcessingStage(),
                ex.getMessage());

        return buildErrorResponse(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Scan Processing Failed",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponseDTO> handleRuntimeException(RuntimeException ex, HttpServletRequest request) {
        log.error("Runtime error for request {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Request Failed",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error for request {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected error occurred. Please try again later.",
                request.getRequestURI()
        );
    }


    private ResponseEntity<ErrorResponseDTO> buildErrorResponse(HttpStatus status, String error, String message, String path) {
        ErrorResponseDTO response = ErrorResponseDTO.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(error)
                .message(message)
                .path(path)
                .build();

        return ResponseEntity.status(status).body(response);
    }

    private FieldErrorDTO mapFieldError(FieldError fieldError) {
        return FieldErrorDTO.builder()
                .field(fieldError.getField())
                .message(fieldError.getDefaultMessage())
                .rejectedValue(fieldError.getRejectedValue())
                .build();
    }
}
