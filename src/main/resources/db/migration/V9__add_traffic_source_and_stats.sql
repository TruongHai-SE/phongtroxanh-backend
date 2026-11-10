-- ==============================================================================
-- PHÒNG TRỌ XANH (PhongTroXanh.vn)
-- MIGRATION: V9 - ADD USER TRAFFIC SOURCE ATTRIBUTION & TRAFFIC STATS TABLE
-- Automatically executed by Flyway on Local PostgreSQL, Testcontainers, and Supabase
-- ==============================================================================

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS traffic_source VARCHAR(30) NOT NULL DEFAULT 'direct';

CREATE TABLE IF NOT EXISTS traffic_stats (
    source VARCHAR(30) PRIMARY KEY,
    count BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO traffic_stats (source, count, updated_at) VALUES
    ('facebook', 84, NOW()),
    ('tiktok', 52, NOW()),
    ('direct', 30, NOW()),
    ('google', 6, NOW()),
    ('zalo', 4, NOW())
ON CONFLICT (source) DO NOTHING;
