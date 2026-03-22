package com.biomechanics.backend.controller;

import com.biomechanics.backend.model.dto.AnalysisResultDTO;
import com.biomechanics.backend.model.dto.BiomechanicsMetricsDTO;
import com.biomechanics.backend.model.dto.ScanUploadRequestDTO;
import com.biomechanics.backend.service.ScanSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/scans")
@RequiredArgsConstructor
@Tag(name = "Biomechanical Scans", description = "3D scan upload and biomechanics analysis")
public class ScanController {
    private final ScanSessionService scanSessionService;

    /**
     * Upload .ply scan file and process it.
     *
     * POST /api/scans/upload
     *
     * Request:
     *   - file: .ply scan file (multipart)
     *   - userId: User ID
     *   - heightCm: User height in centimeters
     *
     * Response:
     *   - Complete analysis with metrics, risk level, and recommendations
     * @return Complete analysis results
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AnalysisResultDTO> uploadScan(
            @RequestPart("file") MultipartFile file,
            @RequestParam("userId") Long userId,
            @RequestParam("heightCm") Double heightCm,
            @RequestParam(value = "scanType", defaultValue = "LIDAR") String scanType) {

        log.info("Received scan upload request for user {}", userId);

        ScanUploadRequestDTO request = new ScanUploadRequestDTO();
        request.setFile(file);
        request.setUserId(userId);
        request.setHeightCm(heightCm);
        request.setScanType(scanType);

        try {
            AnalysisResultDTO result = scanSessionService.processScanUpload(request);
            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            log.error("Invalid request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();

        } catch (IOException e) {
            log.error("File processing error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();

        } catch (Exception e) {
            log.error("Unexpected error during scan processing: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get analysis results for a specific session.
     *
     * GET /api/scans/{sessionId}
     *
     * @param sessionId Scan session ID
     * @return Analysis results
     */
    @GetMapping("/{sessionId}")
    @Operation(
            summary = "Get analysis results for a session",
            description = "Retrieves complete biomechanics analysis for a specific scan session"
    )
    public ResponseEntity<AnalysisResultDTO> getResults(@Parameter(description = "Scan session ID") @PathVariable Long sessionId){
        log.info("Fetching results for session {}", sessionId);

        try {
            AnalysisResultDTO result = scanSessionService.getAnalysisResults(sessionId);
            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            log.error("Session not found: {}", sessionId);
            return ResponseEntity.notFound().build();

        } catch (Exception e) {
            log.error("Error fetching results: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get scan history for a user.
     *
     * GET /api/scans/user/{userId}/history
     *
     * Returns list of all scans ordered by date (newest first).
     *
     * @param userId User ID
     * @return List of biomechanics metrics for all scans
     */
    @GetMapping("/user/{userId}/history")
    @Operation(
            summary = "Get user's scan history",
            description = "Retrieves all biomechanics scans for a user, ordered by date (newest first)"
    )
    public ResponseEntity<List<BiomechanicsMetricsDTO>> getUserHistory(
            @Parameter(description = "User ID")
            @PathVariable Long userId){
        log.info("Fetching scan history for user {}", userId);

        try {
            List<BiomechanicsMetricsDTO> history = scanSessionService.getUserScanHistory(userId);

            log.info("Retrieved {} scans for user {}", history.size(), userId);
            return ResponseEntity.ok(history);

        } catch (IllegalArgumentException e) {
            log.error("User not found: {}", userId);
            return ResponseEntity.notFound().build();

        } catch (Exception e) {
            log.error("Error fetching history: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Health check endpoint.
     *
     * GET /api/scans/health
     *
     * @return Simple status message
     */
    @GetMapping("/health")
    @Operation(
            summary = "Health check",
            description = "Simple endpoint to verify the scan service is running"
    )
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Biomechanical Scan Service is running");
    }
}
