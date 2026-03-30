package com.codejam.execution.service;

import com.codejam.commons.util.ObjectUtils;
import com.codejam.execution.config.MicroserviceConfig;
import com.codejam.execution.dto.CodeSubmission;
import com.codejam.execution.dto.ExecutionResult;
import com.codejam.execution.dto.RunHistoryItemDto;
import com.codejam.execution.dto.RunHistoryResponse;
import com.codejam.execution.model.RunHistory;
import com.codejam.execution.repository.RunHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class RunHistoryService {

    private final RunHistoryRepository runHistoryRepository;
    private final MicroserviceConfig microserviceConfig;

    @Transactional
    public void saveRun(String userId, CodeSubmission submission, ExecutionResult result) {
        RunHistory run = buildRun(userId, submission, result);
        runHistoryRepository.save(run);

        int maxRuns = microserviceConfig.getRunHistory().getMaxRunsPerUser();
        runHistoryRepository.pruneOldRuns(userId, maxRuns);
    }
    public RunHistoryResponse getLast10ForUser(String userId) {
        if (ObjectUtils.isNullOrEmpty(userId)) {
            return RunHistoryResponse.builder().runHistory(List.of()).build();
        }
        int maxRuns = microserviceConfig.getRunHistory().getMaxRunsPerUser();
        PageRequest pageRequest = PageRequest.of(0, maxRuns, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<RunHistoryItemDto> runs = runHistoryRepository.findByUserId(userId, pageRequest)
                .getContent()
                .stream()
                .map(this::toDto)
                .toList();

        System.out.println("Total Number of runs"+ runs.size());
        return RunHistoryResponse.builder().runHistory(runs).build();
    }

    private RunHistoryItemDto toDto(RunHistory r) {
        return RunHistoryItemDto.builder()
                .id(r.getId())
                .roomId(r.getRoomId())
                .language(r.getLanguage())
                .code(r.getCode())
                .status(r.getStatus())
                .stdout(r.getStdout())
                .stderr(r.getStderr())
                .exitCode(r.getExitCode())
                .executionTimeMs(r.getExecutionTimeMs())
                .errorMessage(r.getErrorMessage())
                .createdAt(r.getCreatedAt())
                .build();
    }

    private RunHistory buildRun(String userId,
                                CodeSubmission submission,
                                ExecutionResult result) {

        Objects.requireNonNull(userId, "userId required");
        Objects.requireNonNull(submission, "submission required");
        Objects.requireNonNull(result, "result required");

        var runHistoryConfig = microserviceConfig.getRunHistory();
        return RunHistory.builder()
                .userId(userId)
                .roomId(submission.getRoomId())
                .language(submission.getLanguage().name())
                .code(truncate(submission.getCode(), runHistoryConfig.getMaxCodeLength()))
                .status(result.getStatus())
                .stdout(truncate(result.getStdout(), runHistoryConfig.getMaxOutputLength()))
                .stderr(truncate(result.getStderr(), runHistoryConfig.getMaxOutputLength()))
                .exitCode(result.getExitCode())
                .executionTimeMs(result.getExecutionTimeMs())
                .errorMessage(truncate(result.getErrorMessage(), runHistoryConfig.getMaxErrorLength()))
                .build();
    }
    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength
                ? value
                : value.substring(0, maxLength);
    }
}
