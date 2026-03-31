package com.codejam.auth.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Application configuration (auth, execution, and former gateway limits).
 */
@Configuration
@ConfigurationProperties(prefix = "app")
@Data
public class MicroserviceConfig {

    private OAuth oauth = new OAuth();
    private Otp otp = new Otp();
    private String frontendUrl;

    private ExecutionConfig executor = new ExecutionConfig();
    private RunHistoryConfig runHistory = new RunHistoryConfig();
    private Gateway gateway = new Gateway();

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpiration;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Value("${spring.mail.host:smtp.gmail.com}")
    private String mailHost;

    @Value("${spring.mail.port:587}")
    private int mailPort;

    @Value("${app.password.resetTokenExpiration:3600}")
    private long resetTokenExpiration;

    @PostConstruct
    public void init() {
        if (jwtSecret != null) {
            jwtSecret = jwtSecret.trim();
        }
        if (executor == null) {
            executor = new ExecutionConfig();
        }
        if (runHistory == null) {
            runHistory = new RunHistoryConfig();
        }
        if (gateway == null) {
            gateway = new Gateway();
        }
    }

    public ExecutionConfig getExecutor() {
        if (executor == null) {
            executor = new ExecutionConfig();
        }
        return executor;
    }

    public RunHistoryConfig getRunHistory() {
        if (runHistory == null) {
            runHistory = new RunHistoryConfig();
        }
        return runHistory;
    }

    public Gateway getGateway() {
        if (gateway == null) {
            gateway = new Gateway();
        }
        return gateway;
    }

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
        private String fromEmail;
    }

    @Data
    public static class Gateway {
        private Cors cors = new Cors();
        private RateLimit rateLimit = new RateLimit();
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

    @Data
    public static class RunHistoryConfig {
        private int maxRunsPerUser = 10;
        private int maxCodeLength = 10_000;
        private int maxOutputLength = 5_000;
        private int maxErrorLength = 2_000;

        public int getMaxRunsPerUser() {
            return maxRunsPerUser > 0 ? maxRunsPerUser : 10;
        }

        public int getMaxCodeLength() {
            return maxCodeLength > 0 ? maxCodeLength : 10_000;
        }

        public int getMaxOutputLength() {
            return maxOutputLength > 0 ? maxOutputLength : 5_000;
        }

        public int getMaxErrorLength() {
            return maxErrorLength > 0 ? maxErrorLength : 2_000;
        }
    }

    @Data
    public static class ExecutionConfig {
        private String dockerHost = "unix:///var/run/docker.sock";
        private Long timeoutSeconds = 30L;
        private Long memoryLimitMB = 256L;
        private Double cpuLimit = 0.5;
        private String workspaceHostPath;

        public long getTimeoutSeconds() {
            return timeoutSeconds != null ? timeoutSeconds : 30L;
        }

        public long getMemoryLimitMB() {
            return memoryLimitMB != null ? memoryLimitMB : 256L;
        }

        public double getCpuLimit() {
            return cpuLimit != null ? cpuLimit : 0.5;
        }

        public String getWorkspaceHostPath() {
            return workspaceHostPath;
        }
    }
}
