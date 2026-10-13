-- Migration V4: Tạo bảng đánh giá & góp ý hệ thống nền tảng (system_feedbacks)
CREATE TABLE IF NOT EXISTS system_feedbacks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    rating INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    category VARCHAR(50) NOT NULL DEFAULT 'app_experience',
    comment TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_system_feedbacks_created_at ON system_feedbacks (created_at DESC);
