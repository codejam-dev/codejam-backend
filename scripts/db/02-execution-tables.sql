-- Execution Service Tables
-- Schema: execution
-- Run after 00-init.sql (execution schema must exist)

-- Run history: last 10 runs per user
CREATE TABLE IF NOT EXISTS execution.run_history (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    room_id VARCHAR(255) NOT NULL,
    language VARCHAR(32) NOT NULL,
    code TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    stdout TEXT,
    stderr TEXT,
    exit_code INTEGER,
    execution_time_ms BIGINT,
    error_message VARCHAR(1024),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Index for fetching last 10 runs per user
CREATE INDEX IF NOT EXISTS idx_run_history_user_created ON execution.run_history(user_id, created_at DESC);
