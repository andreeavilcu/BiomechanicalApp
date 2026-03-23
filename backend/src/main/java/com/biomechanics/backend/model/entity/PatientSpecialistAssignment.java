package com.biomechanics.backend.model.entity;

import com.biomechanics.backend.model.enums.AssignmentStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "patient_specialist_assignments",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_patient_specialist",
                        columnNames = {"patient_id", "specialist_id"}
                )
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatientSpecialistAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "assignment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private User patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "specialist_id", nullable = false)
    private User specialist;

    @Column(name = "assigned_date", nullable = false)
    private LocalDate assignedDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private AssignmentStatus status = AssignmentStatus.ACTIVE;

    @Column(name = "referral_reason", columnDefinition = "TEXT")
    private String referralReason;

    @Column(name = "clinical_notes", columnDefinition = "TEXT")
    private String clinicalNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.assignedDate == null) {
            this.assignedDate = LocalDate.now();
        }
    }

    public boolean isActive() {
        return AssignmentStatus.ACTIVE.equals(this.status);
    }
}
