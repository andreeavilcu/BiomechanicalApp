package com.biomechanics.backend.service;

import com.biomechanics.backend.model.dto.admin.SystemStatsDTO;
import com.biomechanics.backend.model.dto.UserDTO;
import com.biomechanics.backend.model.entity.User;
import com.biomechanics.backend.model.enums.UserRole;
import com.biomechanics.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;

    public List<UserDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<UserDTO> getUsersByRole(UserRole role) {
        return userRepository.findByRole(role).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserDTO updateUserRole(Long userId, UserRole newRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        UserRole oldRole = user.getRole();
        user.setRole(newRole);
        userRepository.save(user);
        log.info("ADMIN: Changed role for user {} from {} to {}", user.getEmail(), oldRole, newRole);
        return toDTO(user);
    }

    @Transactional
    public UserDTO setUserActiveStatus(Long userId, boolean active) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        user.setIsActive(active);
        userRepository.save(user);
        log.info("ADMIN: {} account for user {}", active ? "Activated" : "Deactivated", user.getEmail());
        return toDTO(user);
    }

    public SystemStatsDTO getSystemStats() {
        List<User> allUsers = userRepository.findAll();
        Map<UserRole, Long> countByRole = allUsers.stream()
                .collect(Collectors.groupingBy(User::getRole, Collectors.counting()));

        return SystemStatsDTO.builder()
                .totalUsers((long) allUsers.size())
                .totalPatients(countByRole.getOrDefault(UserRole.PATIENT, 0L))
                .totalSpecialists(countByRole.getOrDefault(UserRole.SPECIALIST, 0L))
                .totalResearchers(countByRole.getOrDefault(UserRole.RESEARCHER, 0L))
                .totalAdmins(countByRole.getOrDefault(UserRole.ADMIN, 0L))
                .activeUsers(allUsers.stream().filter(User::getIsActive).count())
                .build();
    }

    private UserDTO toDTO(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .dateOfBirth(user.getDateOfBirth())
                .gender(user.getGender())
                .heightCm(user.getHeightCm())
                .age(user.getAge())
                .build();
    }

}