package com.biomechanics.backend.repository;

import com.biomechanics.backend.model.entity.PatientSpecialistAssignment;
import com.biomechanics.backend.model.entity.User;
import com.biomechanics.backend.model.enums.AssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PatientSpecialistAssignmentRepository extends JpaRepository<PatientSpecialistAssignment, Long>{
    List<PatientSpecialistAssignment> findBySpecialistAndStatus(User specialist, AssignmentStatus status);

    boolean existsBySpecialistAndPatient(User specialist, User patient);

    boolean existsBySpecialistAndPatientId(User specialist, Long patientId);

    Optional<PatientSpecialistAssignment> findBySpecialistAndPatientId(User specialist, Long patientId);
}
