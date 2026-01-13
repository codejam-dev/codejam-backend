package com.codejam.execution.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Judge0SubmissionRequest {
    @JsonProperty("language_id")
    private Integer languageId;
    
    @JsonProperty("source_code")
    private String sourceCode;
    
    @JsonProperty("stdin")
    private String stdin;
    
    @JsonProperty("cpu_time_limit")
    private Double cpuTimeLimit;
    
    @JsonProperty("memory_limit")
    private Integer memoryLimit; // KB
    
    @JsonProperty("wall_time_limit")
    private Double wallTimeLimit;
    
    @JsonProperty("base64_encoded")
    @Builder.Default
    private Boolean base64Encoded = false;
}
