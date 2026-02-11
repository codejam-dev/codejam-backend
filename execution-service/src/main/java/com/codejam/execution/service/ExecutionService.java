package com.codejam.execution.service;

import com.codejam.execution.dto.CodeSubmission;
import com.codejam.execution.dto.ExecutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionService {

    private final CodeExecutor executor;

    public ExecutionResult execute(CodeSubmission submission) {
        return executor.execute(submission);
    }

    public Object getSupportedLanguages() {
        return CodeSubmission.Language.values();
    }
}