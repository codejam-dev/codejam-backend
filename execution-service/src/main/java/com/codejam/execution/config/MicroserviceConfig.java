package com.codejam.execution.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

@Configuration
@RefreshScope
@ConfigurationProperties(prefix = "app")
@Data
public class MicroserviceConfig {

    private ExecutionConfig executor = new ExecutionConfig();

    @PostConstruct
    public void init() {
        // Ensure executor is never null
        if (executor == null) {
            executor = new ExecutionConfig();
        }
    }

    /**
     * Get executor config, ensuring it's never null
     */
    public ExecutionConfig getExecutor() {
        if (executor == null) {
            executor = new ExecutionConfig();
        }
        return executor;
    }

    private Judge0Config judge0 = new Judge0Config();

    /**
     * Get Judge0 config, ensuring it's never null
     */
    public Judge0Config getJudge0() {
        if (judge0 == null) {
            judge0 = new Judge0Config();
        }
        return judge0;
    }

    @Data
    public static class ExecutionConfig {
        private String type = "docker";
        private String dockerHost = "unix:///var/run/docker.sock";
        private Long timeoutSeconds = 30L;
        private Long memoryLimitMB = 256L;
        private Double cpuLimit = 0.5;
        
        // Getters for compatibility (Lombok @Data generates these, but explicit for clarity)
        public long getTimeoutSeconds() {
            return timeoutSeconds != null ? timeoutSeconds : 30L;
        }
        
        public long getMemoryLimitMB() {
            return memoryLimitMB != null ? memoryLimitMB : 256L;
        }
        
        public double getCpuLimit() {
            return cpuLimit != null ? cpuLimit : 0.5;
        }
    }

    @Data
    @ConfigurationProperties(prefix = "app.judge0.rapidapi")
    public static class Judge0Config {
        private String endpoint = "https://judge0-ce.p.rapidapi.com";
        private String apiKey;
        private String apiHost = "judge0-ce.p.rapidapi.com";
        private Integer timeoutSeconds = 25;
        private Integer pollIntervalMs = 1000;
        private Integer maxPollAttempts = 25;
        private Long memoryLimitMB = 256L;
    }
}