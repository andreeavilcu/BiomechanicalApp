package com.biomechanics.backend.model.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class ScanUploadRequestDTO {
    @NotNull(message = "The .ply file is mandatory")
    private MultipartFile file;

    @NotNull(message = "User ID is mandatory")
    @Positive(message = "User ID must be positive")
    private Long userId;

    @NotNull(message = "Height is mandatory")
    @DecimalMin(value = "50.0", message = "Minimum height is 50 cm")
    @DecimalMax(value = "250.0", message = "Maximum height is 250 cm")
    private Double heightCm;

    private String scanType = "LIDAR";

}
