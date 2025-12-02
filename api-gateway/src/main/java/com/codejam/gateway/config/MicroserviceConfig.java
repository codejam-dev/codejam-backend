package com.codejam.gateway.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

/**
 * Centralized configuration class for api-gateway
 * All dynamic configuration values from Config Server are loaded here
 * 
 * This class has @RefreshScope to enable dynamic configuration refresh
 * without restarting the service. Call /actuator/refresh to update values.
 */
@Configuration
@RefreshScope
@ConfigurationProperties(prefix = "gateway")
@Data
public class MicroserviceConfig {

    // JWT Configuration (different prefix)
    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpiration;

    // Gateway Configuration
    private Cors cors = new Cors();
    private RateLimit rateLimit = new RateLimit();

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

