package com.biomechanics.backend.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ScanProcessingException Tests")
class ScanProcessingExceptionTest {

    @Nested
    @DisplayName("Constructor(String message)")
    class MessageOnlyConstructor {

        @Test
        @DisplayName("Stores the message and leaves filename/stage null")
        void shouldStoreMessageAndNullFields() {
            ScanProcessingException ex = new ScanProcessingException("Processing failed");

            assertThat(ex.getMessage()).isEqualTo("Processing failed");
            assertThat(ex.getScanFileName()).isNull();
            assertThat(ex.getProcessingStage()).isNull();
            assertThat(ex.getCause()).isNull();
        }
    }

    @Nested
    @DisplayName("Constructor(String message, Throwable cause)")
    class MessageAndCauseConstructor {

        @Test
        @DisplayName("Stores message and cause; filename/stage remain null")
        void shouldStoreMessageAndCause() {
            RuntimeException cause = new RuntimeException("root cause");
            ScanProcessingException ex = new ScanProcessingException("Processing failed", cause);

            assertThat(ex.getMessage()).isEqualTo("Processing failed");
            assertThat(ex.getCause()).isSameAs(cause);
            assertThat(ex.getScanFileName()).isNull();
            assertThat(ex.getProcessingStage()).isNull();
        }
    }

    @Nested
    @DisplayName("Constructor(String message, String scanFileName, String processingStage)")
    class MessageFileAndStageConstructor {

        @Test
        @DisplayName("Stores message, filename and stage correctly")
        void shouldStoreAllThreeFields() {
            ScanProcessingException ex = new ScanProcessingException(
                    "Processing failed", "scan.ply", "KEYPOINT_DETECTION");

            assertThat(ex.getMessage()).isEqualTo("Processing failed");
            assertThat(ex.getScanFileName()).isEqualTo("scan.ply");
            assertThat(ex.getProcessingStage()).isEqualTo("KEYPOINT_DETECTION");
            assertThat(ex.getCause()).isNull();
        }

        @Test
        @DisplayName("getScanFileName() returns the file name passed to constructor")
        void shouldReturnCorrectScanFileName() {
            ScanProcessingException ex = new ScanProcessingException("err", "patient_scan.ply", "UPLOAD");

            assertThat(ex.getScanFileName()).isEqualTo("patient_scan.ply");
        }

        @Test
        @DisplayName("getProcessingStage() returns the stage passed to constructor")
        void shouldReturnCorrectProcessingStage() {
            ScanProcessingException ex = new ScanProcessingException("err", "scan.ply", "PYTHON");

            assertThat(ex.getProcessingStage()).isEqualTo("PYTHON");
        }
    }

    @Nested
    @DisplayName("Constructor(String message, String scanFileName, String processingStage, Throwable cause)")
    class FullConstructor {

        @Test
        @DisplayName("Stores all four fields including cause")
        void shouldStoreAllFourFields() {
            RuntimeException cause = new RuntimeException("IO error");
            ScanProcessingException ex = new ScanProcessingException(
                    "Processing failed", "scan.ply", "METRICS", cause);

            assertThat(ex.getMessage()).isEqualTo("Processing failed");
            assertThat(ex.getScanFileName()).isEqualTo("scan.ply");
            assertThat(ex.getProcessingStage()).isEqualTo("METRICS");
            assertThat(ex.getCause()).isSameAs(cause);
        }
    }
}
