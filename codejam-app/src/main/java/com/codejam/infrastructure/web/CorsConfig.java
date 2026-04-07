package com.codejam.infrastructure.web;

import com.codejam.auth.config.MicroserviceConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CORS for browser clients. Exposed as {@link CorsConfigurationSource} so
 * {@link org.springframework.security.config.annotation.web.builders.HttpSecurity#cors}
 * runs in the Security filter chain (required for preflight before auth).
 */
@Configuration
@RequiredArgsConstructor
public class CorsConfig {

    private final MicroserviceConfig microserviceConfig;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        String origins = microserviceConfig.getGateway().getCors().getAllowedOrigins();
        List<String> originList = Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        config.setAllowedOrigins(originList);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"));
        // Cannot use "*" with allowCredentials(true); Spring reflects requested headers on preflight
        config.addAllowedHeader(CorsConfiguration.ALL);
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
