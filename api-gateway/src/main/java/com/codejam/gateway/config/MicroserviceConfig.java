package com.codejam.gateway.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

@RefreshScope
@ConfigurationProperties(prefix = "gateway")
@Data
public class MicroserviceConfig {

    @Value("${jwt.secret}")
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
        if (jwtSecret != null) {
            jwtSecret = jwtSecret.trim();
        }
    }

    @Data
    public static class Cors {
        private String allowedOrigins = "http://localhost:3000,http://localhost:5173";
    }

    @Data
    public static class RateLimit {
        private int maxRequests = 5;
        private long windowDurationSeconds = 60;
    }
}

