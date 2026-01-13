package com.codejam.execution.service;

import com.codejam.execution.config.MicroserviceConfig;
import com.codejam.execution.dto.CodeSubmission;
import com.codejam.execution.dto.ExecutionResult;
import com.codejam.execution.dto.ExecutionStatus;
import com.codejam.execution.dto.Judge0SubmissionRequest;
import com.codejam.execution.dto.Judge0SubmissionResponse;
import com.codejam.execution.exception.ExecutionTimeoutException;
import com.codejam.execution.exception.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class Judge0Executor implements com.codejam.execution.service.CodeExecutor {

    private final MicroserviceConfig microserviceConfig;
    private final RestTemplate restTemplate;

    // Judge0 language ID mapping
    private static final Map<CodeSubmission.Language, Integer> LANGUAGE_ID_MAP = new HashMap<>();
    
    static {
        LANGUAGE_ID_MAP.put(CodeSubmission.Language.JAVASCRIPT, 63); // Node.js
        LANGUAGE_ID_MAP.put(CodeSubmission.Language.PYTHON, 71);      // Python 3
        LANGUAGE_ID_MAP.put(CodeSubmission.Language.JAVA, 62);        // Java
        LANGUAGE_ID_MAP.put(CodeSubmission.Language.CPP, 54);          // C++
        LANGUAGE_ID_MAP.put(CodeSubmission.Language.C, 50);            // C
        LANGUAGE_ID_MAP.put(CodeSubmission.Language.GO, 60);           // Go
        LANGUAGE_ID_MAP.put(CodeSubmission.Language.RUST, 73);          // Rust
    }

    // Judge0 status IDs
    private static final int STATUS_IN_QUEUE = 1;
    private static final int STATUS_PROCESSING = 2;
    private static final int STATUS_ACCEPTED = 3;
    private static final int STATUS_WRONG_ANSWER = 4;
    private static final int STATUS_TIME_LIMIT_EXCEEDED = 5;
    private static final int STATUS_COMPILATION_ERROR = 6;
    private static final int STATUS_RUNTIME_ERROR_SIGSEGV = 7;
    private static final int STATUS_RUNTIME_ERROR_SIGXFSZ = 8;
    private static final int STATUS_RUNTIME_ERROR_SIGFPE = 9;
    private static final int STATUS_RUNTIME_ERROR_SIGABRT = 10;
    private static final int STATUS_RUNTIME_ERROR_NZEC = 11;
    private static final int STATUS_RUNTIME_ERROR_OTHER = 12;
    private static final int STATUS_INTERNAL_ERROR = 13;

    @Override
    public ExecutionResult execute(CodeSubmission submission) {
        long startTime = System.currentTimeMillis();
        
        try {
            MicroserviceConfig.Judge0Config judge0Config = microserviceConfig.getJudge0();
            
            if (judge0Config == null || judge0Config.getApiKey() == null || judge0Config.getApiKey().isEmpty()) {
                log.error("Judge0 API key not configured");
                return ExecutionResult.systemError(submission.getRoomId(), "Judge0 API key not configured");
            }

            // Map language to Judge0 language ID
            Integer languageId = LANGUAGE_ID_MAP.get(submission.getLanguage());
            if (languageId == null) {
                log.error("Unsupported language: {}", submission.getLanguage());
                return ExecutionResult.systemError(submission.getRoomId(), "Unsupported language: " + submission.getLanguage());
            }

            // Create submission
            String token = createSubmission(submission, languageId, judge0Config);
            if (token == null) {
                return ExecutionResult.systemError(submission.getRoomId(), "Failed to create submission");
            }

            // Poll for result with exponential backoff
            Judge0SubmissionResponse result = pollSubmission(token, judge0Config);
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            // Map Judge0 result to ExecutionResult
            return mapToExecutionResult(submission.getRoomId(), result, executionTime);

        } catch (RateLimitExceededException e) {
            log.warn("Rate limit exceeded for room {}", submission.getRoomId());
            return ExecutionResult.systemError(submission.getRoomId(), e.getMessage());
        } catch (ExecutionTimeoutException e) {
            log.warn("Execution timeout for room {}", submission.getRoomId());
            long executionTime = System.currentTimeMillis() - startTime;
            return ExecutionResult.timeout(submission.getRoomId(), executionTime);
        } catch (RestClientException e) {
            log.error("REST client error executing code via Judge0 for room {}", submission.getRoomId(), e);
            return ExecutionResult.systemError(submission.getRoomId(), "Network error: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error executing code via Judge0 for room {}", submission.getRoomId(), e);
            return ExecutionResult.systemError(submission.getRoomId(), "Execution failed: " + e.getMessage());
        }
    }

    private String createSubmission(CodeSubmission submission, Integer languageId, MicroserviceConfig.Judge0Config config) {
        try {
            String url = config.getEndpoint() + "/submissions?base64_encoded=false&wait=false";
            
            Judge0SubmissionRequest request = Judge0SubmissionRequest.builder()
                    .languageId(languageId)
                    .sourceCode(submission.getCode())
                    .stdin("") // No input for now
                    .cpuTimeLimit((double) config.getTimeoutSeconds())
                    .memoryLimit((int) (config.getMemoryLimitMB() != null ? config.getMemoryLimitMB() * 1024 : 256 * 1024)) // Convert MB to KB
                    .wallTimeLimit((double) (config.getTimeoutSeconds() + 5)) // Add 5s buffer for wall time
                    .base64Encoded(false)
                    .build();

            HttpHeaders headers = createHeaders(config);
            HttpEntity<Judge0SubmissionRequest> httpEntity = new HttpEntity<>(request, headers);

            ResponseEntity<Judge0SubmissionResponse> response = restTemplate.postForEntity(
                url, httpEntity, Judge0SubmissionResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String token = response.getBody().getToken();
                log.debug("Created Judge0 submission with token: {}", token);
                return token;
            }

            log.error("Failed to create submission: {}", response.getStatusCode());
            return null;

        } catch (RestClientException e) {
            log.error("Error creating submission", e);
            throw e;
        }
    }

    private Judge0SubmissionResponse pollSubmission(String token, MicroserviceConfig.Judge0Config config) {
        String url = config.getEndpoint() + "/submissions/" + token + "?base64_encoded=false";
        HttpHeaders headers = createHeaders(config);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        int maxAttempts = config.getMaxPollAttempts() != null ? config.getMaxPollAttempts() : 25;
        int basePollIntervalMs = config.getPollIntervalMs() != null ? config.getPollIntervalMs() : 1000;
        int maxSleepMs = 5000; // Max 5 seconds

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                ResponseEntity<Judge0SubmissionResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, Judge0SubmissionResponse.class
                );

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    Judge0SubmissionResponse result = response.getBody();
                    Integer statusId = result.getStatus() != null ? result.getStatus().getId() : null;

                    // If not in queue or processing, we have the final result
                    if (statusId != null && statusId != STATUS_IN_QUEUE && statusId != STATUS_PROCESSING) {
                        log.debug("Submission {} completed with status {}", token, statusId);
                        return result;
                    }

                    // Still processing, wait with exponential backoff
                    int sleepMs = Math.min(basePollIntervalMs * (1 << attempt), maxSleepMs);
                    log.debug("Submission {} still processing (status {}), sleeping {}ms before next poll (attempt {}/{})", 
                            token, statusId, sleepMs, attempt + 1, maxAttempts);
                    
                    try {
                        Thread.sleep(sleepMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new ExecutionTimeoutException("Execution polling interrupted");
                    }
                } else {
                    log.warn("Failed to poll submission: {}", response.getStatusCode());
                }

            } catch (RestClientException e) {
                log.error("Error polling submission (attempt {}/{})", attempt + 1, maxAttempts, e);
                // Continue polling on error, but with exponential backoff
                int sleepMs = Math.min(basePollIntervalMs * (1 << attempt), maxSleepMs);
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new ExecutionTimeoutException("Execution polling interrupted");
                }
            }
        }

        // Timeout - return timeout result
        log.warn("Submission {} polling timed out after {} attempts", token, maxAttempts);
        throw new ExecutionTimeoutException("Execution timed out after " + maxAttempts + " polling attempts");
    }

    private ExecutionResult mapToExecutionResult(String roomId, Judge0SubmissionResponse result, long executionTime) {
        if (result.getStatus() == null) {
            return ExecutionResult.systemError(roomId, "Invalid response from Judge0");
        }

        Integer statusId = result.getStatus().getId();
        String stdout = result.getStdout() != null ? result.getStdout() : "";
        String stderr = result.getStderr() != null ? result.getStderr() : "";
        String compileOutput = result.getCompileOutput() != null ? result.getCompileOutput() : "";
        String message = result.getMessage() != null ? result.getMessage() : "";
        
        // Convert time from seconds to milliseconds
        long executionTimeMs = result.getTime() != null ? 
                (long) (result.getTime() * 1000) : executionTime;

        switch (statusId) {
            case STATUS_ACCEPTED:
                return ExecutionResult.builder()
                    .roomId(roomId)
                    .status(ExecutionStatus.SUCCESS)
                    .stdout(stdout)
                    .exitCode(0)
                    .executionTimeMs(executionTimeMs)
                    .build();

            case STATUS_COMPILATION_ERROR:
                String compileError = !compileOutput.isEmpty() ? compileOutput : 
                                     (!stderr.isEmpty() ? stderr : message);
                return ExecutionResult.builder()
                    .roomId(roomId)
                    .status(ExecutionStatus.ERROR)
                    .stderr(compileError)
                    .exitCode(1)
                    .executionTimeMs(executionTimeMs)
                    .build();

            case STATUS_RUNTIME_ERROR_SIGSEGV:
            case STATUS_RUNTIME_ERROR_SIGXFSZ:
            case STATUS_RUNTIME_ERROR_SIGFPE:
            case STATUS_RUNTIME_ERROR_SIGABRT:
            case STATUS_RUNTIME_ERROR_NZEC:
            case STATUS_RUNTIME_ERROR_OTHER:
            case STATUS_WRONG_ANSWER:
                String errorMsg = !stderr.isEmpty() ? stderr : 
                                 (result.getStatus().getDescription() != null ? 
                                  result.getStatus().getDescription() : "Runtime error");
                return ExecutionResult.builder()
                    .roomId(roomId)
                    .status(ExecutionStatus.ERROR)
                    .stderr(errorMsg)
                    .stdout(stdout)
                    .exitCode(1)
                    .executionTimeMs(executionTimeMs)
                    .build();

            case STATUS_TIME_LIMIT_EXCEEDED:
                return ExecutionResult.timeout(roomId, executionTimeMs);

            case STATUS_INTERNAL_ERROR:
            default:
                String systemError = result.getStatus().getDescription() != null ? 
                                   result.getStatus().getDescription() : "Internal error";
                if (!message.isEmpty()) {
                    systemError = message;
                }
                return ExecutionResult.systemError(roomId, systemError);
        }
    }

    private HttpHeaders createHeaders(MicroserviceConfig.Judge0Config config) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-RapidAPI-Key", config.getApiKey());
        headers.set("X-RapidAPI-Host", config.getApiHost());
        return headers;
    }
}
