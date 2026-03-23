package com.biomechanics.backend.model.entity;

import com.biomechanics.backend.model.enums.Gender;
import com.biomechanics.backend.model.enums.UserRole;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    @Column(name = "height_cm", precision = 5, scale = 2)
    private BigDecimal heightCm;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20, nullable = false)
    private UserRole role = UserRole.PATIENT;

    @Column(name = "specialization", length = 150)
    private String specialization;

    @Column(name = "license_number", length = 50, unique = true)
    private String licenseNumber;

    @Column(name = "institution", length = 200)
    private String institution;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @OneToMany(mappedBy = "specialist", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<PatientSpecialistAssignment> patientAssignments = new HashSet<>();

    @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<PatientSpecialistAssignment> specialistAssignments = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public int getAge() {
        if (dateOfBirth == null) return 0;
        return LocalDate.now().getYear() - dateOfBirth.getYear();
    }

    public boolean isPatient() {
        return UserRole.PATIENT.equals(this.role);
    }

    public boolean isSpecialist() {
        return UserRole.SPECIALIST.equals(this.role);
    }

    public boolean isResearcher() {
        return UserRole.RESEARCHER.equals(this.role);
    }

    public boolean isAdmin() {
        return UserRole.ADMIN.equals(this.role);
    }
}
