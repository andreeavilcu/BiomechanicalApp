package com.biomechanics.backend.controller;

import com.biomechanics.backend.model.dto.AnalysisResultDTO;
import com.biomechanics.backend.model.dto.UserDTO;
import com.biomechanics.backend.model.enums.ProcessingStatus;
import com.biomechanics.backend.model.enums.UserRole;
import com.biomechanics.backend.service.SpecialistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SpecialistController Tests")
class SpecialistControllerTest {

    @Mock private SpecialistService specialistService;
    @Mock private UserDetails userDetails;

    @InjectMocks
    private SpecialistController specialistController;

    @BeforeEach
    void setUp() {
        when(userDetails.getUsername()).thenReturn("spec@test.com");
    }

    @Nested
    @DisplayName("getMyPatients()")
    class GetMyPatients {

        @Test
        @DisplayName("200 OK and list of assigned patients")
        void shouldReturn200WithPatients() {
            List<UserDTO> patients = List.of(
                    UserDTO.builder().id(1L).email("pac@test.com").role(UserRole.PATIENT).build()
            );
            when(specialistService.getAssignedPatients("spec@test.com")).thenReturn(patients);

            ResponseEntity<List<UserDTO>> response = specialistController.getMyPatients(userDetails);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
            assertThat(response.getBody().get(0).getEmail()).isEqualTo("pac@test.com");
        }

        @Test
        @DisplayName("Empty list when specialist has no patients")
        void shouldReturn200WithEmptyList() {
            when(specialistService.getAssignedPatients("spec@test.com")).thenReturn(List.of());

            ResponseEntity<List<UserDTO>> response = specialistController.getMyPatients(userDetails);

            assertThat(response.getBody()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getPatientReport()")
    class GetPatientReport {

        @Test
        @DisplayName("200 OK and assigned patient's report")
        void shouldReturn200WithReport() {
            AnalysisResultDTO report = AnalysisResultDTO.builder()
                    .sessionId(5L)
                    .status(ProcessingStatus.COMPLETED)
                    .recommendations(List.of())
                    .build();

            when(specialistService.getPatientSessionReport("spec@test.com", 1L, 5L))
                    .thenReturn(report);

            ResponseEntity<AnalysisResultDTO> response =
                    specialistController.getPatientReport(1L, 5L, userDetails);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getSessionId()).isEqualTo(5L);
        }

        @Test
        @DisplayName("AccessDeniedException when patient is not assigned")
        void shouldPropagateAccessDenied() {
            when(specialistService.getPatientSessionReport(any(), anyLong(), anyLong()))
                    .thenThrow(new AccessDeniedException("Unassigned patient"));

            assertThatThrownBy(() -> specialistController.getPatientReport(99L, 5L, userDetails))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("getClinicalNotes()")
    class GetClinicalNotes {

        @Test
        @DisplayName("200 OK and clinical notes for the assigned patient")
        void shouldReturn200WithNotes() {
            when(specialistService.getClinicalNotes("spec@test.com", 1L))
                    .thenReturn("Pacient cu scolioza moderata.");

            ResponseEntity<String> response =
                    specialistController.getClinicalNotes(1L, userDetails);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo("Pacient cu scolioza moderata.");
        }

        @Test
        @DisplayName("200 OK with empty string when no notes exist")
        void shouldReturn200WithEmptyNotes() {
            when(specialistService.getClinicalNotes("spec@test.com", 1L)).thenReturn("");

            ResponseEntity<String> response =
                    specialistController.getClinicalNotes(1L, userDetails);

            assertThat(response.getBody()).isEmpty();
        }
    }

    @Nested
    @DisplayName("saveClinicalNotes()")
    class SaveClinicalNotes {

        @Test
        @DisplayName("204 No Content on successful save")
        void shouldReturn204OnSave() {
            doNothing().when(specialistService).addClinicalNotes(any(), anyLong(), any(), any());

            ResponseEntity<Void> response = specialistController
                    .saveClinicalNotes(1L, "Note noi.", userDetails);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }

        @Test
        @DisplayName("Service receives specialist's email and notes")
        void shouldDelegateToService() {
            doNothing().when(specialistService).addClinicalNotes(any(), anyLong(), any(), any());

            specialistController.saveClinicalNotes(1L, "Note de test.", userDetails);

            verify(specialistService).addClinicalNotes("spec@test.com", 1L, null, "Note de test.");
        }
    }

    @Nested
    @DisplayName("assignPatient()")
    class AssignPatient {

        @Test
        @DisplayName("204 No Content on successful assignment")
        void shouldReturn204OnAssign() {
            doNothing().when(specialistService).assignPatient(any(), any(), any());

            ResponseEntity<Void> response = specialistController
                    .assignPatient("pac@test.com", "Referral reason", userDetails);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }

        @Test
        @DisplayName("RuntimeException (patient already assigned) is propagated")
        void shouldPropagateAlreadyAssigned() {
            doThrow(new RuntimeException("already assigned"))
                    .when(specialistService).assignPatient(any(), any(), any());

            assertThatThrownBy(() ->
                    specialistController.assignPatient("pac@test.com", null, userDetails))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("already assigned");
        }

        @Test
        @DisplayName("Service receives specialist's email, patient's email, and reason")
        void shouldDelegateCorrectly() {
            doNothing().when(specialistService).assignPatient(any(), any(), any());

            specialistController.assignPatient("pac@test.com", "Dureri de spate", userDetails);

            verify(specialistService).assignPatient("spec@test.com", "pac@test.com", "Dureri de spate");
        }
    }
}
