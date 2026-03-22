package com.biomechanics.backend.service;

import com.biomechanics.backend.model.dto.PythonResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class PythonIntegrationService {

    private final WebClient pythonWebClient;

    @Value("${python.service.timeout:180}")
    private int timeoutSeconds;

    /**
     * Sends .ply scan file to Python Flask service for AI processing.
     *
     * @param file .ply scan file
     * @param heightCm User's height in centimeters
     * @return PythonResponseDTO containing 13 keypoints (11 detected + 2 calculated)
     * @throws IOException if file reading fails
     * @throws RuntimeException if Python service fails
     */
    public PythonResponseDTO processScanFile(MultipartFile file, Double heightCm) throws IOException {
        log.info("Sending scan to Python service: filename={}, size={} KB, height={} cm",
                file.getOriginalFilename(), file.getSize() / 1024, heightCm);

        validateScanFile(file);

        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            });
            builder.part("height", heightCm.toString());

            PythonResponseDTO response = pythonWebClient
                    .post()
                    .uri("/process-scan")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(PythonResponseDTO.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .onErrorResume(WebClientResponseException.class, this::handleWebClientError)
                    .block();

            validatePythonResponse(response);

            log.info("Python processing successful. Method: {}, Confidence: {}",
                    response.getMeta().getMethod(),
                    response.getMeta().getBestScore());

            return response;
        } catch (WebClientResponseException e) {
            log.error("Python service HTTP error: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Python service returned error: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error communicating with Python service: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process scan with Python service", e);
        }
    }

    private void validateScanFile(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".ply")) {
            throw new IllegalArgumentException("Only .ply files are supported. Got: " + filename);
        }

        long maxSize = 200 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException(
                    String.format("File too large: %d MB (max: 100 MB)", file.getSize() / 1024 / 1024)
            );
        }

        log.debug("File validation passed: {}", filename);
    }

    private void validatePythonResponse(PythonResponseDTO response) {
        if (response == null) {
            throw new RuntimeException("Python service returned null response");
        }

        if (response.getMeta() == null) {
            log.error("Invalid Python response: missing 'meta' field");
            throw new RuntimeException("Invalid response from Python service: missing metadata");
        }

        if (response.getLHip() == null || response.getRHip() == null ||
                response.getLKnee() == null || response.getRKnee() == null ||
                response.getLAnkle() == null || response.getRAnkle() == null) {
            throw new RuntimeException("Python service failed to detect lower body keypoints");
        }

        if (response.getNeck() == null ||
                response.getLShoulder() == null || response.getRShoulder() == null) {
            throw new RuntimeException("Python service failed to detect upper body keypoints");
        }

        log.debug("Python response validation passed. Detected {} keypoints",
                countDetectedKeypoints(response));
    }

    private int countDetectedKeypoints(PythonResponseDTO response) {
        int count = 0;
        if (response.getNose() != null) count++;
        if (response.getLEar() != null) count++;
        if (response.getREar() != null) count++;
        if (response.getNeck() != null) count++;
        if (response.getLShoulder() != null) count++;
        if (response.getRShoulder() != null) count++;
        if (response.getLHip() != null) count++;
        if (response.getRHip() != null) count++;
        if (response.getPelvis() != null) count++;
        if (response.getLKnee() != null) count++;
        if (response.getRKnee() != null) count++;
        if (response.getLAnkle() != null) count++;
        if (response.getRAnkle() != null) count++;
        return count;
    }

    private Mono<PythonResponseDTO> handleWebClientError(WebClientResponseException ex) {
        String errorBody = ex.getResponseBodyAsString();

        if (ex.getStatusCode() == HttpStatus.BAD_REQUEST) {
            log.error("Python service rejected request (400): {}", errorBody);
            return Mono.error(new RuntimeException("Invalid scan file format or parameters"));
        } else if (ex.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR) {
            log.error("Python service internal error (500): {}", errorBody);
            return Mono.error(new RuntimeException("Python AI model failed to process scan"));
        } else if (ex.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
            log.error("Python service unavailable (503)");
            return Mono.error(new RuntimeException("Python service is currently unavailable"));
        } else {
            log.error("Python service error {}: {}", ex.getStatusCode(), errorBody);
            return Mono.error(new RuntimeException("Python service error: " + ex.getStatusCode()));
        }
    }


    /**
     * Health check for Python service availability.
     *
     * @return true if Python service is reachable
     */
    public boolean isPythonServiceAvailable() {
        try {
            log.debug("Checking Python service health...");

            String response = pythonWebClient
                    .get()
                    .uri("/")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();

            log.info("Python service is available");
            return true;

        } catch (Exception e) {
            log.warn("Python service is NOT available: {}", e.getMessage());
            return false;
        }
    }

}
