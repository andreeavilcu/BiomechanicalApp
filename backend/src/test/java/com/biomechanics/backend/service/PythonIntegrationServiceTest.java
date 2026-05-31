package com.biomechanics.backend.service;

import com.biomechanics.backend.model.dto.PythonResponseDTO;
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
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
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
        @DisplayName("File too large (>500MB) throws IllegalArgumentException")
        void shouldThrowForOversizedFile() {
            MultipartFile mockFile = mock(MultipartFile.class);
            when(mockFile.isEmpty()).thenReturn(false);
            when(mockFile.getOriginalFilename()).thenReturn("scan.ply");
            when(mockFile.getSize()).thenReturn(501L * 1024 * 1024);

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

        @Test
        @DisplayName("Returns true when WebClient responds successfully")
        void shouldReturnTrueWhenServiceReachable() {
            WebClient.RequestHeadersUriSpec<?> uriSpec = mock(WebClient.RequestHeadersUriSpec.class);
            WebClient.RequestHeadersSpec<?> headersSpec = mock(WebClient.RequestHeadersSpec.class);
            WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

            when(pythonWebClient.get()).thenReturn((WebClient.RequestHeadersUriSpec) uriSpec);
            when(uriSpec.uri("/")).thenReturn((WebClient.RequestHeadersSpec) headersSpec);
            when(headersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("OK"));

            boolean result = service.isPythonServiceAvailable();

            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("validatePythonResponse() — invoked via processScanFile()")
    class ValidatePythonResponse {

        @SuppressWarnings("unchecked")
        private void stubWebClientToReturn(PythonResponseDTO dto) {
            WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
            WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
            WebClient.RequestHeadersSpec<?> headersSpec = mock(WebClient.RequestHeadersSpec.class);
            WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

            when(pythonWebClient.post()).thenReturn(uriSpec);
            when(uriSpec.uri(anyString())).thenReturn(bodySpec);
            when(bodySpec.contentType(any())).thenReturn(bodySpec);
            when(bodySpec.body(any())).thenReturn((WebClient.RequestHeadersSpec) headersSpec);
            when(headersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(PythonResponseDTO.class)).thenReturn(Mono.just(dto));
        }

        private MockMultipartFile validPlyFile() {
            return new MockMultipartFile("file", "scan.ply", "application/octet-stream", new byte[]{1, 2, 3});
        }

        @Test
        @DisplayName("Throws RuntimeException when Python response is null")
        void shouldThrowWhenResponseIsNull() {
            WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
            WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
            WebClient.RequestHeadersSpec<?> headersSpec = mock(WebClient.RequestHeadersSpec.class);
            WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

            when(pythonWebClient.post()).thenReturn(uriSpec);
            when(uriSpec.uri(anyString())).thenReturn(bodySpec);
            when(bodySpec.contentType(any())).thenReturn(bodySpec);
            when(bodySpec.body(any())).thenReturn((WebClient.RequestHeadersSpec) headersSpec);
            when(headersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(PythonResponseDTO.class)).thenReturn(Mono.empty());

            assertThatThrownBy(() -> service.processScanFile(validPlyFile(), 175.0))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("Throws RuntimeException when meta field is null")
        void shouldThrowWhenMetaIsNull() throws Exception {
            PythonResponseDTO dto = new PythonResponseDTO();
            // meta is null by default; all keypoint fields also null
            stubWebClientToReturn(dto);

            assertThatThrownBy(() -> service.processScanFile(validPlyFile(), 175.0))
                    .isInstanceOf(RuntimeException.class)
                    .cause()
                    .hasMessageContaining("metadata");
        }

        @Test
        @DisplayName("Throws RuntimeException when lower body keypoints are missing")
        void shouldThrowWhenLowerBodyKeypointsMissing() throws Exception {
            PythonResponseDTO dto = new PythonResponseDTO();
            dto.setMeta(new PythonResponseDTO.MetadataDTO());
            // lHip, rHip, lKnee, rKnee, lAnkle, rAnkle are null

            stubWebClientToReturn(dto);

            assertThatThrownBy(() -> service.processScanFile(validPlyFile(), 175.0))
                    .isInstanceOf(RuntimeException.class)
                    .cause()
                    .hasMessageContaining("lower body");
        }

        @Test
        @DisplayName("Throws RuntimeException when upper body keypoints are missing")
        void shouldThrowWhenUpperBodyKeypointsMissing() throws Exception {
            PythonResponseDTO dto = new PythonResponseDTO();
            dto.setMeta(new PythonResponseDTO.MetadataDTO());

            PythonResponseDTO.KeypointDTO kp = new PythonResponseDTO.KeypointDTO();
            dto.setLHip(kp);
            dto.setRHip(kp);
            dto.setLKnee(kp);
            dto.setRKnee(kp);
            dto.setLAnkle(kp);
            dto.setRAnkle(kp);
            // neck, lShoulder, rShoulder are null

            stubWebClientToReturn(dto);

            assertThatThrownBy(() -> service.processScanFile(validPlyFile(), 175.0))
                    .isInstanceOf(RuntimeException.class)
                    .cause()
                    .hasMessageContaining("upper body");
        }
    }
}
