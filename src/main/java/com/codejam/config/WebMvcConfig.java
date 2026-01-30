package com.codejam.config;

import com.codejam.gateway.config.MicroserviceConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final com.codejam.gateway.config.MicroserviceConfig microserviceConfig;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = microserviceConfig.getCors().getAllowedOrigins() != null
                ? microserviceConfig.getCors().getAllowedOrigins().split(",")
                : new String[]{"http://localhost:3000", "http://localhost:5173"};
        registry.addMapping("/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
