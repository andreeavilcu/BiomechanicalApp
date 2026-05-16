package com.biomechanics.backend.mapper;

import com.biomechanics.backend.model.dto.UserDTO;
import com.biomechanics.backend.model.entity.User;
import com.biomechanics.backend.model.enums.Gender;
import com.biomechanics.backend.model.enums.UserRole;
import com.biomechanics.backend.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserMapper Tests")
class UserMapperTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserMapper userMapper;

    private User buildUser() {
        User u = new User();
        u.setId(1L);
        u.setEmail("pac@test.com");
        u.setFirstName("Ion");
        u.setLastName("Popescu");
        u.setDateOfBirth(LocalDate.of(1990, 5, 20));
        u.setGender(Gender.MALE);
        u.setHeightCm(BigDecimal.valueOf(175));
        u.setRole(UserRole.PATIENT);
        u.setIsActive(true);
        return u;
    }

    @Nested
    @DisplayName("toDTO()")
    class ToDTO {

        @Test
        @DisplayName("Valid User - DTO populated correctly")
        void shouldMapAllFields() {
            User user = buildUser();

            UserDTO dto = userMapper.toDTO(user);

            assertThat(dto).isNotNull();
            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getEmail()).isEqualTo("pac@test.com");
            assertThat(dto.getFirstName()).isEqualTo("Ion");
            assertThat(dto.getLastName()).isEqualTo("Popescu");
            assertThat(dto.getGender()).isEqualTo(Gender.MALE);
            assertThat(dto.getRole()).isEqualTo(UserRole.PATIENT);
            assertThat(dto.getIsActive()).isTrue();
            assertThat(dto.getHeightCm()).isEqualByComparingTo(BigDecimal.valueOf(175));
        }

        @Test
        @DisplayName("Null User - returns null")
        void shouldReturnNullForNullUser() {
            assertThat(userMapper.toDTO(null)).isNull();
        }

        @Test
        @DisplayName("Age is calculated from date of birth")
        void shouldCalculateAge() {
            User user = buildUser();

            UserDTO dto = userMapper.toDTO(user);

            assertThat(dto.getAge()).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("getUserByEmail()")
    class GetUserByEmail {

        @Test
        @DisplayName("Existing email - returns User entity")
        void shouldReturnUserForExistingEmail() {
            User user = buildUser();
            when(userRepository.findByEmail("pac@test.com")).thenReturn(Optional.of(user));

            User result = userMapper.getUserByEmail("pac@test.com");

            assertThat(result).isEqualTo(user);
        }

        @Test
        @DisplayName("Non-existent email - throws RuntimeException")
        void shouldThrowForMissingEmail() {
            when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userMapper.getUserByEmail("ghost@test.com"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("ghost@test.com");
        }
    }

    @Nested
    @DisplayName("getUserById()")
    class GetUserById {

        @Test
        @DisplayName("Existing ID - returns User entity")
        void shouldReturnUserForExistingId() {
            User user = buildUser();
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            User result = userMapper.getUserById(1L);

            assertThat(result).isEqualTo(user);
        }

        @Test
        @DisplayName("Non-existent ID - throws RuntimeException")
        void shouldThrowForMissingId() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userMapper.getUserById(999L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("999");
        }
    }
}
