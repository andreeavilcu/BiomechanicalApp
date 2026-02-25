package com.biomechanics.backend.model.dto;

import com.biomechanics.backend.model.enums.Gender;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private Long id;

    @NotBlank(message = "Email is mandatory")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "First name is mandatory")
    @Size(max = 100)
    private String firstName;

    @NotBlank(message = "Last name is mandatory")
    @Size(max = 100)
    private String lastName;

    @NotNull(message = "Date of birth is mandatory")
    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    @NotNull(message = "Gender is mandatory")
    private Gender gender;

    @NotNull(message = "Height is mandatory")
    @DecimalMin(value = "50.0", message = "Minimum height is 50 cm")
    @DecimalMax(value = "250.0", message = "Maximum height is 250 cm")
    private BigDecimal heightCm;

    private Integer age;
}
