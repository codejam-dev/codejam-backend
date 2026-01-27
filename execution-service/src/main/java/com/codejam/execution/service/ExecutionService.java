package com.codejam.execution.service;

import com.codejam.execution.dto.CodeSubmission;
import com.codejam.execution.dto.ExecutionResult;
import com.codejam.execution.exception.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionService {

    private final CodeExecutor executor;
    
    @Autowired(required = false)
    private Judge0RateLimiter rateLimiter;

    public ExecutionResult execute(CodeSubmission submission) {
        // Check rate limiter before execution
        if (rateLimiter != null && !rateLimiter.allowExecution(submission.getRoomId())) {
            log.warn("Rate limit exceeded for room: {}", submission.getRoomId());
            throw new RateLimitExceededException("Daily execution limit reached");
        }

        return executor.execute(submission);
    }

    public Object getSupportedLanguages() {
        return CodeSubmission.Language.values();
    }
}