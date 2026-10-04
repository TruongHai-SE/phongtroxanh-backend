-- ==============================================================================
-- PHÒNG TRỌ XANH (PhongTroXanh.vn) - ENTERPRISE CONSOLIDATED MIGRATION
-- MIGRATION: V2 - SEED MASTER DATA, PACKAGES & DEFAULT SYSTEM ADMIN
-- ==============================================================================

-- 1. DANH MỤC LOẠI PHÒNG TRỌ MẶC ĐỊNH (room_types)
INSERT INTO room_types (code, name, is_active, display_order)
VALUES
    ('PHONG_TRO', 'Phòng trọ truyền thống', true, 1),
    ('PHONG_KHEP_KIN', 'Phòng khép kín / Studio', true, 2),
    ('CHUNG_CU_MINI', 'Chung cư mini / Căn hộ', true, 3),
    ('KTX_SLEEPBOX', 'KTX / Kén ngủ Sleepbox', true, 4),
    ('NHA_NGUYEN_CAN', 'Nhà nguyên căn', true, 5),
    ('O_GHEP', 'Phòng ở ghép', true, 6)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    is_active = EXCLUDED.is_active,
    display_order = EXCLUDED.display_order;

-- 2. CÁC GÓI DỊCH VỤ HỆ THỐNG (package_plans) BÁM SÁT THỊ TRƯỜNG THỰC TẾ
INSERT INTO package_plans (id, target_role, name, price_monthly, price_yearly, features)
VALUES
    -- Gói miễn phí khởi đầu
    ('FREE', 'TENANT', 'Gói Miễn Phí', 0, 0, '{"swipes_per_day": 15, "boosts": 0}'::jsonb),

    -- Gói dành cho Người thuê tìm phòng & bạn ở ghép
    ('PRO_TENANT', 'TENANT', 'Gói Pro Tìm Bạn & Thuê Phòng', 29000, 290000, '{"swipes_per_day": 50, "boosts": 2}'::jsonb),
    ('VIP_TENANT', 'TENANT', 'Gói VIP Ghép Đôi Siêu Tốc', 59000, 590000, '{"swipes_per_day": 100, "boosts": 6}'::jsonb),

    -- Gói dành cho Chủ trọ đăng tin & tiếp cận khách thuê
    ('LANDLORD_BASIC', 'LANDLORD', 'Gói Đẩy Tin Trải Nghiệm', 39000, 390000, '{"swipes_per_day": 0, "boosts": 3}'::jsonb),
    ('LANDLORD_VIP', 'LANDLORD', 'Gói Chủ Trọ VIP', 79000, 790000, '{"swipes_per_day": 0, "boosts": 8}'::jsonb),
    ('LANDLORD_PRO', 'LANDLORD', 'Gói Chủ Trọ Chuyên Nghiệp', 149000, 1490000, '{"swipes_per_day": 0, "boosts": 20}'::jsonb)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    price_monthly = EXCLUDED.price_monthly,
    price_yearly = EXCLUDED.price_yearly,
    features = EXCLUDED.features;

-- 3. TÀI KHOẢN QUẢN TRỊ VIÊN HỆ THỐNG (admin@phongtroxanh.vn / Admin@123456)
DO $$
DECLARE
    v_admin_id UUID;
BEGIN
    SELECT id INTO v_admin_id FROM users WHERE email = 'admin@phongtroxanh.vn';
    
    IF v_admin_id IS NULL THEN
        INSERT INTO users (
            email,
            password_hash,
            phone_number,
            full_name,
            role,
            status,
            trust_score,
            is_verified,
            created_at,
            updated_at,
            last_active_at
        ) VALUES (
            'admin@phongtroxanh.vn',
            '$2a$10$BH1JeQibf83WHZjNWq0AzuSLICmYqjuB5fMFGp92IA6V2x0cvXZ.S',
            '0900000001',
            'Quản Trị Viên Hệ Thống',
            'ADMIN',
            'ACTIVE',
            100,
            true,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP
        ) RETURNING id INTO v_admin_id;
    ELSE
        UPDATE users SET
            password_hash = '$2a$10$BH1JeQibf83WHZjNWq0AzuSLICmYqjuB5fMFGp92IA6V2x0cvXZ.S',
            role = 'ADMIN',
            status = 'ACTIVE',
            is_verified = true
        WHERE id = v_admin_id;
    END IF;

    INSERT INTO user_profiles (user_id, bio, is_public, show_school, hide_active_status)
    VALUES (v_admin_id, 'Tài khoản quản trị hệ thống Phòng Trọ Xanh', true, false, false)
    ON CONFLICT (user_id) DO NOTHING;

    INSERT INTO user_consumables (user_id, swipes_left, free_swipes_left, boosts_left)
    VALUES (v_admin_id, 999, 999, 999)
    ON CONFLICT (user_id) DO NOTHING;
END $$;
