package com.biomechanics.backend.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ResourceNotFoundException Tests")
class ResourceNotFoundExceptionTest {

    @Nested
    @DisplayName("Constructor(String message)")
    class MessageConstructor {

        @Test
        @DisplayName("Stores the provided message directly")
        void shouldStoreMessage() {
            ResourceNotFoundException ex = new ResourceNotFoundException("Resource not found");

            assertThat(ex.getMessage()).isEqualTo("Resource not found");
        }
    }

    @Nested
    @DisplayName("Constructor(String resourceName, Long id)")
    class ResourceNameAndIdConstructor {

        @Test
        @DisplayName("Formats message as '<Resource> with ID <id> does not exist.'")
        void shouldFormatMessageWithId() {
            ResourceNotFoundException ex = new ResourceNotFoundException("ScanSession", 42L);

            assertThat(ex.getMessage()).isEqualTo("ScanSession with ID 42 does not exist.");
        }

        @Test
        @DisplayName("Includes the resource name and numeric ID in the message")
        void shouldIncludeResourceNameAndId() {
            ResourceNotFoundException ex = new ResourceNotFoundException("User", 1L);

            assertThat(ex.getMessage()).contains("User").contains("1");
        }
    }

    @Nested
    @DisplayName("Constructor(String resourceName, String identifier)")
    class ResourceNameAndStringIdentifierConstructor {

        @Test
        @DisplayName("Formats message as '<Resource> '<identifier>' does not exist.'")
        void shouldFormatMessageWithStringIdentifier() {
            ResourceNotFoundException ex = new ResourceNotFoundException("User", "test@example.com");

            assertThat(ex.getMessage()).isEqualTo("User 'test@example.com' does not exist.");
        }

        @Test
        @DisplayName("Includes both resource name and identifier in the message")
        void shouldIncludeResourceNameAndIdentifier() {
            ResourceNotFoundException ex = new ResourceNotFoundException("ScanSession", "session-abc");

            assertThat(ex.getMessage()).contains("ScanSession").contains("session-abc");
        }
    }
}
