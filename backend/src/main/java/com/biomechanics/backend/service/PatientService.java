package com.biomechanics.backend.service;

import com.biomechanics.backend.mapper.UserMapper;
import com.biomechanics.backend.model.dto.UserDTO;
import com.biomechanics.backend.model.entity.User;
import com.biomechanics.backend.model.enums.AssignmentStatus;
import com.biomechanics.backend.repository.PatientSpecialistAssignmentRepository;
import com.biomechanics.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PatientService {
    private final UserRepository userRepository;
    private final PatientSpecialistAssignmentRepository assignmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public UserDTO getProfile(String email){
        User user = userMapper.getUserByEmail(email);
        return userMapper.toDTO(user);
    }

    @Transactional
    public UserDTO updateProfile(String email, UserDTO request) {
        User user = userMapper.getUserByEmail(email);

        if (request.getFirstName() != null) user.setFirstName(request.getFirstName());
        if (request.getLastName()  != null) user.setLastName(request.getLastName());
        if (request.getDateOfBirth() != null) user.setDateOfBirth(request.getDateOfBirth());
        if (request.getGender()    != null) user.setGender(request.getGender());
        if (request.getHeightCm()  != null) user.setHeightCm(request.getHeightCm());

        User saved = userRepository.save(user);
        log.info("Profile updated for: {}", email);
        return userMapper.toDTO(saved);
    }

    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword) {
        User user = userMapper.getUserByEmail(email);

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect.");
        }

        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException(
                    "New password must be at least 8 characters long."
            );
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Password changed for: {}", email);
    }

    public List<UserDTO> getAssignedSpecialists(String patientEmail) {
        User patient = userMapper.getUserByEmail(patientEmail);

        return assignmentRepository
                .findByPatientAndStatus(
                        patient,
                        AssignmentStatus.ACTIVE
                )
                .stream()
                .map(a -> userMapper.toDTO(a.getSpecialist()))
                .collect(Collectors.toList());
    }

}
