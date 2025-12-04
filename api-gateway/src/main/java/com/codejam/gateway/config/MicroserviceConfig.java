package com.codejam.gateway.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

/**
 * Centralized configuration class for api-gateway
 * All dynamic configuration values from Config Server are loaded here
 * 
 * This class has @RefreshScope to enable dynamic configuration refresh
 * without restarting the service. Call /actuator/refresh to update values.
 */
@RefreshScope
@ConfigurationProperties(prefix = "gateway")
@Data
public class MicroserviceConfig {

    @Value("${jwt.secret:changeit1234567890changeit1234567890}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpiration;

    private Cors cors = new Cors();
    private RateLimit rateLimit = new RateLimit();
    
    @PostConstruct
    public void init() {
        if (cors == null) {
            cors = new Cors();
        }
        if (rateLimit == null) {
            rateLimit = new RateLimit();
        }
    }

    @Data
    public static class Cors {
        private String allowedOrigins = "http://localhost:3000,http://localhost:5173";
    }

    @Data
    public static class RateLimit {
        private boolean enabled = false;
        private int requestsPerSecond = 100;
    }
}

