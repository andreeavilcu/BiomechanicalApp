package com.biomechanics.backend.service;

import com.biomechanics.backend.model.dto.auth.AuthResponseDTO;
import com.biomechanics.backend.model.dto.auth.LoginRequestDTO;
import com.biomechanics.backend.model.dto.auth.RegisterRequestDTO;
import com.biomechanics.backend.model.entity.User;
import com.biomechanics.backend.model.enums.UserRole;
import com.biomechanics.backend.repository.UserRepository;
import com.biomechanics.backend.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;

    @Transactional
    public AuthResponseDTO register(RegisterRequestDTO request) {
        log.info("Attempting registration for email: {}", request.getEmail());

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException(
                    "An account with email " + request.getEmail() + " already exists."
            );
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setDateOfBirth(request.getDateOfBirth());
        user.setGender(request.getGender());
        user.setHeightCm(request.getHeightCm());
        user.setRole(UserRole.PATIENT);
        user.setIsActive(true);

        User savedUser = userRepository.save(user);
        log.info("User registered successfully: {} with role PATIENT", savedUser.getEmail());

        UserDetails userDetails = userDetailsService.loadUserByUsername(savedUser.getEmail());
        String accessToken  = jwtService.generateToken(userDetails, savedUser.getRole());
        String refreshToken = jwtService.generateRefreshToken(userDetails);

        return buildAuthResponse(savedUser, accessToken, refreshToken);
    }

    public AuthResponseDTO login(LoginRequestDTO request) {
        log.info("Login attempt for: {}", request.getEmail());

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );
        } catch (BadCredentialsException e) {
            log.warn("Failed login attempt for: {}", request.getEmail());
            throw new BadCredentialsException("Incorrect email or password.");
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found after authentication"));

        if (!user.getIsActive()) {
            throw new IllegalStateException("Your account has been deactivated. Please contact the administrator.");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String accessToken  = jwtService.generateToken(userDetails, user.getRole());
        String refreshToken = jwtService.generateRefreshToken(userDetails);

        log.info("Successful login for: {} (role: {})", user.getEmail(), user.getRole());
        
        return buildAuthResponse(user, accessToken, refreshToken);
    }

    public AuthResponseDTO refreshToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Missing or invalid refresh token format.");
        }

        final String refreshToken = authorizationHeader.substring(7);
        
        final String userEmail = jwtService.extractUsername(refreshToken);
        if (userEmail == null) {
            throw new IllegalArgumentException("Invalid token.");
        }
        
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found."));

        UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

        if (!jwtService.isTokenValid(refreshToken, userDetails)) {
            throw new IllegalArgumentException("Refresh token expired or invalid.");
        }
        
        String newAccessToken = jwtService.generateToken(userDetails, user.getRole());

        log.info("Token refreshed for: {}", userEmail);
        
        return buildAuthResponse(user, newAccessToken, refreshToken);
    }

    private AuthResponseDTO buildAuthResponse(User user, String accessToken, String refreshToken) {
        return AuthResponseDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .role(user.getRole())
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .build();
    }

}