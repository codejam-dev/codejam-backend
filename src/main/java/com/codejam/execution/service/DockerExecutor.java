package com.codejam.execution.service;

import com.codejam.commons.exception.CustomException;
import com.codejam.execution.config.MicroserviceConfig;
import com.codejam.execution.dto.CodeSubmission;
import com.codejam.execution.dto.ExecutionResult;
import com.codejam.execution.dto.ExecutionStatus;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.NotModifiedException;
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
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
public class DockerExecutor implements CodeExecutor {

    private static final int MAX_RETRIES = 3;
    private static final long BASE_RETRY_DELAY_MS = 500;
    private static final int LOG_TIMEOUT_SECONDS = 10;
    private static final int MAX_CODE_SIZE = 100_000;
    private static final int MAX_OUTPUT_SIZE = 1024 * 1024;
    private static final Pattern JAVA_CLASS_PATTERN = Pattern.compile("public\\s+class\\s+(\\w+)");
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{0,50}$");

    private final DockerClient dockerClient;
    private final MicroserviceConfig microserviceConfig;
    private final ExecutorService executorService;

    private record ContainerContext(String containerId, Path sourceFile) {}
    private record ExecutionOutput(String stdout, String stderr) {}

    @Override
    public ExecutionResult execute(CodeSubmission submission) {
        validateSubmission(submission);
        Future<ExecutionResult> future = executorService.submit(() -> executeInContainer(submission));
        try {
            long timeout = microserviceConfig.getExecutor().getTimeoutSeconds() + 5;
            return future.get(timeout, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("Execution timeout for room {}", submission.getRoomId());
            throw new CustomException("EXECUTION_TIMEOUT", "Code execution exceeded time limit", HttpStatus.REQUEST_TIMEOUT);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof CustomException ce) throw ce;
            log.error("Execution failed for room {}", submission.getRoomId(), cause);
            throw new CustomException("EXECUTION_FAILED", "Code execution failed", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CustomException("EXECUTION_INTERRUPTED", "Execution interrupted", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private ExecutionResult executeInContainer(CodeSubmission submission) {
        long startTime = System.currentTimeMillis();
        ContainerContext ctx = null;
        try {
            ctx = prepareContainer(submission);
            startContainer(ctx.containerId());
            int exitCode = awaitCompletion(ctx.containerId(), startTime);
            ExecutionOutput output = captureOutput(ctx.containerId());
            return buildResult(submission.getRoomId(), exitCode, output, System.currentTimeMillis() - startTime);
        } finally {
            cleanup(ctx);
        }
    }

    private void validateSubmission(CodeSubmission submission) {
        if (submission.getCode() == null || submission.getCode().trim().isEmpty()) {
            throw new CustomException("INVALID_CODE", "Code cannot be empty", HttpStatus.BAD_REQUEST);
        }
        if (submission.getCode().length() > MAX_CODE_SIZE) {
            throw new CustomException("CODE_TOO_LARGE", "Code exceeds 100KB limit", HttpStatus.BAD_REQUEST);
        }
    }

    private ContainerContext prepareContainer(CodeSubmission submission) {
        try {
            Path sourceFile = createSourceFile(submission);
            String containerId = createContainer(submission, sourceFile);
            return new ContainerContext(containerId, sourceFile);
        } catch (IOException e) {
            log.error("Failed to create source file for room {}", submission.getRoomId(), e);
            throw new CustomException("IO_ERROR", "Failed to prepare execution environment", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private Path createSourceFile(CodeSubmission submission) throws IOException {
        String ext = submission.getLanguage().getExtension();
        if (submission.getLanguage() == CodeSubmission.Language.JAVA) {
            String className = extractJavaClassName(submission.getCode());
            Path dir = Files.createTempDirectory("codejam-");
            Path file = dir.resolve(className + ext);
            Files.writeString(file, submission.getCode());
            return file;
        }
        Path file = Files.createTempFile("codejam-", ext);
        Files.writeString(file, submission.getCode());
        return file;
    }

    private String extractJavaClassName(String code) {
        Matcher matcher = JAVA_CLASS_PATTERN.matcher(code);
        if (!matcher.find()) return "Main";
        String className = matcher.group(1);
        if (!SAFE_IDENTIFIER.matcher(className).matches()) {
            log.warn("Unsafe class name detected, using Main as fallback");
            return "Main";
        }
        return className;
    }

    private String createContainer(CodeSubmission submission, Path sourceFile) {
        String image = submission.getLanguage().getDockerImage();
        String command = buildCommand(submission.getLanguage(), sourceFile.getFileName().toString());
        HostConfig hostConfig = buildHostConfig(sourceFile.getParent());
        return retryOnTimeout("create", () -> {
            CreateContainerResponse container = dockerClient.createContainerCmd(image)
                    .withCmd("sh", "-c", command)
                    .withHostConfig(hostConfig)
                    .withAttachStdin(false)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withTty(false)
                    .exec();
            return container.getId();
        });
    }

    private HostConfig buildHostConfig(Path workDir) {
        long memoryBytes = microserviceConfig.getExecutor().getMemoryLimitMB() * 1024 * 1024;
        double cpuLimit = Math.min(microserviceConfig.getExecutor().getCpuLimit(), 1.0);
        return HostConfig.newHostConfig()
                .withMemory(memoryBytes)
                .withCpuQuota((long) (cpuLimit * 100000))
                .withCpuPeriod(100000L)
                .withPidsLimit(50L)
                .withNetworkMode("none")
                .withSecurityOpts(List.of("no-new-privileges"))
                .withTmpFs(Map.of("/tmp", "rw,exec,nosuid,size=100m"))
                .withBinds(new Bind(workDir.toString(), new Volume("/workspace"), AccessMode.ro));
    }

    private void startContainer(String containerId) {

        retryOnTimeout("start", () -> {
            try {
                dockerClient.startContainerCmd(containerId).exec();
            } catch (NotModifiedException e) {
                log.debug("Container {} already started (304)", containerId);
            }
            return null;
        });
    }

    private int awaitCompletion(String containerId, long startTime) {
        long timeoutSeconds = microserviceConfig.getExecutor().getTimeoutSeconds();
        try {
            Integer exitCode = dockerClient.waitContainerCmd(containerId)
                    .exec(new com.github.dockerjava.api.command.WaitContainerResultCallback())
                    .awaitStatusCode(timeoutSeconds, TimeUnit.SECONDS);
            if (exitCode != null) return exitCode;
        } catch (Exception e) {
            log.warn("Wait failed for container {}, falling back to polling", containerId);
        }
        return pollCompletion(containerId, startTime, timeoutSeconds);
    }

    private int pollCompletion(String containerId, long startTime, long timeoutSeconds) {
        long deadline = startTime + (timeoutSeconds * 1000);
        long delay = 200;
        while (System.currentTimeMillis() < deadline) {
            try {
                InspectContainerResponse.ContainerState state = dockerClient.inspectContainerCmd(containerId).exec().getState();
                if (state != null && !Boolean.TRUE.equals(state.getRunning())) {
                    return state.getExitCodeLong() != null ? state.getExitCodeLong().intValue() : 1;
                }
            } catch (Exception e) {
                log.debug("Inspect failed: {}", e.getMessage());
            }
            sleep(delay);
            delay = Math.min(delay * 2, 2000);
        }
        forceStop(containerId);
        throw new CustomException("EXECUTION_TIMEOUT", "Execution timed out", HttpStatus.REQUEST_TIMEOUT);
    }

    private String buildCommand(CodeSubmission.Language lang, String fileName) {
        String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
        String binary = "/tmp/" + baseName;
        return switch (lang) {
            case JAVASCRIPT -> "cd /workspace && timeout 30s node " + fileName + " </dev/null";
            case PYTHON -> "cd /workspace && timeout 30s python " + fileName + " </dev/null";
            case JAVA -> String.format("cd /workspace && javac -d /tmp %s && cd /tmp && timeout 30s java -Djava.awt.headless=true -XX:+UseSerialGC %s </dev/null", fileName, baseName);
//            case CPP -> String.format("cd /workspace && g++ -O2 -o %s %s && timeout 30s %s </dev/null", binary, fileName, binary);
//            case C -> String.format("cd /workspace && gcc -O2 -o %s %s && timeout 30s %s </dev/null", binary, fileName, binary);
//            case GO -> "cd /workspace && GOTMPDIR=/tmp timeout 30s go run " + fileName + " </dev/null";
//            case RUST -> String.format("cd /workspace && rustc -O %s -o %s && timeout 30s %s </dev/null", fileName, binary, binary);
        };
    }

    private ExecutionOutput captureOutput(String containerId) {
        try (var stdout = new ByteArrayOutputStream(); var stderr = new ByteArrayOutputStream()) {
            CompletableFuture<Void> logCapture = new CompletableFuture<>();

            dockerClient.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withFollowStream(false)  // Don't follow, just get all logs
                    .withTimestamps(false)
                    .withSince(0)  // Get all logs from start
                    .exec(new ResultCallback.Adapter<Frame>() {
                        @Override
                        public void onNext(Frame frame) {
                            try {
                                byte[] payload = frame.getPayload();
                                if (payload != null && payload.length > 0) {
                                    var stream = frame.getStreamType() == StreamType.STDOUT ? stdout : stderr;
                                    if (stream.size() + payload.length <= MAX_OUTPUT_SIZE) {
                                        stream.write(payload);
                                    }
                                }
                            } catch (IOException e) {
                                log.error("Error capturing output", e);
                            }
                        }

                        @Override
                        public void onComplete() {
                            super.onComplete();
                            logCapture.complete(null);
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            super.onError(throwable);
                            log.error("Error in log stream for container {}", containerId, throwable);
                            logCapture.completeExceptionally(throwable);
                        }
                    });

            try {
                logCapture.get(LOG_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                log.warn("Log capture timed out for container {}", containerId);
            } catch (Exception e) {
                log.error("Failed waiting for logs", e);
            }

            String stdoutStr = stdout.toString();
            String stderrStr = stderr.toString();

            log.debug("Captured {} bytes stdout, {} bytes stderr for container {}",
                    stdoutStr.length(), stderrStr.length(), containerId);

            return new ExecutionOutput(truncate(stdoutStr), truncate(stderrStr));
        } catch (Exception e) {
            log.error("Failed to capture output for container {}", containerId, e);
            return new ExecutionOutput("", "");
        }
    }

    private String truncate(String output) {
        if (output.length() <= MAX_OUTPUT_SIZE) return output;
        return output.substring(0, MAX_OUTPUT_SIZE) + "\n... (output truncated)";
    }

    private ExecutionResult buildResult(String roomId, int exitCode, ExecutionOutput output, long execTime) {
        return ExecutionResult.builder()
                .roomId(roomId)
                .status(exitCode == 0 ? ExecutionStatus.SUCCESS : ExecutionStatus.ERROR)
                .stdout(output.stdout())
                .stderr(output.stderr())
                .exitCode(exitCode)
                .executionTimeMs(execTime)
                .build();
    }

    @FunctionalInterface
    private interface RetryableAction<T> { T execute() throws Exception; }

    private <T> T retryOnTimeout(String operation, RetryableAction<T> action) {
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return action.execute();
            } catch (Exception e) {
                lastException = e;
                if (!isSocketTimeout(e) || attempt == MAX_RETRIES) break;
                long delay = BASE_RETRY_DELAY_MS * (1L << (attempt - 1));
                log.warn("Socket timeout on {} (attempt {}/{}), retrying in {}ms", operation, attempt, MAX_RETRIES, delay);
                sleep(delay);
            }
        }
        log.error("Operation {} failed after {} retries", operation, MAX_RETRIES, lastException);
        throw new CustomException("CONTAINER_" + operation.toUpperCase() + "_FAILED",
                "Container operation failed", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private boolean isSocketTimeout(Exception e) {
        return e instanceof java.net.SocketTimeoutException
               || (e.getCause() instanceof java.net.SocketTimeoutException)
               || (e.getMessage() != null && e.getMessage().contains("timeout"));
    }

    private boolean isContainerStarted(String containerId) {
        try {
            InspectContainerResponse.ContainerState state = dockerClient.inspectContainerCmd(containerId).exec().getState();
            return state != null && (Boolean.TRUE.equals(state.getRunning()) || state.getExitCodeLong() != null);
        } catch (Exception e) {
            return false;
        }
    }

    private void forceStop(String containerId) {
        try {
            dockerClient.stopContainerCmd(containerId).withTimeout(1).exec();
        } catch (Exception e) {
            log.warn("Failed to stop container {}", containerId);
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CustomException("EXECUTION_INTERRUPTED", "Execution interrupted", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void cleanup(ContainerContext ctx) {
        if (ctx == null) return;
        try {
            dockerClient.removeContainerCmd(ctx.containerId()).withForce(true).exec();
        } catch (Exception e) {
            log.warn("Failed to remove container {}", ctx.containerId());
        }
        try {
            Files.deleteIfExists(ctx.sourceFile());
            Path parent = ctx.sourceFile().getParent();
            if (parent != null && parent.getFileName().toString().startsWith("codejam-")) {
                Files.deleteIfExists(parent);
            }
        } catch (IOException e) {
            log.warn("Failed to delete temp files", e);
        }
    }
}