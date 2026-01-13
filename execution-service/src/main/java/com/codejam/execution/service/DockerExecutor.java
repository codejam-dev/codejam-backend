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
import com.github.dockerjava.api.command.InspectContainerResponse;
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
            log.info("Starting container {} for room {}, language: {}", containerId, submission.getRoomId(), submission.getLanguage());
            
            try {
                // Start container with stdin disabled to prevent hanging
                dockerClient.startContainerCmd(containerId)
                        .exec();
                log.info("Container {} started successfully", containerId);
            } catch (Exception e) {
                log.error("Failed to start container {} for room {}", containerId, submission.getRoomId(), e);
                throw new CustomException("CONTAINER_START_FAILED", "Failed to start container: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
            }

            // Wait for container to complete with timeout
            Integer exitCode;
            try {
                exitCode = dockerClient
                        .waitContainerCmd(containerId)
                        .exec(new WaitContainerResultCallback())
                        .awaitStatusCode(microserviceConfig.getExecutor().getTimeoutSeconds(), TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("Error waiting for container {} to complete, checking status directly: {}", containerId, e.getMessage());
                // If wait times out, try to get container status directly
                try {
                    InspectContainerResponse containerInfo = dockerClient.inspectContainerCmd(containerId).exec();
                    InspectContainerResponse.ContainerState state = containerInfo.getState();
                    if (state != null && state.getRunning() != null && !state.getRunning()) {
                        // Use getExitCodeLong() instead of deprecated getExitCode()
                        Long exitCodeLong = state.getExitCodeLong();
                        exitCode = exitCodeLong != null ? exitCodeLong.intValue() : null;
                        log.info("Container {} exited with code: {}", containerId, exitCode);
                    } else {
                        // Container still running, force stop it
                        log.warn("Container {} still running after timeout, forcing stop", containerId);
                        try {
                            dockerClient.stopContainerCmd(containerId).withTimeout(1).exec();
                        } catch (Exception stopError) {
                            log.error("Failed to stop container {}: {}", containerId, stopError.getMessage());
                        }
                        exitCode = 124; // Exit code for timeout
                    }
                } catch (CustomException ce) {
                    throw ce; // Re-throw timeout exceptions
                } catch (Exception inspectError) {
                    log.error("Failed to inspect container {}: {}", containerId, inspectError.getMessage());
                    // Force stop and return timeout
                    try {
                        dockerClient.stopContainerCmd(containerId).withTimeout(1).exec();
                    } catch (Exception stopError) {
                        log.error("Failed to stop container {}: {}", containerId, stopError.getMessage());
                    }
                    throw new CustomException("EXECUTION_TIMEOUT", "Execution timed out after " + microserviceConfig.getExecutor().getTimeoutSeconds() + "s", HttpStatus.REQUEST_TIMEOUT);
                }
            }

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
                .withCmd("sh", "-c", command)
                .withHostConfig(hostConfig)
                .withAttachStdin(false)  // Disable stdin to prevent hanging
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withTty(false)  // Disable TTY to prevent interactive mode
                .withStdinOpen(false)  // Explicitly close stdin
                .exec();

        return container.getId();
    }

    private String buildCommand(CodeSubmission.Language language, String fileName) {
        String fileNameWithoutExt = fileName.substring(0, fileName.lastIndexOf('.'));
        String binaryPath = "/tmp/" + fileNameWithoutExt;

        return switch (language) {
            case JAVASCRIPT -> "cd /workspace && node " + fileName + " </dev/null";
            case PYTHON -> "cd /workspace && python " + fileName;
            case JAVA -> "cd /workspace && javac -d /tmp " + fileName + " 2>&1 && cd /tmp && java " + fileNameWithoutExt + " 2>&1 </dev/null || exit $?";
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
