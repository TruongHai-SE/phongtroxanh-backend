-- ==============================================================================
-- PHÒNG TRỌ XANH (PhongTroXanh.vn) - ENTERPRISE CONSOLIDATED SCHEMA DDL
-- DATABASE ENGINE: PostgreSQL 18 + PostGIS Extension
-- MIGRATION: V1 - CORE SCHEMA DDL
-- ==============================================================================

-- 0. KÍCH HOẠT EXTENSION
CREATE EXTENSION IF NOT EXISTS "postgis";

-- 1. ĐỊNH NGHĨA CÁC ENUM TYPES
DO $$ BEGIN
    CREATE TYPE user_role_enum AS ENUM ('TENANT', 'LANDLORD', 'ADMIN');
    CREATE TYPE user_status_enum AS ENUM ('ACTIVE', 'WARNED', 'LOCKED', 'DELETED');
    CREATE TYPE gender_enum AS ENUM ('MALE', 'FEMALE', 'OTHER');
    CREATE TYPE verification_status_enum AS ENUM ('PENDING', 'APPROVED', 'REJECTED');
    CREATE TYPE room_status_enum AS ENUM ('AVAILABLE', 'RENTED', 'HIDDEN', 'EXPIRED');
    CREATE TYPE swipe_dir_enum AS ENUM ('LEFT', 'RIGHT');
    CREATE TYPE match_status_enum AS ENUM ('MATCHED', 'UNMATCHED', 'BLOCKED');
    CREATE TYPE swap_status_enum AS ENUM ('OPEN', 'MATCHING', 'PENDING_LANDLORD', 'APPROVED', 'COMPLETED', 'CANCELLED', 'DECLINED');
    CREATE TYPE rental_status_enum AS ENUM ('PENDING_CHECKIN', 'CHECKED_IN', 'TERMINATED', 'CANCELLED');
    CREATE TYPE transaction_status_enum AS ENUM ('PENDING', 'SUCCESS', 'FAILED', 'EXPIRED');
    CREATE TYPE payment_method_enum AS ENUM ('VNPAY', 'VIETQR', 'PAYOS');
    CREATE TYPE report_status_enum AS ENUM ('NEW', 'PROCESSING', 'INVESTIGATING', 'RESOLVED', 'DISMISSED');
    CREATE TYPE report_severity_enum AS ENUM ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- Sequence cho mã giao dịch PayOS
CREATE SEQUENCE IF NOT EXISTS payos_order_code_seq START WITH 1000000000;

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
    preferred_gender VARCHAR(20) DEFAULT 'ANY',
    bio TEXT,
    address VARCHAR(255),
    budget_min NUMERIC(12, 2),
    budget_max NUMERIC(12, 2),
    preferred_districts VARCHAR(100)[],
    sleep_schedule VARCHAR(50),
    cleanliness_level INT CHECK (cleanliness_level BETWEEN 1 AND 5),
    guest_frequency VARCHAR(50),
    smoking_tolerance BOOLEAN,
    pet_tolerance VARCHAR(50),
    noise_tolerance INT CHECK (noise_tolerance BETWEEN 0 AND 100),
    allow_guests BOOLEAN,
    early_sleeper BOOLEAN,
    is_neat BOOLEAN,
    non_smoking BOOLEAN,
    preferred_room_type VARCHAR(50),
    proximity_school BOOLEAN,
    proximity_work BOOLEAN,
    proximity_market BOOLEAN,
    proximity_bus BOOLEAN,
    interests VARCHAR(50)[],
    is_public BOOLEAN DEFAULT TRUE,
    show_school BOOLEAN DEFAULT TRUE,
    hide_active_status BOOLEAN DEFAULT FALSE
);

-- 4. BẢNG HỒ SƠ NỘP CCCD / KYC (user_verifications)
CREATE TABLE IF NOT EXISTS user_verifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    id_card_number VARCHAR(255) NOT NULL,
    id_card_front_url VARCHAR(500) NOT NULL,
    id_card_back_url VARCHAR(500) NOT NULL,
    status verification_status_enum DEFAULT 'PENDING',
    rejection_reason TEXT,
    reviewed_by UUID REFERENCES users(id),
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 5. BẢNG DANH MỤC LOẠI PHÒNG TRỌ (room_types)
CREATE TABLE IF NOT EXISTS room_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    display_order INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 6. BẢNG PHÒNG TRỌ (rooms)
CREATE TABLE IF NOT EXISTS rooms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    landlord_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    room_type VARCHAR(50) NOT NULL,
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
    expires_at TIMESTAMPTZ DEFAULT (NOW() + INTERVAL '30 days'),
    amenities VARCHAR(100)[] NOT NULL DEFAULT '{}',
    creation_request_id UUID,
    last_image_upload_id UUID
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
    display_order INT DEFAULT 0,
    phash BIGINT
);

-- 9. BẢNG PHÒNG ĐÃ LƯU (saved_rooms)
CREATE TABLE IF NOT EXISTS saved_rooms (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (user_id, room_id)
);

-- 10. BẢNG QUẸT PHÒNG TRỌ (room_swipes)
CREATE TABLE IF NOT EXISTS room_swipes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    action VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT uq_user_room_swipe UNIQUE (user_id, room_id)
);

-- 11. BẢNG VUỐT BẠN Ở GHÉP (swipes)
CREATE TABLE IF NOT EXISTS swipes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    swiper_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    target_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    direction swipe_dir_enum NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (swiper_id, target_id)
);

-- 12. BẢNG GHÉP ĐÔI THÀNH CÔNG (matches)
CREATE TABLE IF NOT EXISTS matches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_a_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    user_b_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    compatibility_score NUMERIC(5, 2) NOT NULL,
    status match_status_enum DEFAULT 'MATCHED',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (user_a_id, user_b_id)
);

-- 13. BẢNG HOÁN ĐỔI PHÒNG THUÊ (swap_requests)
CREATE TABLE IF NOT EXISTS swap_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    current_room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE RESTRICT,
    offered_room_id UUID REFERENCES rooms(id),
    is_leaseholder BOOLEAN DEFAULT FALSE,
    target_districts VARCHAR(100)[],
    target_room_type VARCHAR(50),
    target_budget_max NUMERIC(12, 2),
    habits VARCHAR(100)[],
    reason TEXT NOT NULL,
    proposal_message TEXT,
    proposal_created_at TIMESTAMPTZ,
    target_move_in_date DATE,
    matched_tenant_id UUID REFERENCES users(id),
    landlord_id UUID REFERENCES users(id),
    landlord_decision swap_status_enum DEFAULT 'PENDING_LANDLORD',
    status swap_status_enum DEFAULT 'OPEN',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 14. BẢNG HỢP ĐỒNG THUÊ & CHECK-IN QR (rental_contracts)
CREATE TABLE IF NOT EXISTS rental_contracts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE RESTRICT,
    landlord_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    tenant_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    status rental_status_enum DEFAULT 'PENDING_CHECKIN',
    is_leaseholder BOOLEAN DEFAULT TRUE,
    check_in_code VARCHAR(16),
    check_in_qr_token VARCHAR(255),
    start_date DATE,
    end_date DATE,
    monthly_rent NUMERIC(12, 2),
    deposit_amount NUMERIC(12, 2),
    checked_in_at TIMESTAMPTZ,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 15. BẢNG ĐÁNH GIÁ 2 CHIỀU (reviews)
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
    status VARCHAR(30) DEFAULT 'ACTIVE',
    landlord_reply TEXT,
    replied_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 16. BẢNG KHIẾU NẠI ĐÁNH GIÁ (review_disputes)
CREATE TABLE IF NOT EXISTS review_disputes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_id UUID NOT NULL REFERENCES reviews(id) ON DELETE CASCADE,
    appellant_id UUID NOT NULL REFERENCES users(id),
    reason TEXT NOT NULL,
    evidence_images VARCHAR(500)[],
    status VARCHAR(30) DEFAULT 'PENDING_REVIEW',
    admin_notes TEXT,
    resolved_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    resolved_at TIMESTAMPTZ
);

-- 17. BẢNG LỊCH SỬ ĐIỂM UY TÍN (trust_score_logs)
CREATE TABLE IF NOT EXISTS trust_score_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    delta INT NOT NULL,
    final_score INT NOT NULL,
    reason VARCHAR(255) NOT NULL,
    reference_id UUID,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 18. BẢNG GÓI DỊCH VỤ & GIAO DỊCH THANH TOÁN (package_plans, subscriptions, payment_transactions, user_consumables)
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
    gateway_reference VARCHAR(150),
    payment_url VARCHAR(1000),
    payment_link_id VARCHAR(100),
    return_url VARCHAR(1000),
    qr_code_url VARCHAR(500),
    qr_expired_at TIMESTAMPTZ,
    benefits JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS user_consumables (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    swipes_left INT DEFAULT 15,
    free_swipes_left INT NOT NULL DEFAULT 15,
    boosts_left INT DEFAULT 0,
    profile_boost_expires_at TIMESTAMPTZ,
    last_swipe_reset_at DATE DEFAULT CURRENT_DATE,
    version BIGINT DEFAULT 0
);

-- 19. BẢNG BÁO CÁO VI PHẠM (reports)
CREATE TABLE IF NOT EXISTS reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    target_type VARCHAR(20) NOT NULL,
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

-- 20. BẢNG HỘI THOẠI & TIN NHẮN REAL-TIME (conversations, messages)
CREATE TABLE IF NOT EXISTS conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type VARCHAR(20) NOT NULL,
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
    attachment_url VARCHAR(2000),
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 21. BẢNG THÔNG BÁO IN-APP (notifications)
CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    type VARCHAR(50) NOT NULL,
    data JSONB,
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 22. BẢNG TRANSACTIONAL OUTBOX (outbox_events)
CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(30) DEFAULT 'PENDING',
    retry_count INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    processed_at TIMESTAMPTZ
);

-- 23. BẢNG NHẬT KÝ KIỂM TOÁN HỆ THỐNG (system_audit_logs)
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
