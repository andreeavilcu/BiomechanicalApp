package com.biomechanics.backend.controller;

import com.biomechanics.backend.model.dto.AnalysisResultDTO;
import com.biomechanics.backend.model.dto.UserDTO;
import com.biomechanics.backend.service.SpecialistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SPECIALIST', 'ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Specialist", description = "Endpoints for specialists to manage their patients and reports")
public class SpecialistController {
    private final SpecialistService specialistService;

    @GetMapping("/my-patients")
    @Operation(summary = "Get list of assigned patients")
    public ResponseEntity<List<UserDTO>> getMyPatients(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        List<UserDTO> patients = specialistService.getAssignedPatients(userDetails.getUsername());
        return ResponseEntity.ok(patients);
    }

    @GetMapping("/{patientId}/reports/{sessionId}")
    @Operation(summary = "Get biomechanical report of an assigned patient")
    public ResponseEntity<AnalysisResultDTO> getPatientReport(
            @PathVariable Long patientId,
            @PathVariable Long sessionId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        AnalysisResultDTO report = specialistService.getPatientSessionReport(
                userDetails.getUsername(), patientId, sessionId
        );
        return ResponseEntity.ok(report);
    }


    @GetMapping("/{patientId}/history")
    @Operation(summary = "Get scan history of an assigned patient")
    public ResponseEntity<List<AnalysisResultDTO>> getPatientHistory(
            @PathVariable Long patientId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        List<AnalysisResultDTO> history = specialistService.getPatientHistory(
                userDetails.getUsername(), patientId
        );
        return ResponseEntity.ok(history);
    }

    @PutMapping("/{patientId}/sessions/{sessionId}/notes")
    @Operation(summary = "Add clinical notes to a patient's scan session")
    public ResponseEntity<Void> addClinicalNotes(
            @PathVariable Long patientId,
            @PathVariable Long sessionId,
            @RequestBody @NotNull String clinicalNotes,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        specialistService.addClinicalNotes(
                userDetails.getUsername(), patientId, sessionId, clinicalNotes
        );
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/assign")
    @Operation(summary = "Assign a patient to the current specialist")
    public ResponseEntity<Void> assignPatient(
            @RequestParam String patientEmail,
            @RequestParam(required = false) String referralReason,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        specialistService.assignPatient(userDetails.getUsername(), patientEmail, referralReason);
        return ResponseEntity.noContent().build();
    }
}
