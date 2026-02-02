-- Auth Service Tables
-- Schema: auth

-- Users table
CREATE TABLE IF NOT EXISTS auth.users (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL UNIQUE,
    name VARCHAR(500),
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255),
    profile_image_url TEXT,
    provider VARCHAR(50),  -- LOCAL, GOOGLE, FACEBOOK, GITHUB
    provider_id VARCHAR(500),
    profile_image BYTEA,
    enabled BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_users_email ON auth.users(email);
CREATE INDEX IF NOT EXISTS idx_users_user_id ON auth.users(user_id);
CREATE INDEX IF NOT EXISTS idx_users_provider ON auth.users(provider);
