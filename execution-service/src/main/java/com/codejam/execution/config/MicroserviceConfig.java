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
    private RunHistoryConfig runHistory = new RunHistoryConfig();

    @PostConstruct
    public void init() {
        if (executor == null) {
            executor = new ExecutionConfig();
        }
        if (runHistory == null) {
            runHistory = new RunHistoryConfig();
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