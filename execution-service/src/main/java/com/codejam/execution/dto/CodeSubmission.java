package com.codejam.execution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodeSubmission {

    @NotBlank(message = "Room ID is required")
    private String roomId;

    @NotNull(message = "Language is required")
    private Language language;

    @NotBlank(message = "Code is required")
    private String code;

    @Getter
    public enum Language {
        JAVASCRIPT("javascript", "node:20-alpine", ".js", "node"),
        PYTHON("python", "python:3.11-alpine", ".py", "python"),
        JAVA("java", "openjdk:17-alpine", ".java", "javac"),
        CPP("cpp", "gcc:13-alpine", ".cpp", "g++"),
        C("c", "gcc:13-alpine", ".c", "gcc"),
        GO("go", "golang:1.21-alpine", ".go", "go run"),
        RUST("rust", "rust:1.75-alpine", ".rs", "rustc");

        private final String name;
        private final String dockerImage;
        private final String extension;
        private final String compiler;

        Language(String name, String dockerImage, String extension, String compiler) {
            this.name = name;
            this.dockerImage = dockerImage;
            this.extension = extension;
            this.compiler = compiler;
        }

    }
}