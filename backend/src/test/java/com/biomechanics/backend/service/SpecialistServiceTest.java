package com.biomechanics.backend.service;

import com.biomechanics.backend.mapper.UserMapper;
import com.biomechanics.backend.model.dto.UserDTO;
import com.biomechanics.backend.model.entity.PatientSpecialistAssignment;
import com.biomechanics.backend.model.entity.User;
import com.biomechanics.backend.model.enums.AssignmentStatus;
import com.biomechanics.backend.model.enums.UserRole;
import com.biomechanics.backend.repository.PatientSpecialistAssignmentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SpecialistService Tests")
class SpecialistServiceTest {

    @Mock private UserMapper                            userMapper;
    @Mock private PatientSpecialistAssignmentRepository assignmentRepository;
    @Mock private ScanSessionService                    scanSessionService;

    @InjectMocks
    private SpecialistService specialistService;

    private User buildUser(Long id, String email, UserRole role) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setRole(role);
        u.setIsActive(true);
        return u;
    }

    private PatientSpecialistAssignment buildAssignment(User specialist, User patient) {
        PatientSpecialistAssignment a = new PatientSpecialistAssignment();
        a.setId(1L);
        a.setSpecialist(specialist);
        a.setPatient(patient);
        a.setStatus(AssignmentStatus.ACTIVE);
        a.setAssignedDate(LocalDate.now());
        return a;
    }

    @Nested
    @DisplayName("getAssignedPatients()")
    class GetAssignedPatients {

        @Test
        @DisplayName("Returns list of patients assigned to the specialist")
        void shouldReturnAssignedPatients() {
            User specialist = buildUser(1L, "spec@test.com", UserRole.SPECIALIST);
            User patient    = buildUser(2L, "pac@test.com",  UserRole.PATIENT);

            PatientSpecialistAssignment assignment = buildAssignment(specialist, patient);

            when(userMapper.getUserByEmail("spec@test.com")).thenReturn(specialist);
            when(assignmentRepository.findBySpecialistAndStatus(specialist, AssignmentStatus.ACTIVE))
                    .thenReturn(List.of(assignment));
            when(userMapper.toDTO(patient)).thenReturn(
                    UserDTO.builder().id(2L).email("pac@test.com").role(UserRole.PATIENT).build()
            );

            List<UserDTO> result = specialistService.getAssignedPatients("spec@test.com");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getEmail()).isEqualTo("pac@test.com");
        }

        @Test
        @DisplayName("Specialist with no patients returns empty list")
        void shouldReturnEmptyListWhenNoPatientsAssigned() {
            User specialist = buildUser(1L, "spec@test.com", UserRole.SPECIALIST);

            when(userMapper.getUserByEmail("spec@test.com")).thenReturn(specialist);
            when(assignmentRepository.findBySpecialistAndStatus(specialist, AssignmentStatus.ACTIVE))
                    .thenReturn(List.of());

            assertThat(specialistService.getAssignedPatients("spec@test.com")).isEmpty();
        }

        @Test
        @DisplayName("User with non-SPECIALIST role throws RuntimeException")
        void shouldThrowForNonSpecialistRole() {
            User researcher = buildUser(3L, "res@test.com", UserRole.RESEARCHER);
            when(userMapper.getUserByEmail("res@test.com")).thenReturn(researcher);

            assertThatThrownBy(() -> specialistService.getAssignedPatients("res@test.com"))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("getClinicalNotes()")
    class GetClinicalNotes {

        @Test
        @DisplayName("Clinical notes are returned correctly")
        void shouldReturnClinicalNotes() {
            User specialist = buildUser(1L, "spec@test.com", UserRole.SPECIALIST);
            User patient    = buildUser(2L, "pac@test.com",  UserRole.PATIENT);

            PatientSpecialistAssignment assignment = buildAssignment(specialist, patient);
            assignment.setClinicalNotes("Pacient cu scolioza moderata.");

            when(userMapper.getUserByEmail("spec@test.com")).thenReturn(specialist);
            when(assignmentRepository.existsBySpecialistAndPatientId(specialist, 2L)).thenReturn(true);
            when(assignmentRepository.findBySpecialistAndPatientId(specialist, 2L))
                    .thenReturn(Optional.of(assignment));

            String notes = specialistService.getClinicalNotes("spec@test.com", 2L);

            assertThat(notes).isEqualTo("Pacient cu scolioza moderata.");
        }

        @Test
        @DisplayName("Null notes return empty string")
        void shouldReturnEmptyStringWhenNotesAreNull() {
            User specialist = buildUser(1L, "spec@test.com", UserRole.SPECIALIST);
            PatientSpecialistAssignment assignment = buildAssignment(specialist, buildUser(2L, "p@t.com", UserRole.PATIENT));
            assignment.setClinicalNotes(null);

            when(userMapper.getUserByEmail("spec@test.com")).thenReturn(specialist);
            when(assignmentRepository.existsBySpecialistAndPatientId(specialist, 2L)).thenReturn(true);
            when(assignmentRepository.findBySpecialistAndPatientId(specialist, 2L))
                    .thenReturn(Optional.of(assignment));

            assertThat(specialistService.getClinicalNotes("spec@test.com", 2L)).isEmpty();
        }

        @Test
        @DisplayName("Unassigned patient throws AccessDeniedException")
        void shouldThrowForUnassignedPatient() {
            User specialist = buildUser(1L, "spec@test.com", UserRole.SPECIALIST);
            when(userMapper.getUserByEmail("spec@test.com")).thenReturn(specialist);
            when(assignmentRepository.existsBySpecialistAndPatientId(specialist, 99L)).thenReturn(false);

            assertThatThrownBy(() -> specialistService.getClinicalNotes("spec@test.com", 99L))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("addClinicalNotes()")
    class AddClinicalNotes {

        @Test
        @DisplayName("Clinical notes are saved successfully")
        void shouldSaveNotesSuccessfully() {
            User specialist = buildUser(1L, "spec@test.com", UserRole.SPECIALIST);
            User patient    = buildUser(2L, "pac@test.com",  UserRole.PATIENT);
            PatientSpecialistAssignment assignment = buildAssignment(specialist, patient);

            when(userMapper.getUserByEmail("spec@test.com")).thenReturn(specialist);
            when(assignmentRepository.existsBySpecialistAndPatientId(specialist, 2L)).thenReturn(true);
            when(assignmentRepository.findBySpecialistAndPatientId(specialist, 2L))
                    .thenReturn(Optional.of(assignment));

            specialistService.addClinicalNotes("spec@test.com", 2L, null, "Note noi.");

            assertThat(assignment.getClinicalNotes()).isEqualTo("Note noi.");
            verify(assignmentRepository).save(assignment);
        }
    }

    @Nested
    @DisplayName("assignPatient()")
    class AssignPatient {

        @Test
        @DisplayName("Successful assignment creates a new assignment")
        void shouldAssignPatientSuccessfully() {
            User specialist = buildUser(1L, "spec@test.com", UserRole.SPECIALIST);
            User patient    = buildUser(2L, "pac@test.com",  UserRole.PATIENT);

            when(userMapper.getUserByEmail("spec@test.com")).thenReturn(specialist);
            when(userMapper.getUserByEmail("pac@test.com")).thenReturn(patient);
            when(assignmentRepository.existsBySpecialistAndPatient(specialist, patient)).thenReturn(false);

            specialistService.assignPatient("spec@test.com", "pac@test.com", "Referral reason");

            verify(assignmentRepository).save(any(PatientSpecialistAssignment.class));
        }

        @Test
        @DisplayName("Already assigned patient throws RuntimeException")
        void shouldThrowWhenPatientAlreadyAssigned() {
            User specialist = buildUser(1L, "spec@test.com", UserRole.SPECIALIST);
            User patient    = buildUser(2L, "pac@test.com",  UserRole.PATIENT);

            when(userMapper.getUserByEmail("spec@test.com")).thenReturn(specialist);
            when(userMapper.getUserByEmail("pac@test.com")).thenReturn(patient);
            when(assignmentRepository.existsBySpecialistAndPatient(specialist, patient)).thenReturn(true);

            assertThatThrownBy(() ->
                    specialistService.assignPatient("spec@test.com", "pac@test.com", null))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("already assigned");
        }

        @Test
        @DisplayName("Assigning a non-patient throws RuntimeException")
        void shouldThrowWhenAssigningNonPatient() {
            User specialist  = buildUser(1L, "spec@test.com", UserRole.SPECIALIST);
            User nonPatient  = buildUser(3L, "res@test.com",  UserRole.RESEARCHER);

            when(userMapper.getUserByEmail("spec@test.com")).thenReturn(specialist);
            when(userMapper.getUserByEmail("res@test.com")).thenReturn(nonPatient);

            assertThatThrownBy(() ->
                    specialistService.assignPatient("spec@test.com", "res@test.com", null))
                    .isInstanceOf(RuntimeException.class);
        }
    }
}
