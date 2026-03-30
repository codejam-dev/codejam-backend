-- CodeJam Database Initialization
-- Run this script first to create schemas

-- Create auth schema for auth-service
CREATE SCHEMA IF NOT EXISTS auth;

-- Grant permissions (if needed for specific users)
-- GRANT ALL ON SCHEMA auth TO codejam;


-- Create executor schema for execution-service
CREATE SCHEMA IF NOT EXISTS execution;