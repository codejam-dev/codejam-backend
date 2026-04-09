package com.codejam.execution.service;

import com.codejam.auth.config.MicroserviceConfig;
import com.codejam.commons.exception.CustomException;
import com.codejam.execution.dto.CodeSubmission;
import com.codejam.execution.dto.ExecutionResult;
import com.codejam.execution.dto.ExecutionStatus;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared validation, workspace file layout, shell commands, and result mapping for both
 * ephemeral one-shot containers and pooled exec-based runs.
 */
@Component
@RequiredArgsConstructor
public class ExecutionWorkspacePreparer {

    public static final int MAX_CODE_SIZE = 100_000;
    public static final int MAX_OUTPUT_SIZE = 1024 * 1024;

    private static final Pattern JAVA_CLASS_PATTERN = Pattern.compile("public\\s+class\\s+(\\w+)");
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{0,50}$");

    private final MicroserviceConfig microserviceConfig;

    public void validateSubmission(CodeSubmission submission) {
        if (submission.getCode() == null || submission.getCode().trim().isEmpty()) {
            throw new CustomException("INVALID_CODE", "Code cannot be empty", HttpStatus.BAD_REQUEST);
        }
        if (submission.getCode().length() > MAX_CODE_SIZE) {
            throw new CustomException("CODE_TOO_LARGE", "Code exceeds 100KB limit", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Creates a temp directory under the configured workspace with source file(s); returns path to the main source file.
     */
    public Path createSourceFile(CodeSubmission submission) throws IOException {
        String ext = submission.getLanguage().getExtension();
        Path baseDir = getWorkspaceDir();

        if (submission.getLanguage() == CodeSubmission.Language.JAVA) {
            String className = extractJavaClassName(submission.getCode());
            Path dir = Files.createTempDirectory(baseDir, "codejam-");
            Path file = dir.resolve(className + ext);
            Files.writeString(file, submission.getCode());
            return file;
        }
        Path dir = Files.createTempDirectory(baseDir, "codejam-");
        Path file = dir.resolve("code" + ext);
        Files.writeString(file, submission.getCode());
        return file;
    }

    public Path getWorkspaceDir() throws IOException {
        String hostPath = microserviceConfig.getExecutor().getWorkspaceHostPath();
        if (hostPath != null && !hostPath.isEmpty()) {
            Path dir = Path.of(hostPath);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            return dir;
        }
        return Path.of(System.getProperty("java.io.tmpdir"));
    }

    public String extractJavaClassName(String code) {
        Matcher matcher = JAVA_CLASS_PATTERN.matcher(code);
        if (!matcher.find()) {
            return "Main";
        }
        String className = matcher.group(1);
        if (!SAFE_IDENTIFIER.matcher(className).matches()) {
            return "Main";
        }
        return className;
    }

    public HostConfig buildEphemeralHostConfig(Path workDir) {
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

    public String buildCommand(CodeSubmission.Language lang, String fileName) {
        long timeoutSec = microserviceConfig.getExecutor().getTimeoutSeconds();
        String t = Math.min(Math.max(timeoutSec, 1), 300) + "s";
        String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
        return switch (lang) {
            case JAVASCRIPT -> "cd /workspace && timeout " + t + " node " + fileName + " </dev/null";
            case PYTHON -> "cd /workspace && timeout " + t + " python " + fileName + " </dev/null";
            case JAVA -> String.format(
                    "cd /workspace && javac -d /tmp %s && cd /tmp && timeout %s java -Djava.awt.headless=true -XX:+UseSerialGC %s </dev/null",
                    fileName, t, baseName);
        };
    }

    public String truncateOutput(String output) {
        if (output.length() <= MAX_OUTPUT_SIZE) {
            return output;
        }
        return output.substring(0, MAX_OUTPUT_SIZE) + "\n... (output truncated)";
    }

    public ExecutionResult buildResult(String roomId, int exitCode, ExecutionCapturedOutput output, long execTimeMs) {
        return ExecutionResult.builder()
                .roomId(roomId)
                .status(exitCode == 0 ? ExecutionStatus.SUCCESS : ExecutionStatus.ERROR)
                .stdout(output.stdout())
                .stderr(output.stderr())
                .exitCode(exitCode)
                .executionTimeMs(execTimeMs)
                .build();
    }

    public void deleteHostWorkspace(Path sourceFile) {
        try {
            Files.deleteIfExists(sourceFile);
            Path parent = sourceFile.getParent();
            if (parent != null && parent.getFileName().toString().startsWith("codejam-")) {
                Files.deleteIfExists(parent);
            }
        } catch (IOException e) {
            // best-effort cleanup
        }
    }
}
