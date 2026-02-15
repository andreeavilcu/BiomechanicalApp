package com.biomechanics.backend.model.entity;

import com.biomechanics.backend.model.enums.ProcessingStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "scan_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScanSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "scan_file_path", length = 500, nullable = false)
    private String scanFilePath;

    @Column(name = "scan_date", nullable = false)
    private LocalDateTime scanDate;

    @Column(name = "scan_type", length = 50)
    private String scanType = "LIDAR";

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", length = 50)
    private ProcessingStatus processingStatus = ProcessingStatus.PENDING;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "python_method", length = 100)
    private String pythonMethod;

    @Column(name = "ai_confidence_score", precision = 4, scale = 3)
    private BigDecimal aiConfidenceScore;

    @Column(name = "target_height_meters", precision = 4, scale = 2)
    private BigDecimal targetHeightMeters;

    @Column(name = "scaling_factor", precision = 6, scale = 4)
    private BigDecimal scalingFactor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        scanDate = LocalDateTime.now();
    }
}
