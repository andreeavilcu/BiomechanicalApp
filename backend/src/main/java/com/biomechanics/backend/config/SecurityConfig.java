package com.biomechanics.backend.config;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        log.info("Configuring Security Filter Chain (DEVELOPMENT MODE - All endpoints public)");

        http
                .csrf(AbstractHttpConfigurer::disable)

                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/actuator/health",
                                "/api/scans/health"
                        ).permitAll()

                        // TODO: For production, protect these with JWT:
                        // .requestMatchers("/api/scans/**").authenticated()
                        // .requestMatchers("/api/users/**").authenticated()
                        .anyRequest().permitAll()
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        log.info("Configuring CORS (DEVELOPMENT MODE - All origins allowed)");

        CorsConfiguration configuration = new CorsConfiguration();

        // DEVELOPMENT: Allow all origins
        // TODO: For production, restrict to specific frontend URL:
        // configuration.setAllowedOrigins(List.of("https://your-frontend-domain.com"));
        configuration.setAllowedOriginPatterns(List.of("*"));

        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
        ));

        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "Accept",
                "X-Requested-With"
        ));

        configuration.setExposedHeaders(List.of(
                "Authorization"
        ));

        configuration.setAllowCredentials(true);

        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    /*
     * TODO: JWT AUTHENTICATION
     *
     * @Bean
     * public JwtAuthenticationFilter jwtAuthenticationFilter() {
     *     return new JwtAuthenticationFilter();
     * }
     *
     *
     * http
     *     .addFilterBefore(jwtAuthenticationFilter(),
     *                      UsernamePasswordAuthenticationFilter.class)
     *     .authorizeHttpRequests(auth -> auth
     *         .requestMatchers("/api/auth/**").permitAll()  // Login/Register
     *         .requestMatchers("/api/scans/**").authenticated()
     *         .anyRequest().authenticated()
     *     );
     *
     * @Bean
     * public PasswordEncoder passwordEncoder() {
     *     return new BCryptPasswordEncoder();
     * }
     *
     * @Bean
     * public AuthenticationManager authenticationManager(
     *         AuthenticationConfiguration config) throws Exception {
     *     return config.getAuthenticationManager();
     * }
     */
}
