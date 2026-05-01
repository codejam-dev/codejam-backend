package com.codejam.execution.service;

/**
 * Stdout/stderr pair from a container or exec session.
 */
public record ExecutionCapturedOutput(String stdout, String stderr) {}
