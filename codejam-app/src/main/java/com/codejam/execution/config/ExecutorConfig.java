package com.codejam.execution.config;

import com.codejam.auth.config.MicroserviceConfig;
import com.codejam.execution.service.CodeExecutor;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.okhttp.OkDockerHttpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
    public DockerClient dockerClient() {
        String dockerHostStr = microserviceConfig.getExecutor().getDockerHost();

        if (dockerHostStr == null || dockerHostStr.trim().isEmpty()) {
            dockerHostStr = "unix:///var/run/docker.sock";
        }

        log.info("Using docker host: '{}'", dockerHostStr);

        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHostStr)
                .build();

        URI dockerHostUri = config.getDockerHost();

        OkDockerHttpClient httpClient = new OkDockerHttpClient.Builder()
                .dockerHost(dockerHostUri)
                .connectTimeout(60)
                .readTimeout(300)
                .build();

        DockerClient client = DockerClientImpl.getInstance(config, httpClient);
        log.info("Docker client initialized with host: {}", config.getDockerHost());

        return client;
    }

    @Bean(name = "codeExecutorService", destroyMethod = "shutdown")
    public ExecutorService codeExecutorService() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("code-exec-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor.getThreadPoolExecutor();
    }

}
