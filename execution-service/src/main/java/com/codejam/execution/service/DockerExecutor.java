package com.codejam.execution.service;

import com.codejam.commons.exception.CustomException;
import com.codejam.execution.config.MicroserviceConfig;
import com.codejam.execution.dto.CodeSubmission;
import com.codejam.execution.dto.ExecutionResult;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
public class DockerExecutor implements CodeExecutor {

    private final DockerClient dockerClient;
    private final MicroserviceConfig microserviceConfig;

    @Override
    public ExecutionResult execute(CodeSubmission submission) {
        long startTime = System.currentTimeMillis();
        String containerId = null;
        Path tempFile = null;

        try {
            tempFile = createTempFile(submission);

            containerId = createContainer(submission, tempFile);
            dockerClient.startContainerCmd(containerId).exec();

            Integer exitCode = dockerClient
                    .waitContainerCmd(containerId)
                    .exec(new WaitContainerResultCallback())
                    .awaitStatusCode(microserviceConfig.getExecutor().getTimeoutSeconds(), TimeUnit.SECONDS);

            if (exitCode == null) {
                dockerClient.stopContainerCmd(containerId).withTimeout(1).exec();
                throw new CustomException("EXECUTION_TIMEOUT", "Execution timed out after " + microserviceConfig.getExecutor().getTimeoutSeconds() + "s", HttpStatus.REQUEST_TIMEOUT);
            }

            String output = captureOutput(containerId);
            long executionTime = System.currentTimeMillis() - startTime;

            return exitCode == 0 ?
                    ExecutionResult.success(submission.getRoomId(), output, executionTime) :
                    ExecutionResult.error(submission.getRoomId(), output, executionTime);


        } catch (IOException e) {
            log.error("IO error during execution for room {}", submission.getRoomId(), e);
            throw new CustomException("IO_ERROR", "Failed to create temporary file: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);

        } catch (Exception e) {
            log.error("Unexpected error during execution for room {}", submission.getRoomId(), e);
            throw new CustomException("EXECUTION_FAILED", "Code execution failed: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        } finally {
            cleanup(containerId, tempFile);
        }
    }


    private Path createTempFile(CodeSubmission submission) throws IOException {
        String extension = submission.getLanguage().getExtension();
        Path tempFile = Files.createTempFile("code-" + UUID.randomUUID(), extension);
        Files.writeString(tempFile, submission.getCode());
        return tempFile;
    }

    private String createContainer(CodeSubmission submission, Path tempFile) {
        String image = submission.getLanguage().getDockerImage();
        String command = buildCommand(submission.getLanguage(), tempFile.getFileName().toString());
        long memoryLimitBytes = microserviceConfig.getExecutor().getMemoryLimitMB() * 1024 * 1024;
        double cpuLimit = Math.min(microserviceConfig.getExecutor().getCpuLimit(), 1.0);

        HostConfig hostConfig = HostConfig.newHostConfig()
                .withMemory(memoryLimitBytes)
                .withCpuQuota((long) (cpuLimit * 100000))
                .withCpuPeriod(100000L)
                .withPidsLimit(50L)
                .withNetworkMode("none")
                .withReadonlyRootfs(true)
                .withCapDrop(Capability.ALL)
                .withSecurityOpts(List.of("no-new-privileges"))
                .withTmpFs(Map.of("/tmp", "rw,exec,nosuid,size=100m"))
                .withBinds(
                        new Bind(tempFile.getParent().toString(),
                                new Volume("/workspace"), AccessMode.ro)
                );

        CreateContainerResponse container = dockerClient.createContainerCmd(image)
                .withUser("65534:65534")
                .withCmd("/bin/sh", "-c", command)
                .withHostConfig(hostConfig)
                .exec();

        return container.getId();
    }

    private String buildCommand(CodeSubmission.Language language, String fileName) {
        String fileNameWithoutExt = fileName.substring(0, fileName.lastIndexOf('.'));
        String binaryPath = "/tmp/" + fileNameWithoutExt;

        return switch (language) {
            case JAVASCRIPT -> "cd /workspace && node " + fileName;
            case PYTHON -> "cd /workspace && python " + fileName;
            case JAVA -> "cd /workspace && javac -d /tmp " + fileName + " && cd /tmp && java " + fileNameWithoutExt;
            case CPP -> "cd /workspace && g++ -o " + binaryPath + " " + fileName + " && " + binaryPath;
            case C -> "cd /workspace && gcc -o " + binaryPath + " " + fileName + " && " + binaryPath;
            case GO -> "cd /workspace && GOTMPDIR=/tmp go run " + fileName;
            case RUST -> "cd /workspace && rustc " + fileName + " -o " + binaryPath + " && " + binaryPath;
        };
    }


    private String captureOutput(String containerId) {
        try (ByteArrayOutputStream stdout = new ByteArrayOutputStream(); ByteArrayOutputStream stderr = new ByteArrayOutputStream()) {

            dockerClient.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .exec(new ResultCallback.Adapter< >() {
                @Override
                public void onNext(Frame frame) {
                    try {
                        byte[] payload = frame.getPayload();
                        if (frame.getStreamType() == StreamType.STDOUT) {
                            stdout.write(payload);
                        } else {
                            stderr.write(payload);
                        }
                    } catch (IOException e) {
                        log.error("Failed to capture output", e);
                    }
                }
            }).awaitCompletion();

            String output = stdout.toString();
            String error = stderr.toString();

            return error.isEmpty() ? output : error;

        } catch (Exception e) {
            log.error("Failed to capture container output", e);
            return "Failed to capture output: " + e.getMessage();
        }
    }

    private void cleanup(String containerId, Path tempFile) {
        if (containerId != null) {
            try {
                dockerClient.removeContainerCmd(containerId).withForce(true).exec();
            } catch (Exception e) {
                log.warn("Failed to remove container {}", containerId, e);
            }
        }

        if (tempFile != null) {
            try {
                Files.deleteIfExists(tempFile);

                String fileNameWithoutExt = tempFile.getFileName().toString().replaceFirst("[.][^.]+$", "");
                Path binary = tempFile.getParent().resolve(fileNameWithoutExt);
                Files.deleteIfExists(binary);

            } catch (IOException e) {
                log.warn("Failed to delete temp files", e);
            }
        }
    }

}
