package com.biomechanics.backend.model.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemStatsDTO {
    private Long totalUsers;
    private Long totalPatients;
    private Long totalSpecialists;
    private Long totalResearchers;
    private Long totalAdmins;
    private Long activeUsers;
}