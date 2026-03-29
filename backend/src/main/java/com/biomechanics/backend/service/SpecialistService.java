package com.biomechanics.backend.service;

import com.biomechanics.backend.mapper.UserMapper;
import com.biomechanics.backend.model.dto.AnalysisResultDTO;
import com.biomechanics.backend.model.dto.UserDTO;
import com.biomechanics.backend.model.entity.PatientSpecialistAssignment;
import com.biomechanics.backend.model.entity.User;
import com.biomechanics.backend.model.enums.AssignmentStatus;
import com.biomechanics.backend.model.enums.UserRole;
import com.biomechanics.backend.repository.PatientSpecialistAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpecialistService {

    private final UserMapper userMapper;
    private final PatientSpecialistAssignmentRepository assignmentRepository;

    public List<UserDTO> getAssignedPatients(String specialistEmail) {
        User specialist = userMapper.getUserByEmail(specialistEmail);
        validateRole(specialist, UserRole.SPECIALIST);

        return assignmentRepository
                .findBySpecialistAndStatus(specialist, AssignmentStatus.ACTIVE)
                .stream()
                .map(a -> userMapper.toDTO(a.getPatient()))
                .collect(Collectors.toList());
    }

    public AnalysisResultDTO getPatientSessionReport(String specialistEmail, Long patientId, Long sessionId) {
        User specialist = userMapper.getUserByEmail(specialistEmail);
        validatePatientIsAssigned(specialist, patientId);

        // TODO: delegare către ScanService.getSessionReport(sessionId)
        return AnalysisResultDTO.builder()
                .sessionId(sessionId)
                .build();
    }

    public List<AnalysisResultDTO> getPatientHistory(String specialistEmail, Long patientId) {
        User specialist = userMapper.getUserByEmail(specialistEmail);
        validatePatientIsAssigned(specialist, patientId);

        // TODO: delegare către ScanService.getHistoryForUser(patientId)
        return List.of();
    }

    @Transactional
    public void addClinicalNotes(String specialistEmail, Long patientId, Long sessionId, String notes) {
        User specialist = userMapper.getUserByEmail(specialistEmail);
        validatePatientIsAssigned(specialist, patientId);

        PatientSpecialistAssignment assignment = assignmentRepository
                .findBySpecialistAndPatientId(specialist, patientId)
                .orElseThrow(() -> new RuntimeException("Assignment not found"));

        assignment.setClinicalNotes(notes);
        assignmentRepository.save(assignment);
        log.info("SPECIALIST {}: Added clinical notes for patient {}", specialistEmail, patientId);
    }

    @Transactional
    public void assignPatient(String specialistEmail, String patientEmail, String referralReason) {
        User specialist = userMapper.getUserByEmail(specialistEmail);
        User patient = userMapper.getUserByEmail(patientEmail);
        validateRole(specialist, UserRole.SPECIALIST);
        validateRole(patient, UserRole.PATIENT);

        if (assignmentRepository.existsBySpecialistAndPatient(specialist, patient)) {
            throw new RuntimeException("Patient already assigned to this specialist");
        }

        PatientSpecialistAssignment assignment = new PatientSpecialistAssignment();
        assignment.setSpecialist(specialist);
        assignment.setPatient(patient);
        assignment.setReferralReason(referralReason);
        assignment.setStatus(AssignmentStatus.ACTIVE);
        assignmentRepository.save(assignment);

        log.info("SPECIALIST {}: Assigned patient {}", specialistEmail, patientEmail);
    }

    private void validateRole(User user, UserRole expectedRole) {
        if (!user.getRole().equals(expectedRole)) {
            throw new RuntimeException("User " + user.getEmail() + " does not have role " + expectedRole);
        }
    }

    private void validatePatientIsAssigned(User specialist, Long patientId) {
        boolean isAssigned = assignmentRepository
                .existsBySpecialistAndPatientId(specialist, patientId);
        if (!isAssigned) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Patient " + patientId + " is not assigned to this specialist"
            );
        }
    }

}
