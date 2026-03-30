package com.codejam.execution.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
@Data
@Builder
public class RunHistoryResponse {
    List<RunHistoryItemDto> runHistory;
}
