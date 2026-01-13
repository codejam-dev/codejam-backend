package com.codejam.execution.config;

import com.codejam.execution.service.CodeExecutor;
import com.codejam.execution.service.DockerExecutor;
//import com.codejam.execution.service.OracleVMExecutor;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class ExecutorConfig {

    private final MicroserviceConfig microserviceConfig;

    @Bean
    @ConditionalOnProperty(name = "app.executor.type", havingValue = "docker", matchIfMissing = true)
    public DockerClient dockerClient() {
        String dockerHost = microserviceConfig.getExecutor().getDockerHost();

        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                .build();

        ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();

        return DockerClientImpl.getInstance(config, httpClient);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "app.executor.type", havingValue = "docker", matchIfMissing = true)
    public CodeExecutor dockerExecutor(DockerClient dockerClient) {
        return new DockerExecutor(dockerClient, microserviceConfig);
    }

//    @Bean
//    @ConditionalOnProperty(name = "app.executor.type", havingValue = "oracle-vm")
//    public CodeExecutor oracleVMExecutor() {
//        return new OracleVMExecutor();
//    }
}