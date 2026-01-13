package com.codejam.execution.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Judge0SubmissionResponse {
    private String token;
    
    @JsonProperty("status")
    private Judge0Status status;
    
    @JsonProperty("stdout")
    private String stdout;
    
    @JsonProperty("stderr")
    private String stderr;
    
    @JsonProperty("compile_output")
    private String compileOutput;
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("time")
    private Double time; // seconds
    
    @JsonProperty("memory")
    private Integer memory; // KB
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Judge0Status {
        private Integer id;
        private String description;
    }
}
