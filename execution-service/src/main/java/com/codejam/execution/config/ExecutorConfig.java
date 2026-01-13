package com.codejam.execution.config;

import com.codejam.execution.service.CodeExecutor;
import com.codejam.execution.service.DockerExecutor;
import com.codejam.execution.service.Judge0Executor;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.okhttp.OkDockerHttpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.net.URI;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class ExecutorConfig {

    private final MicroserviceConfig microserviceConfig;

    @Bean
    @ConditionalOnProperty(name = "app.executor.type", havingValue = "docker", matchIfMissing = true)
    public DockerClient dockerClient() {
        log.info("Creating DockerClient bean...");

        // Read docker host from config
        String dockerHostStr = microserviceConfig.getExecutor().getDockerHost();
        log.info("Raw docker host from config: '{}'", dockerHostStr);

        // Validate and normalize docker host
        if (dockerHostStr == null || dockerHostStr.trim().isEmpty()) {
            log.warn("Docker host is not configured, using default: unix:///var/run/docker.sock");
            dockerHostStr = "unix:///var/run/docker.sock";
        }

        log.info("Using docker host: '{}'", dockerHostStr);

        // Create config
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHostStr)
                .build();

        log.info("DockerClientConfig created with host: {}", config.getDockerHost());

        // Explicitly use okhttp transport for Unix socket support
        URI dockerHostUri = config.getDockerHost();
        log.info("Creating OkDockerHttpClient with URI: {}", dockerHostUri);
        
        // Increase timeouts for container operations which can take longer
        // Note: Starting a container should be fast, but we set high timeouts to handle slow Docker daemons
        OkDockerHttpClient httpClient = new OkDockerHttpClient.Builder()
                .dockerHost(dockerHostUri)
                .connectTimeout(60)   // seconds - for initial connection
                .readTimeout(300)     // seconds - 5 minutes for all operations (container start should be instant, but handle slow daemons)
                .build();

        // Create client with explicit okhttp transport
        DockerClient client = DockerClientImpl.getInstance(config, httpClient);

        log.info("Docker client initialized successfully with host: {}", config.getDockerHost());
        log.info("Using okhttp transport for Unix socket support");

        return client;
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "app.executor.type", havingValue = "docker", matchIfMissing = true)
    public CodeExecutor dockerExecutor(DockerClient dockerClient) {
        return new DockerExecutor(dockerClient, microserviceConfig);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "app.executor.type", havingValue = "judge0")
    public CodeExecutor judge0Executor(org.springframework.web.client.RestTemplate judge0RestTemplate) {
        return new Judge0Executor(microserviceConfig, judge0RestTemplate);
    }
}
