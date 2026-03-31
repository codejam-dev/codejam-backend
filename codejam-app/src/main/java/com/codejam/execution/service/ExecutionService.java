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
    private final RunHistoryService runHistoryService;

    public ExecutionResult execute(CodeSubmission submission, String userId) {
        log.info("Executing code for room: {}, language: {}", submission.getRoomId(), submission.getLanguage());
        ExecutionResult result = executor.execute(submission,userId);
        log.info("Execution completed for room: {}, status: {}, time: {}ms", result.getRoomId(), result.getStatus(), result.getExecutionTimeMs());
        runHistoryService.saveRun(userId, submission, result);
        return result;
    }

    public Object getSupportedLanguages() {
        return CodeSubmission.Language.values();
    }
}