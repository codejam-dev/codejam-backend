package com.codejam.execution.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionResult {

    private String roomId;
    private ExecutionStatus status;
    private String stdout;
    private String stderr;
    private Integer exitCode;
    private Long executionTimeMs;
    private String errorMessage;

    public static ExecutionResult success(String roomId, String output, long timeMs) {
        return ExecutionResult.builder()
                .roomId(roomId)
                .status(ExecutionStatus.SUCCESS)
                .stdout(output)
                .exitCode(0)
                .executionTimeMs(timeMs)
                .build();
    }

    public static ExecutionResult error(String roomId, String error, long timeMs) {
        return ExecutionResult.builder()
                .roomId(roomId)
                .status(ExecutionStatus.ERROR)
                .stderr(error)
                .exitCode(1)
                .executionTimeMs(timeMs)
                .build();
    }

    public static ExecutionResult timeout(String roomId, long timeMs) {
        return ExecutionResult.builder()
                .roomId(roomId)
                .status(ExecutionStatus.TIMEOUT)
                .errorMessage("Execution timed out")
                .executionTimeMs(timeMs)
                .build();
    }

    public static ExecutionResult systemError(String roomId, String errorMessage) {
        return ExecutionResult.builder()
                .roomId(roomId)
                .status(ExecutionStatus.SYSTEM_ERROR)
                .errorMessage(errorMessage)
                .build();
    }
}
