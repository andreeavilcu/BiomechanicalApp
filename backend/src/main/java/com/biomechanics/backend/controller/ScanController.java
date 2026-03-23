package com.biomechanics.backend.controller;

import com.biomechanics.backend.model.dto.AnalysisResultDTO;
import com.biomechanics.backend.model.dto.ScanUploadRequestDTO;
import com.biomechanics.backend.service.ScanSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;


@RestController
@RequestMapping("/api/scans")
@RequiredArgsConstructor
@Tag(name = "Scans", description = "Upload and retrieve biomechanical scans and analyses")
@SecurityRequirement(name = "bearerAuth")
public class ScanController {

    private final ScanSessionService scanSessionService;

    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('PATIENT', 'SPECIALIST', 'ADMIN')")
    @Operation(summary = "Upload a .ply or .pcd file and run AI processing", description = "Uploads a 3D point cloud file and runs the AI pipeline to analyze posture and biomechanics.")
    public ResponseEntity<AnalysisResultDTO> uploadScan(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") Long userId,
            @RequestParam("heightCm") Double heightCm,
            @RequestParam(value = "scanType", defaultValue = "LIDAR") String scanType,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        validateScanAccess(userDetails, userId);

        AnalysisResultDTO result = scanSessionService.processScan(
                file, userId, heightCm, scanType
        );
        return ResponseEntity.ok(result);
    }

    @GetMapping("/my-history")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get current user's scan history", description = "Retrieves all past scan analyses for the authenticated user.")
    public ResponseEntity<List<AnalysisResultDTO>> getMyHistory(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        List<AnalysisResultDTO> history = scanSessionService
                .getHistoryByEmail(userDetails.getUsername());
        return ResponseEntity.ok(history);
    }

    @GetMapping("/{sessionId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get specific scan session details", description = "Retrieves the biomechanical analysis results for a specific scan session.")
    public ResponseEntity<AnalysisResultDTO> getSession(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        AnalysisResultDTO result = scanSessionService
                .getSessionForUser(sessionId, userDetails.getUsername());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/user/{userId}/history")
    @PreAuthorize("hasAnyRole('SPECIALIST', 'ADMIN')")
    @Operation(summary = "Get specific user's scan history", description = "Allows specialists and admins to retrieve the scan history of a specific user.")
    public ResponseEntity<List<AnalysisResultDTO>> getUserHistory(
            @PathVariable Long userId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        List<AnalysisResultDTO> history = scanSessionService
                .getHistoryByUserId(userId, userDetails.getUsername());
        return ResponseEntity.ok(history);
    }

    @DeleteMapping("/{sessionId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete scan session", description = "Deletes a specific scan session. Users can only delete their own scans; admins can delete any scan.")
    public ResponseEntity<Void> deleteSession(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        scanSessionService.deleteSession(sessionId, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }


    private void validateScanAccess(UserDetails userDetails, Long targetUserId) {
        boolean isPatient = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_PATIENT"));

        if (isPatient) {
            boolean ownsResource = scanSessionService
                    .isOwner(userDetails.getUsername(), targetUserId);

            if (!ownsResource) {
                throw new AccessDeniedException(
                        "A patient can only upload scans for their own account."
                );
            }
        }
       
    }
}
