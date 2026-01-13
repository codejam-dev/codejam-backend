package com.codejam.execution.service;


import com.codejam.execution.dto.CodeSubmission;
import com.codejam.execution.dto.ExecutionResult;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OracleVMExecutor implements CodeExecutor {

    @Override
    public ExecutionResult execute(CodeSubmission submission) {
        log.error("Oracle VM executor not implemented yet");
        throw new UnsupportedOperationException(
                "Oracle VM executor is not configured. Use Docker executor for development."
        );
    }
}
