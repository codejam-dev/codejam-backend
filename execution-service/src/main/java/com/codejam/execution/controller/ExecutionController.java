package com.codejam.execution.controller;

import com.codejam.commons.constant.ApiConstants;
import com.codejam.execution.dto.CodeSubmission;
import com.codejam.execution.dto.ExecutionResult;
import com.codejam.execution.dto.RunHistoryResponse;
import com.codejam.execution.service.ExecutionService;
import com.codejam.execution.service.RunHistoryService;
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

    private final ExecutionService executionService;
    private final RunHistoryService runHistoryService;

    @PostMapping("/run")
    public ResponseEntity<ExecutionResult> runCode(@RequestHeader(value = ApiConstants.USER_ID_HEADER) String userId, @Valid @RequestBody CodeSubmission submission) {
        return ResponseEntity.ok(executionService.execute(submission,userId));
    }

    @GetMapping("/history")
    public ResponseEntity<RunHistoryResponse> getRunHistory(@RequestHeader(value = ApiConstants.USER_ID_HEADER) String userId) {
        return ResponseEntity.ok(runHistoryService.getLast10ForUser(userId));
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