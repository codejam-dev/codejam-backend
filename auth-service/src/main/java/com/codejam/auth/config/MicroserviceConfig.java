package com.codejam.auth.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

/**
 * Centralized configuration class for auth-service
 * All dynamic configuration values from Config Server are loaded here
 * 
 * This class has @RefreshScope to enable dynamic configuration refresh
 * without restarting the service. Call /actuator/refresh to update values.
 */
@Configuration
@RefreshScope
@ConfigurationProperties(prefix = "app")
@Data
public class MicroserviceConfig {

    // Application Configuration
    private OAuth oauth = new OAuth();
    private Otp otp = new Otp();
    private String frontendUrl;

    // JWT Configuration (different prefix)
    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpiration;

    // Mail Configuration (different prefix)
    @Value("${spring.mail.username}")
    private String mailUsername;

    @Value("${spring.mail.password}")
    private String mailPassword;

    @Value("${spring.mail.host:smtp.gmail.com}")
    private String mailHost;

    @Value("${spring.mail.port:587}")
    private int mailPort;

    @Data
    public static class OAuth {
        private String successRedirect;
        private String failureRedirect;
    }

    @Data
    public static class Otp {
        private boolean enableDynamic = false;
        private int ttl = 600;
        private int length = 6;
        private int maxAttempts = 5;
        private String testValue;
        private String testTransactionId;
    }
}

