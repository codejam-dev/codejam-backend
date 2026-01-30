package com.codejam.execution.controller;

import com.codejam.execution.dto.CodeSubmission;
import com.codejam.execution.dto.ExecutionResult;
import com.codejam.execution.exception.RateLimitExceededException;
import com.codejam.execution.service.ExecutionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/v1/api/execution")
@RequiredArgsConstructor
public class ExecutionController {

    private final ExecutionService executionService;

    @PostMapping("/run")
    public ResponseEntity<ExecutionResult> runCode(@Valid @RequestBody CodeSubmission submission) {
        log.info("Executing code for room: {}, language: {}",
                submission.getRoomId(), submission.getLanguage());

        try {
            ExecutionResult result = executionService.execute(submission);

            log.info("Execution completed for room: {}, status: {}, time: {}ms",
                    result.getRoomId(), result.getStatus(), result.getExecutionTimeMs());

            return ResponseEntity.ok(result);
        } catch (RateLimitExceededException e) {
            log.warn("Rate limit exceeded for room: {}", submission.getRoomId());
            ExecutionResult rateLimitResult = ExecutionResult.systemError(
                submission.getRoomId(), 
                e.getMessage()
            );
            return ResponseEntity.ok(rateLimitResult);
        }
    }

    @GetMapping("/supported-languages")
    public ResponseEntity<?> getSupportedLanguages() {
        return ResponseEntity.ok(executionService.getSupportedLanguages());
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Execution service is running");
    }
}