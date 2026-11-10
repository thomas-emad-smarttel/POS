package com.restaurantpos.cloud.config;

import com.restaurantpos.application.system.ServerHealthUseCase;
import com.restaurantpos.domain.port.SystemClockPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Baseline Cloud Backend wiring and security configuration.
 */
@Configuration
public class CloudSecurityConfig {

    @Bean
    public ServerHealthUseCase cloudHealthUseCase(SystemClockPort systemClockPort) {
        return new ServerHealthUseCase(systemClockPort);
    }

    @Bean
    public SecurityFilterChain cloudSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/cloud/health", "/actuator/health").permitAll()
                        .anyRequest().authenticated()
                );
        return http.build();
    }
}
