package com.codejam.execution.service;

import com.codejam.execution.dto.CodeSubmission;
import com.codejam.execution.dto.ExecutionResult;

public interface CodeExecutor {

    /**
     * Execute code and return result
     * @param submission Code submission with language and source code
     * @return Execution result with stdout, stderr, and exit code
     */
    ExecutionResult execute(CodeSubmission submission);
}