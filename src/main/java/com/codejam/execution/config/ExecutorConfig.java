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
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class ExecutorConfig {

    private final MicroserviceConfig microserviceConfig;

    @Bean
    @ConditionalOnProperty(name = "app.executor.type", havingValue = "docker", matchIfMissing = true)
    public DockerClient dockerClient() {
        log.info("Creating DockerClient bean...");
        String dockerHostStr = microserviceConfig.getExecutor().getDockerHost();
        log.info("Raw docker host from config: '{}'", dockerHostStr);

        if (dockerHostStr == null || dockerHostStr.trim().isEmpty()) {
            log.warn("Docker host is not configured, using default: unix:///var/run/docker.sock");
            dockerHostStr = "unix:///var/run/docker.sock";
        }

        log.info("Using docker host: '{}'", dockerHostStr);

        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHostStr)
                .build();

        log.info("DockerClientConfig created with host: {}", config.getDockerHost());

        URI dockerHostUri = config.getDockerHost();
        log.info("Creating OkDockerHttpClient with URI: {}", dockerHostUri);

        OkDockerHttpClient httpClient = new OkDockerHttpClient.Builder()
                .dockerHost(dockerHostUri)
                .connectTimeout(60)
                .readTimeout(300)
                .build();

        DockerClient client = DockerClientImpl.getInstance(config, httpClient);

        log.info("Docker client initialized successfully with host: {}", config.getDockerHost());
        log.info("Using okhttp transport for Unix socket support");

        return client;
    }

    @Bean(name = "codeExecutorService", destroyMethod = "shutdown")
    public ExecutorService codeExecutorService() {
        log.info("Creating code executor thread pool");
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("code-exec-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("Code executor thread pool created: core={}, max={}, queue={}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        return executor.getThreadPoolExecutor();
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "app.executor.type", havingValue = "docker", matchIfMissing = true)
    public CodeExecutor dockerExecutor(DockerClient dockerClient, ExecutorService codeExecutorService) {
        return new DockerExecutor(dockerClient, microserviceConfig, codeExecutorService);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "app.executor.type", havingValue = "judge0")
    public CodeExecutor judge0Executor(org.springframework.web.client.RestTemplate judge0RestTemplate) {
        return new Judge0Executor(microserviceConfig, judge0RestTemplate);
    }
}