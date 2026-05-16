package com.biomechanics.backend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PythonIntegrationService Tests")
class PythonIntegrationServiceTest {

    @Mock
    private WebClient pythonWebClient;

    @InjectMocks
    private PythonIntegrationService service;

    @Nested
    @DisplayName("validateScanFile()")
    class ValidateScanFile {

        @Test
        @DisplayName("Empty file throws IllegalArgumentException")
        void shouldThrowForEmptyFile() {
            MockMultipartFile emptyFile = new MockMultipartFile(
                    "file", "scan.ply", null, new byte[0]);

            assertThatThrownBy(() -> service.processScanFile(emptyFile, 175.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("empty");
        }

        @Test
        @DisplayName("File with invalid extension (non-.ply) throws IllegalArgumentException")
        void shouldThrowForNonPlyExtension() {
            MockMultipartFile txtFile = new MockMultipartFile(
                    "file", "scan.txt", null, new byte[]{1, 2, 3});

            assertThatThrownBy(() -> service.processScanFile(txtFile, 175.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(".ply");
        }

        @Test
        @DisplayName("File with null filename throws IllegalArgumentException")
        void shouldThrowForNullFilename() {
            MockMultipartFile nullNameFile = new MockMultipartFile(
                    "file", null, null, new byte[]{1, 2, 3});

            assertThatThrownBy(() -> service.processScanFile(nullNameFile, 175.0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("File too large (>200MB) throws IllegalArgumentException")
        void shouldThrowForOversizedFile() {
            MultipartFile mockFile = mock(MultipartFile.class);
            when(mockFile.isEmpty()).thenReturn(false);
            when(mockFile.getOriginalFilename()).thenReturn("scan.ply");
            when(mockFile.getSize()).thenReturn(201L * 1024 * 1024);

            assertThatThrownBy(() -> service.processScanFile(mockFile, 175.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("File too large");
        }
    }

    @Nested
    @DisplayName("isPythonServiceAvailable()")
    class IsPythonServiceAvailable {

        @Test
        @DisplayName("Returns false when WebClient throws exception")
        void shouldReturnFalseWhenWebClientThrows() {
            WebClient.RequestHeadersUriSpec<?> uriSpec = mock(WebClient.RequestHeadersUriSpec.class);
            when(pythonWebClient.get()).thenReturn((WebClient.RequestHeadersUriSpec) uriSpec);
            when(uriSpec.uri("/")).thenThrow(new RuntimeException("Connection refused"));

            boolean result = service.isPythonServiceAvailable();

            assertThat(result).isFalse();
        }
    }
}
