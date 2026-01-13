package com.codejam.execution.controller;

import com.codejam.execution.dto.CodeSubmission;
import com.codejam.execution.dto.ExecutionResult;
import com.codejam.execution.service.CodeExecutor;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/execution")
@RequiredArgsConstructor
public class ExecutionController {

    private final CodeExecutor executor;

    @PostMapping("/run")
    public ResponseEntity<ExecutionResult> runCode(@Valid @RequestBody CodeSubmission submission) {
        log.info("Executing code for room: {}, language: {}",
                submission.getRoomId(), submission.getLanguage());

        ExecutionResult result = executor.execute(submission);

        log.info("Execution completed for room: {}, status: {}, time: {}ms",
                result.getRoomId(), result.getStatus(), result.getExecutionTimeMs());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Execution service is running");
    }
}