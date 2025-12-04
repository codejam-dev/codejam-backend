package com.codejam.gateway.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class AppConfig {

    private final MicroserviceConfig microserviceConfig;

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        
        // Get CORS origins from config (comma-separated string)
        // Use default if config is not available
        String allowedOrigins = "http://localhost:3000,http://localhost:5173";
        if (microserviceConfig != null && microserviceConfig.getCors() != null 
                && microserviceConfig.getCors().getAllowedOrigins() != null) {
            allowedOrigins = microserviceConfig.getCors().getAllowedOrigins();
        }
        
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        corsConfig.setAllowedOriginPatterns(origins);
        
        corsConfig.setMaxAge(3600L);
        corsConfig.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        corsConfig.setAllowedHeaders(List.of("*"));
        corsConfig.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/v1/api/**", corsConfig);

        return new CorsWebFilter(source);
    }
}

