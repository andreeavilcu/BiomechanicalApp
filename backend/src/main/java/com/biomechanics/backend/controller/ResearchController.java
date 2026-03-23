package com.biomechanics.backend.controller;

import com.biomechanics.backend.model.dto.research.AggregateMetricsDTO;
import com.biomechanics.backend.model.dto.research.PostureTrendDTO;
import com.biomechanics.backend.model.enums.RiskLevel;
import com.biomechanics.backend.service.ResearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/research")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('RESEARCHER', 'ADMIN')")
@Tag(name = "Research", description = "Anonymized and aggregated data endpoints for research - RESEARCHER and ADMIN only")
@SecurityRequirement(name = "bearerAuth")
public class ResearchController {
    private final ResearchService researchService;

    @GetMapping("/metrics/aggregate")
    @Operation(
            summary = "Get aggregated biomechanical metrics",
            description = "Returns cohort-level averages, standard deviations, and percentiles. Data is 100% anonymized."
    )
    public ResponseEntity<AggregateMetricsDTO> getAggregateMetrics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(researchService.getAggregateMetrics(from, to));
    }

    @GetMapping("/posture-trends")
    @Operation(
            summary = "Get temporal posture trends",
            description = "Retrieves the evolution of the average posture score over a specified number of recent days."
    )
    public ResponseEntity<List<PostureTrendDTO>> getPostureTrends(
            @RequestParam(defaultValue = "90") int lastDays
    ) {
        return ResponseEntity.ok(researchService.getPostureTrends(lastDays));
    }

    @GetMapping("/export/csv")
    @Operation(
            summary = "Export anonymized CSV dataset",
            description = "Exports an anonymized dataset suitable for external statistical analysis. Personally identifiable information (PII) is completely excluded."
    )
    public ResponseEntity<String> exportAnonymizedCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        String csvContent = researchService.exportAnonymizedCsv(from, to);
        return ResponseEntity.ok()
                .header("Content-Type", "text/csv")
                .header("Content-Disposition", "attachment; filename=\"biomechanics_anonymized.csv\"")
                .body(csvContent);
    }
}
