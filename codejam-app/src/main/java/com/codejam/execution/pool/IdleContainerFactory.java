package com.codejam.execution.pool;

import com.codejam.auth.config.MicroserviceConfig;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.HostConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Creates long-lived idle containers (sleep loop) with tmpfs /workspace and /tmp — no host bind mounts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdleContainerFactory {

    private static final String IDLE_COMMAND = "sh -c 'while true; do sleep 86400; done'";

    private final DockerClient dockerClient;
    private final MicroserviceConfig microserviceConfig;

    public HostConfig buildIdlePoolHostConfig() {
        long memoryBytes = microserviceConfig.getExecutor().getMemoryLimitMB() * 1024 * 1024;
        double cpuLimit = Math.min(microserviceConfig.getExecutor().getCpuLimit(), 1.0);
        return HostConfig.newHostConfig()
                .withMemory(memoryBytes)
                .withCpuQuota((long) (cpuLimit * 100000))
                .withCpuPeriod(100000L)
                .withPidsLimit(50L)
                .withNetworkMode("none")
                .withSecurityOpts(List.of("no-new-privileges"))
                .withTmpFs(Map.of(
                        "/tmp", "rw,exec,nosuid,size=100m",
                        "/workspace", "rw,exec,nosuid,size=50m"));
    }

    /**
     * Create and start an idle container for the given image; returns container id.
     */
    public String createAndStartIdle(String image) {
        HostConfig hostConfig = buildIdlePoolHostConfig();
        CreateContainerResponse created = dockerClient.createContainerCmd(image)
                .withHostConfig(hostConfig)
                .withCmd("sh", "-c", IDLE_COMMAND)
                .withAttachStdin(false)
                .withAttachStdout(false)
                .withAttachStderr(false)
                .withTty(false)
                .exec();
        String id = created.getId();
        try {
            dockerClient.startContainerCmd(id).exec();
        } catch (NotModifiedException e) {
            log.debug("Container {} already started", id);
        }
        return id;
    }

    public void removeContainer(String containerId) {
        if (containerId == null) {
            return;
        }
        try {
            dockerClient.removeContainerCmd(containerId).withForce(true).exec();
        } catch (Exception e) {
            log.warn("Failed to remove pooled container {}", containerId, e);
        }
    }
}
