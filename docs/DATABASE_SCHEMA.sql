-- ==============================================================================
-- PHÒNG TRỌ XANH (PhongTroXanh.vn) - ENTERPRISE DATABASE SCHEMA DDL
-- DATABASE ENGINE: PostgreSQL 16 + PostGIS Extension
-- ==============================================================================

-- 0. KÍCH HOẠT EXTENSION
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "postgis";

-- 1. ĐỊNH NGHĨA CÁC ENUM TYPES
DO $$ BEGIN
    CREATE TYPE user_role_enum AS ENUM ('TENANT', 'LANDLORD', 'ADMIN');
    CREATE TYPE user_status_enum AS ENUM ('ACTIVE', 'WARNED', 'LOCKED', 'DELETED');
    CREATE TYPE gender_enum AS ENUM ('MALE', 'FEMALE', 'OTHER');
    CREATE TYPE verification_status_enum AS ENUM ('PENDING', 'APPROVED', 'REJECTED');
    CREATE TYPE room_status_enum AS ENUM ('AVAILABLE', 'RENTED', 'HIDDEN', 'EXPIRED');
    CREATE TYPE swipe_dir_enum AS ENUM ('LEFT', 'RIGHT', 'SUPER'); -- LEFT: Bỏ qua (X), RIGHT: Thích (Heart), SUPER: Super Match (⭐)
    CREATE TYPE match_status_enum AS ENUM ('MATCHED', 'UNMATCHED', 'BLOCKED');
    CREATE TYPE swap_status_enum AS ENUM ('OPEN', 'MATCHING', 'PENDING_LANDLORD', 'APPROVED', 'COMPLETED', 'CANCELLED', 'DECLINED');
    CREATE TYPE rental_status_enum AS ENUM ('PENDING_CHECKIN', 'CHECKED_IN', 'TERMINATED', 'CANCELLED');
    CREATE TYPE transaction_status_enum AS ENUM ('PENDING', 'SUCCESS', 'FAILED', 'EXPIRED');
    CREATE TYPE payment_method_enum AS ENUM ('VNPAY', 'VIETQR');
    CREATE TYPE report_status_enum AS ENUM ('NEW', 'PROCESSING', 'RESOLVED', 'DISMISSED');
    CREATE TYPE report_severity_enum AS ENUM ('CRITICAL', 'MEDIUM', 'LOW');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- 2. BẢNG NGƯỜI DÙNG GỐC (users)
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_number VARCHAR(15) UNIQUE,
    email VARCHAR(255) UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    role user_role_enum NOT NULL DEFAULT 'TENANT',
    status user_status_enum NOT NULL DEFAULT 'ACTIVE',
    avatar_url VARCHAR(500),
    is_verified BOOLEAN DEFAULT FALSE,
    trust_score INT DEFAULT 50 CHECK (trust_score BETWEEN 0 AND 100),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    last_active_at TIMESTAMPTZ DEFAULT NOW()
);

-- 3. BẢNG HỒ SƠ & MA TRẬN TƯƠNG THÍCH BẠN CÙNG PHÒNG (user_profiles)
CREATE TABLE IF NOT EXISTS user_profiles (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    school_or_company VARCHAR(200),
    birth_date DATE,
    gender gender_enum DEFAULT 'OTHER',
    preferred_gender VARCHAR(20) DEFAULT 'ANY', -- 'MALE', 'FEMALE', 'ANY' (Bất kỳ)
    bio TEXT,
    budget_min NUMERIC(12, 2) DEFAULT 1000000,
    budget_max NUMERIC(12, 2) DEFAULT 4000000,
    preferred_districts VARCHAR(100)[],
    sleep_schedule VARCHAR(50),
    cleanliness_level INT DEFAULT 4 CHECK (cleanliness_level BETWEEN 1 AND 5),
    guest_frequency VARCHAR(50),
    smoking_tolerance BOOLEAN DEFAULT FALSE,
    pet_tolerance VARCHAR(50) DEFAULT 'NONE',
    noise_tolerance INT DEFAULT 40 CHECK (noise_tolerance BETWEEN 0 AND 100),
    allow_guests BOOLEAN DEFAULT FALSE,
    early_sleeper BOOLEAN DEFAULT TRUE,
    is_neat BOOLEAN DEFAULT TRUE,
    non_smoking BOOLEAN DEFAULT TRUE,
    preferred_room_type VARCHAR(50) DEFAULT 'PHONG_KHEP_KIN',
    proximity_school BOOLEAN DEFAULT TRUE,
    proximity_work BOOLEAN DEFAULT TRUE,
    proximity_market BOOLEAN DEFAULT TRUE,
    proximity_bus BOOLEAN DEFAULT TRUE,
    interests VARCHAR(50)[],
    is_public BOOLEAN DEFAULT TRUE,
    show_school BOOLEAN DEFAULT TRUE,
    hide_active_status BOOLEAN DEFAULT FALSE
);

-- 4. BẢNG HỒ SƠ NỘP CCCD / KYC (user_verifications)
CREATE TABLE IF NOT EXISTS user_verifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    id_card_number VARCHAR(255) NOT NULL, -- Mã hóa AES-256
    id_card_front_url VARCHAR(500) NOT NULL, -- S3 Private URI
    id_card_back_url VARCHAR(500) NOT NULL,
    status verification_status_enum DEFAULT 'PENDING',
    rejection_reason TEXT,
    reviewed_by UUID REFERENCES users(id),
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 5. BẢNG DANH MỤC LOẠI PHÒNG TRỌ ĐỘNG (room_types - Admin có thể thêm mới)
CREATE TABLE IF NOT EXISTS room_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) UNIQUE NOT NULL, -- 'PHONG_TRO', 'PHONG_KHEP_KIN', 'STUDIO', 'CAN_HO_MINI', 'SLEEPBOX', 'KY_TUC_XA'
    name VARCHAR(100) NOT NULL,       -- 'Phòng trọ', 'Phòng khép kín', 'Studio', 'Căn hộ mini', 'Sleepbox'
    is_active BOOLEAN DEFAULT TRUE,
    display_order INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 6. BẢNG PHÒNG TRỌ (rooms)
CREATE TABLE IF NOT EXISTS rooms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    landlord_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    room_type VARCHAR(50) NOT NULL, -- Mã loại phòng động (linh hoạt cho Admin mở rộng)
    price NUMERIC(12, 2) NOT NULL,
    deposit_amount NUMERIC(12, 2) NOT NULL,
    area_sqm NUMERIC(6, 2) NOT NULL,
    floor_number INT DEFAULT 1,
    max_occupants INT DEFAULT 2,
    address_street VARCHAR(255) NOT NULL,
    district VARCHAR(100) NOT NULL,
    city VARCHAR(100) NOT NULL DEFAULT 'TP.HCM',
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    location GEOMETRY(Point, 4326),
    status room_status_enum DEFAULT 'AVAILABLE',
    is_verified BOOLEAN DEFAULT FALSE,
    is_boosted BOOLEAN DEFAULT FALSE,
    boost_expires_at TIMESTAMPTZ,
    view_count BIGINT DEFAULT 0,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    expires_at TIMESTAMPTZ DEFAULT (NOW() + INTERVAL '30 days')
);

-- 7. BẢNG BIỂU PHÍ PHỤ TRỢ (room_fees)
CREATE TABLE IF NOT EXISTS room_fees (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    fee_label VARCHAR(100) NOT NULL,
    fee_value VARCHAR(100) NOT NULL
);

-- 8. BẢNG ẢNH PHÒNG TRỌ (room_images)
CREATE TABLE IF NOT EXISTS room_images (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    image_url VARCHAR(500) NOT NULL,
    is_primary BOOLEAN DEFAULT FALSE,
    display_order INT DEFAULT 0
);

-- 9. BẢNG PHÒNG ĐÃ LƯU (saved_rooms)
CREATE TABLE IF NOT EXISTS saved_rooms (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (user_id, room_id)
);

-- 10. BẢNG VUỐT THẺ (swipes)
CREATE TABLE IF NOT EXISTS swipes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    swiper_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    target_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    direction swipe_dir_enum NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (swiper_id, target_id)
);

-- 11. BẢNG GHÉP ĐÔI THÀNH CÔNG (matches)
CREATE TABLE IF NOT EXISTS matches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_a_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    user_b_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    compatibility_score NUMERIC(5, 2) NOT NULL,
    status match_status_enum DEFAULT 'MATCHED',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (user_a_id, user_b_id)
);

-- 12. BẢNG HOÁN ĐỔI PHÒNG THUÊ (swap_requests)
CREATE TABLE IF NOT EXISTS swap_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    current_room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE RESTRICT,
    is_leaseholder BOOLEAN DEFAULT FALSE,
    target_districts VARCHAR(100)[],
    target_room_type VARCHAR(50), -- Loại phòng mong muốn (động)
    target_budget_max NUMERIC(12, 2),
    habits VARCHAR(100)[],
    reason TEXT NOT NULL,
    target_move_in_date DATE,
    matched_tenant_id UUID REFERENCES users(id),
    landlord_id UUID REFERENCES users(id),
    landlord_decision swap_status_enum DEFAULT 'PENDING_LANDLORD',
    status swap_status_enum DEFAULT 'OPEN',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 13. BẢNG HỢP ĐỒNG THUÊ & CHECK-IN QR (rental_contracts)
CREATE TABLE IF NOT EXISTS rental_contracts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE RESTRICT,
    landlord_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    tenant_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    status rental_status_enum DEFAULT 'PENDING_CHECKIN',
    is_leaseholder BOOLEAN DEFAULT TRUE,
    check_in_code VARCHAR(16) NOT NULL,
    check_in_qr_token VARCHAR(255) UNIQUE NOT NULL,
    start_date DATE,
    end_date DATE,
    checked_in_at TIMESTAMPTZ,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 14. BẢNG ĐÁNH GIÁ 2 CHIỀU (reviews)
CREATE TABLE IF NOT EXISTS reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rental_contract_id UUID NOT NULL REFERENCES rental_contracts(id) ON DELETE RESTRICT,
    reviewer_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reviewee_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    room_id UUID REFERENCES rooms(id) ON DELETE SET NULL,
    rating INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    tags VARCHAR(50)[],
    comment TEXT NOT NULL,
    images VARCHAR(500)[],
    is_verified_stay BOOLEAN DEFAULT TRUE,
    status VARCHAR(30) DEFAULT 'ACTIVE', -- 'ACTIVE', 'UNDER_DISPUTE', 'REMOVED'
    landlord_reply TEXT,
    replied_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 15. BẢNG KHIẾU NẠI ĐÁNH GIÁ CHỐNG GIAN LẬN (review_disputes)
CREATE TABLE IF NOT EXISTS review_disputes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_id UUID NOT NULL REFERENCES reviews(id) ON DELETE CASCADE,
    appellant_id UUID NOT NULL REFERENCES users(id),
    reason TEXT NOT NULL,
    evidence_images VARCHAR(500)[],
    status VARCHAR(30) DEFAULT 'PENDING_REVIEW', -- 'PENDING_REVIEW', 'ACCEPTED', 'REJECTED'
    admin_notes TEXT,
    resolved_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    resolved_at TIMESTAMPTZ
);

-- 16. BẢNG LỊCH SỬ ĐIỂM UY TÍN (trust_score_logs)
CREATE TABLE IF NOT EXISTS trust_score_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    delta INT NOT NULL,
    final_score INT NOT NULL,
    reason VARCHAR(255) NOT NULL,
    reference_id UUID,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 17. BẢNG GÓI DỊCH VỤ & SUBSCRIPTION (package_plans, subscriptions, payment_transactions, user_consumables)
CREATE TABLE IF NOT EXISTS package_plans (
    id VARCHAR(50) PRIMARY KEY,
    target_role user_role_enum NOT NULL,
    name VARCHAR(100) NOT NULL,
    price_monthly NUMERIC(12, 2) NOT NULL,
    price_yearly NUMERIC(12, 2) NOT NULL,
    features JSONB NOT NULL
);

CREATE TABLE IF NOT EXISTS subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    plan_id VARCHAR(50) NOT NULL REFERENCES package_plans(id),
    billing_cycle VARCHAR(20) NOT NULL,
    start_date TIMESTAMPTZ NOT NULL,
    end_date TIMESTAMPTZ NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS payment_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    item_type VARCHAR(50) NOT NULL,
    item_name VARCHAR(150) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    payment_method payment_method_enum NOT NULL,
    status transaction_status_enum DEFAULT 'PENDING',
    idempotency_key VARCHAR(100) UNIQUE NOT NULL,
    gateway_order_id VARCHAR(100),
    qr_code_url VARCHAR(500),
    qr_expired_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS user_consumables (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    swipes_left INT DEFAULT 15,              -- Reset hàng ngày
    boosts_left INT DEFAULT 0,               -- Mua thêm, không reset
    super_matches_left INT DEFAULT 0,        -- Super Match ⭐ consumable (SUPER swipe)
    last_swipe_reset_at DATE DEFAULT CURRENT_DATE,
    version BIGINT DEFAULT 0                 -- Optimistic locking (atomic deduct)
);

-- 18. BẢNG BÁO CÁO VI PHẠM & MODERATION (reports)
CREATE TABLE IF NOT EXISTS reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    target_type VARCHAR(20) NOT NULL, -- 'ROOM', 'USER', 'REVIEW'
    target_id UUID NOT NULL,
    report_type VARCHAR(100) NOT NULL,
    detail TEXT NOT NULL,
    evidence_images VARCHAR(500)[],
    status report_status_enum DEFAULT 'NEW',
    severity report_severity_enum DEFAULT 'MEDIUM',
    handler_id UUID REFERENCES users(id),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 19. BẢNG HỘI THOẠI & TIN NHẮN REAL-TIME (conversations, messages)
CREATE TABLE IF NOT EXISTS conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type VARCHAR(20) NOT NULL, -- 'ROOM', 'ROOMMATE'
    room_id UUID REFERENCES rooms(id) ON DELETE SET NULL,
    participant1_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    participant2_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    last_message_text TEXT,
    last_message_at TIMESTAMPTZ DEFAULT NOW(),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 20. BẢNG THÔNG BÁO IN-APP (notifications)
CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    type VARCHAR(50) NOT NULL, -- 'MATCH', 'MESSAGE', 'SYSTEM', 'PAYMENT', 'REVIEW'
    data JSONB,
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 21. BẢNG SỰ KIỆN TRANSACTIONAL OUTBOX (outbox_events)
CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(30) DEFAULT 'PENDING', -- 'PENDING', 'PROCESSED', 'FAILED'
    retry_count INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    processed_at TIMESTAMPTZ
);

-- 22. BẢNG NHẬT KÝ KIỂM TOÁN DOANH NGHIỆP (system_audit_logs)
CREATE TABLE IF NOT EXISTS system_audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id UUID REFERENCES users(id),
    action VARCHAR(100) NOT NULL,
    target_entity VARCHAR(50) NOT NULL,
    target_id UUID NOT NULL,
    payload_before JSONB,
    payload_after JSONB,
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- ==============================================================================
-- CHỈ MỤC TỐI ƯU HÓA TRUY VẤN (PERFORMANCE INDEXES)
-- ==============================================================================
CREATE INDEX IF NOT EXISTS idx_rooms_location_gist ON rooms USING GIST (location);
CREATE INDEX IF NOT EXISTS idx_rooms_district_status ON rooms (district, status, price);
CREATE INDEX IF NOT EXISTS idx_rooms_landlord_id ON rooms (landlord_id);
CREATE INDEX IF NOT EXISTS idx_swipes_swiper_target ON swipes (swiper_id, target_id);
CREATE INDEX IF NOT EXISTS idx_matches_users ON matches (user_a_id, user_b_id);
CREATE INDEX IF NOT EXISTS idx_messages_conversation_created ON messages (conversation_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_user_unread ON notifications (user_id, is_read, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_outbox_pending ON outbox_events (status, created_at) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_reports_status_severity ON reports (status, severity);
CREATE INDEX IF NOT EXISTS idx_audit_logs_actor ON system_audit_logs (actor_id, created_at DESC);

