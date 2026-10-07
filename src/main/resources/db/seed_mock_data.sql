-- ==============================================================================
-- PHÒNG TRỌ XANH (PhongTroXanh.vn) - SEED MOCK DATA SCRIPT (ASIAN PERSONAS & ROOMS)
-- Mô tả: Khởi tạo dữ liệu mẫu cho Supabase / PostgreSQL:
--   1. 3 Chủ trọ (Landlord) người Châu Á có thông tin, SĐT, avatar riêng biệt không trùng lặp
--   2. 10 Phòng trọ trải dài khắp các quận TP.HCM kèm 4 ảnh chất lượng/phòng và biểu phí
--      (100% ảnh phòng trọ/studio/chung cư mini phong cách Châu Á/Việt Nam thực tế, không trùng lặp)
--   3. 10 Người thuê (Tenant) người Châu Á (Việt Nam/Đông Á):
--      - 7 người có nhu cầu tìm bạn ở cùng (is_public = TRUE, đầy đủ 8 trụ cột lối sống)
--      - 3 người chỉ thuê phòng ở riêng (is_public = FALSE, không xuất hiện ở tab tìm bạn)
-- Mật khẩu chung cho tất cả tài khoản test: Admin@123456
-- BCrypt Hash: $2a$10$BH1JeQibf83WHZjNWq0AzuSLICmYqjuB5fMFGp92IA6V2x0cvXZ.S
-- ==============================================================================

DO $$
DECLARE
    -- Landlord UUIDs
    v_landlord_1_id UUID := '11111111-1111-1111-1111-111111111101';
    v_landlord_2_id UUID := '11111111-1111-1111-1111-111111111102';
    v_landlord_3_id UUID := '11111111-1111-1111-1111-111111111103';

    -- Tenant UUIDs
    v_tenant_1_id  UUID := '22222222-2222-2222-2222-222222222201';
    v_tenant_2_id  UUID := '22222222-2222-2222-2222-222222222202';
    v_tenant_3_id  UUID := '22222222-2222-2222-2222-222222222203';
    v_tenant_4_id  UUID := '22222222-2222-2222-2222-222222222204';
    v_tenant_5_id  UUID := '22222222-2222-2222-2222-222222222205';
    v_tenant_6_id  UUID := '22222222-2222-2222-2222-222222222206';
    v_tenant_7_id  UUID := '22222222-2222-2222-2222-222222222207';
    v_tenant_8_id  UUID := '22222222-2222-2222-2222-222222222208';
    v_tenant_9_id  UUID := '22222222-2222-2222-2222-222222222209';
    v_tenant_10_id UUID := '22222222-2222-2222-2222-222222222210';

    -- Room UUIDs
    v_room_1_id  UUID := '33333333-3333-3333-3333-333333333301';
    v_room_2_id  UUID := '33333333-3333-3333-3333-333333333302';
    v_room_3_id  UUID := '33333333-3333-3333-3333-333333333303';
    v_room_4_id  UUID := '33333333-3333-3333-3333-333333333304';
    v_room_5_id  UUID := '33333333-3333-3333-3333-333333333305';
    v_room_6_id  UUID := '33333333-3333-3333-3333-333333333306';
    v_room_7_id  UUID := '33333333-3333-3333-3333-333333333307';
    v_room_8_id  UUID := '33333333-3333-3333-3333-333333333308';
    v_room_9_id  UUID := '33333333-3333-3333-3333-333333333309';
    v_room_10_id UUID := '33333333-3333-3333-3333-333333333310';

    -- Common BCrypt hash for Admin@123456
    v_password_hash VARCHAR(255) := '$2a$10$BH1JeQibf83WHZjNWq0AzuSLICmYqjuB5fMFGp92IA6V2x0cvXZ.S';
BEGIN

    -- -------------------------------------------------------------------------
    -- 1. TẠO 3 CHỦ TRỌ (LANDLORDS - NGƯỜI CHÂU Á THẬT)
    -- -------------------------------------------------------------------------

    -- Landlord 1: Bác Trần Văn Ba (Bình Thạnh & Thủ Đức & Gò Vấp) - Chân dung bác trai Châu Á lớn tuổi
    INSERT INTO users (id, email, password_hash, phone_number, full_name, role, status, trust_score, is_verified, avatar_url, created_at, updated_at)
    VALUES (
        v_landlord_1_id,
        'landlord.bacba@phongtroxanh.vn',
        v_password_hash,
        '0911000101',
        'Bác Trần Văn Ba',
        'LANDLORD',
        'ACTIVE',
        95,
        true,
        'https://images.unsplash.com/photo-1758600432264-b8d2a0fd7d83?auto=format&fit=crop&w=400&q=80',
        NOW(), NOW()
    ) ON CONFLICT (id) DO UPDATE SET
        email = EXCLUDED.email,
        phone_number = EXCLUDED.phone_number,
        full_name = EXCLUDED.full_name,
        avatar_url = EXCLUDED.avatar_url;

    INSERT INTO user_profiles (user_id, bio, is_public, show_school, hide_active_status)
    VALUES (v_landlord_1_id, 'Chủ trọ thân thiện, nhà trọ an ninh, có camera 24/7 và giờ giấc tự do.', true, false, false)
    ON CONFLICT (user_id) DO NOTHING;

    -- Landlord 2: Cô Nguyễn Thị Mai (Quận 10, Quận 3, Quận 1) - Chân dung cô phụ nữ Châu Á trung niên hiền hậu
    INSERT INTO users (id, email, password_hash, phone_number, full_name, role, status, trust_score, is_verified, avatar_url, created_at, updated_at)
    VALUES (
        v_landlord_2_id,
        'landlord.comai@phongtroxanh.vn',
        v_password_hash,
        '0911000102',
        'Cô Nguyễn Thị Mai',
        'LANDLORD',
        'ACTIVE',
        98,
        true,
        'https://images.unsplash.com/photo-1758600587683-d86675a2f6e9?auto=format&fit=crop&w=400&q=80',
        NOW(), NOW()
    ) ON CONFLICT (id) DO UPDATE SET
        email = EXCLUDED.email,
        phone_number = EXCLUDED.phone_number,
        full_name = EXCLUDED.full_name,
        avatar_url = EXCLUDED.avatar_url;

    INSERT INTO user_profiles (user_id, bio, is_public, show_school, hide_active_status)
    VALUES (v_landlord_2_id, 'Hệ thống căn hộ dịch vụ cao cấp, đầy đủ tiện nghi, ưu tiên người đi làm văn phòng và sinh viên văn minh.', true, false, false)
    ON CONFLICT (user_id) DO NOTHING;

    -- Landlord 3: Anh Lê Hoàng Nam (Quận 7, Tân Bình, Phú Nhuận, Quận 5) - Chân dung nam thanh niên Châu Á năng động
    INSERT INTO users (id, email, password_hash, phone_number, full_name, role, status, trust_score, is_verified, avatar_url, created_at, updated_at)
    VALUES (
        v_landlord_3_id,
        'landlord.anhnam@phongtroxanh.vn',
        v_password_hash,
        '0911000103',
        'Anh Lê Hoàng Nam',
        'LANDLORD',
        'ACTIVE',
        90,
        true,
        'https://plus.unsplash.com/premium_photo-1661594873452-20b925561e96?auto=format&fit=crop&w=400&q=80',
        NOW(), NOW()
    ) ON CONFLICT (id) DO UPDATE SET
        email = EXCLUDED.email,
        phone_number = EXCLUDED.phone_number,
        full_name = EXCLUDED.full_name,
        avatar_url = EXCLUDED.avatar_url;

    INSERT INTO user_profiles (user_id, bio, is_public, show_school, hide_active_status)
    VALUES (v_landlord_3_id, 'Quản lý chuỗi Sleepbox và Mini-condo trẻ trung, hiện đại tại TP.HCM.', true, false, false)
    ON CONFLICT (user_id) DO NOTHING;


    -- -------------------------------------------------------------------------
    -- 2. TẠO 10 NGƯỜI THUÊ (TENANTS - 100% CHÂN DUNG NGƯỜI CHÂU Á KHÔNG TRÙNG LẶP)
    -- -------------------------------------------------------------------------

    -- Tenant 1: Nguyễn Văn An (Tìm bạn ở cùng - ĐH Bách Khoa)
    INSERT INTO users (id, email, password_hash, phone_number, full_name, role, status, trust_score, is_verified, avatar_url, created_at, updated_at)
    VALUES (v_tenant_1_id, 'tenant.an@phongtroxanh.vn', v_password_hash, '0922000001', 'Nguyễn Văn An', 'TENANT', 'ACTIVE', 85, true, 'https://plus.unsplash.com/premium_photo-1682095413075-7c2df642fbb1?auto=format&fit=crop&w=400&q=80', NOW(), NOW())
    ON CONFLICT (id) DO UPDATE SET email = EXCLUDED.email, phone_number = EXCLUDED.phone_number, full_name = EXCLUDED.full_name, avatar_url = EXCLUDED.avatar_url;

    INSERT INTO user_profiles (user_id, school_or_company, birth_date, gender, bio, budget_min, budget_max, preferred_districts, sleep_schedule, cleanliness_level, guest_frequency, smoking_tolerance, pet_tolerance, noise_tolerance, allow_guests, early_sleeper, is_neat, non_smoking, interests, is_public, show_school, hide_active_status)
    VALUES (v_tenant_1_id, 'Đại học Bách Khoa TP.HCM', '2003-05-15', 'MALE', 'Sinh viên năm 3 ngành CNTT, thích yên tĩnh để học bài, sạch sẽ và ngăn nắp.', 2000000, 3500000, ARRAY['Quận 10', 'Quận 3', 'Tân Bình'], 'NIGHT_OWL', 5, 'RARELY', false, 'CAT_ONLY', 30, false, false, true, true, ARRAY['Công nghệ', 'Đọc sách', 'Chơi cờ', 'Âm nhạc'], true, true, false)
    ON CONFLICT (user_id) DO UPDATE SET bio = EXCLUDED.bio, is_public = EXCLUDED.is_public;

    INSERT INTO user_consumables (user_id, swipes_left, free_swipes_left, boosts_left)
    VALUES (v_tenant_1_id, 15, 15, 0) ON CONFLICT (user_id) DO NOTHING;

    -- Tenant 2: Trần Thị Bích (Tìm bạn ở cùng - ĐH Kinh Tế UEH)
    INSERT INTO users (id, email, password_hash, phone_number, full_name, role, status, trust_score, is_verified, avatar_url, created_at, updated_at)
    VALUES (v_tenant_2_id, 'tenant.bich@phongtroxanh.vn', v_password_hash, '0922000002', 'Trần Thị Bích', 'TENANT', 'ACTIVE', 88, true, 'https://images.unsplash.com/photo-1782357091589-a3382cabc067?auto=format&fit=crop&w=400&q=80', NOW(), NOW())
    ON CONFLICT (id) DO UPDATE SET email = EXCLUDED.email, phone_number = EXCLUDED.phone_number, full_name = EXCLUDED.full_name, avatar_url = EXCLUDED.avatar_url;

    INSERT INTO user_profiles (user_id, school_or_company, birth_date, gender, bio, budget_min, budget_max, preferred_districts, sleep_schedule, cleanliness_level, guest_frequency, smoking_tolerance, pet_tolerance, noise_tolerance, allow_guests, early_sleeper, is_neat, non_smoking, interests, is_public, show_school, hide_active_status)
    VALUES (v_tenant_2_id, 'Đại học Kinh Tế TP.HCM (UEH)', '2004-02-20', 'FEMALE', 'Nữ sinh viên UEH thích nấu ăn, hòa đồng, mong tìm bạn nữ cùng phòng vui vẻ, tôn trọng không gian riêng.', 2500000, 4000000, ARRAY['Quận 1', 'Quận 3', 'Quận 10'], 'EARLY_BIRD', 4, 'OCCASIONALLY', false, 'NO_PETS', 40, true, true, true, true, ARRAY['Nấu ăn', 'Yoga', 'Du lịch', 'Thời trang'], true, true, false)
    ON CONFLICT (user_id) DO UPDATE SET bio = EXCLUDED.bio, is_public = EXCLUDED.is_public;

    INSERT INTO user_consumables (user_id, swipes_left, free_swipes_left, boosts_left)
    VALUES (v_tenant_2_id, 15, 15, 0) ON CONFLICT (user_id) DO NOTHING;

    -- Tenant 3: Lê Hoàng Cường (Tìm bạn ở cùng - Graphic Designer)
    INSERT INTO users (id, email, password_hash, phone_number, full_name, role, status, trust_score, is_verified, avatar_url, created_at, updated_at)
    VALUES (v_tenant_3_id, 'tenant.cuong@phongtroxanh.vn', v_password_hash, '0922000003', 'Lê Hoàng Cường', 'TENANT', 'ACTIVE', 80, true, 'https://images.unsplash.com/photo-1653146886059-06f022c6edb5?auto=format&fit=crop&w=400&q=80', NOW(), NOW())
    ON CONFLICT (id) DO UPDATE SET email = EXCLUDED.email, phone_number = EXCLUDED.phone_number, full_name = EXCLUDED.full_name, avatar_url = EXCLUDED.avatar_url;

    INSERT INTO user_profiles (user_id, school_or_company, birth_date, gender, bio, budget_min, budget_max, preferred_districts, sleep_schedule, cleanliness_level, guest_frequency, smoking_tolerance, pet_tolerance, noise_tolerance, allow_guests, early_sleeper, is_neat, non_smoking, interests, is_public, show_school, hide_active_status)
    VALUES (v_tenant_3_id, 'FPT Software', '2001-08-10', 'MALE', 'Designer năng động, thích không gian sáng tạo, hay làm việc đêm nhưng không làm ồn.', 3000000, 5000000, ARRAY['Bình Thạnh', 'Phú Nhuận', 'Quận 1'], 'NIGHT_OWL', 4, 'OCCASIONALLY', false, 'DOG_ONLY', 50, true, false, true, true, ARRAY['Thiết kế', 'Nhiếp ảnh', 'Coffee hopping', 'Game'], true, true, false)
    ON CONFLICT (user_id) DO UPDATE SET bio = EXCLUDED.bio, is_public = EXCLUDED.is_public;

    INSERT INTO user_consumables (user_id, swipes_left, free_swipes_left, boosts_left)
    VALUES (v_tenant_3_id, 15, 15, 0) ON CONFLICT (user_id) DO NOTHING;

    -- Tenant 4: Phạm Thị Dung (Tìm bạn ở cùng - ĐH Y Dược)
    INSERT INTO users (id, email, password_hash, phone_number, full_name, role, status, trust_score, is_verified, avatar_url, created_at, updated_at)
    VALUES (v_tenant_4_id, 'tenant.dung@phongtroxanh.vn', v_password_hash, '0922000004', 'Phạm Thị Dung', 'TENANT', 'ACTIVE', 92, true, 'https://images.unsplash.com/photo-1775119856946-97154eead3fd?auto=format&fit=crop&w=400&q=80', NOW(), NOW())
    ON CONFLICT (id) DO UPDATE SET email = EXCLUDED.email, phone_number = EXCLUDED.phone_number, full_name = EXCLUDED.full_name, avatar_url = EXCLUDED.avatar_url;

    INSERT INTO user_profiles (user_id, school_or_company, birth_date, gender, bio, budget_min, budget_max, preferred_districts, sleep_schedule, cleanliness_level, guest_frequency, smoking_tolerance, pet_tolerance, noise_tolerance, allow_guests, early_sleeper, is_neat, non_smoking, interests, is_public, show_school, hide_active_status)
    VALUES (v_tenant_4_id, 'Đại học Y Dược TP.HCM', '2002-11-05', 'FEMALE', 'Sinh viên Y năm cuối, cần phòng trọ yên tĩnh tuyệt đối để tập trung học và đi trực bệnh viện.', 2500000, 4500000, ARRAY['Quận 5', 'Quận 10', 'Quận 11'], 'FLEXIBLE', 5, 'NEVER', false, 'NO_PETS', 20, false, false, true, true, ARRAY['Y học', 'Chạy bộ', 'Cắm hoa', 'Đọc sách'], true, true, false)
    ON CONFLICT (user_id) DO UPDATE SET bio = EXCLUDED.bio, is_public = EXCLUDED.is_public;

    INSERT INTO user_consumables (user_id, swipes_left, free_swipes_left, boosts_left)
    VALUES (v_tenant_4_id, 15, 15, 0) ON CONFLICT (user_id) DO NOTHING;

    -- Tenant 5: Đỗ Minh Đức (Tìm bạn ở cùng - RMIT / Marketing)
    INSERT INTO users (id, email, password_hash, phone_number, full_name, role, status, trust_score, is_verified, avatar_url, created_at, updated_at)
    VALUES (v_tenant_5_id, 'tenant.duc@phongtroxanh.vn', v_password_hash, '0922000005', 'Đỗ Minh Đức', 'TENANT', 'ACTIVE', 86, true, 'https://plus.unsplash.com/premium_photo-1681494529298-a094faff3f99?auto=format&fit=crop&w=400&q=80', NOW(), NOW())
    ON CONFLICT (id) DO UPDATE SET email = EXCLUDED.email, phone_number = EXCLUDED.phone_number, full_name = EXCLUDED.full_name, avatar_url = EXCLUDED.avatar_url;

    INSERT INTO user_profiles (user_id, school_or_company, birth_date, gender, bio, budget_min, budget_max, preferred_districts, sleep_schedule, cleanliness_level, guest_frequency, smoking_tolerance, pet_tolerance, noise_tolerance, allow_guests, early_sleeper, is_neat, non_smoking, interests, is_public, show_school, hide_active_status)
    VALUES (v_tenant_5_id, 'Đại học RMIT Nam Sài Gòn', '2003-09-12', 'MALE', 'Sinh viên RMIT tìm bạn share căn hộ Sunrise City / Eco Green Quận 7, lối sống hiện đại, thể thao.', 4000000, 7000000, ARRAY['Quận 7', 'Quận 4', 'Nhà Bè'], 'FLEXIBLE', 4, 'OCCASIONALLY', false, 'ANY_PETS', 50, true, false, true, true, ARRAY['Gym', 'Bóng rổ', 'Podcasts', 'Startup'], true, true, false)
    ON CONFLICT (user_id) DO UPDATE SET bio = EXCLUDED.bio, is_public = EXCLUDED.is_public;

    INSERT INTO user_consumables (user_id, swipes_left, free_swipes_left, boosts_left)
    VALUES (v_tenant_5_id, 15, 15, 0) ON CONFLICT (user_id) DO NOTHING;

    -- Tenant 6: Hoàng Thu Hà (Tìm bạn ở cùng - ĐH Khoa Học Xã Hội & Nhân Văn)
    INSERT INTO users (id, email, password_hash, phone_number, full_name, role, status, trust_score, is_verified, avatar_url, created_at, updated_at)
    VALUES (v_tenant_6_id, 'tenant.ha@phongtroxanh.vn', v_password_hash, '0922000006', 'Hoàng Thu Hà', 'TENANT', 'ACTIVE', 89, true, 'https://images.unsplash.com/photo-1784052297821-bbf8d6b6bb55?auto=format&fit=crop&w=400&q=80', NOW(), NOW())
    ON CONFLICT (id) DO UPDATE SET email = EXCLUDED.email, phone_number = EXCLUDED.phone_number, full_name = EXCLUDED.full_name, avatar_url = EXCLUDED.avatar_url;

    INSERT INTO user_profiles (user_id, school_or_company, birth_date, gender, bio, budget_min, budget_max, preferred_districts, sleep_schedule, cleanliness_level, guest_frequency, smoking_tolerance, pet_tolerance, noise_tolerance, allow_guests, early_sleeper, is_neat, non_smoking, interests, is_public, show_school, hide_active_status)
    VALUES (v_tenant_6_id, 'ĐH KHXH&NV TP.HCM', '2004-07-25', 'FEMALE', 'Tính tình hiền hòa, yêu thiên nhiên và cây cảnh. Tìm bạn nữ ở ghép khu vực Bình Thạnh / Quận 1.', 2000000, 3500000, ARRAY['Bình Thạnh', 'Quận 1', 'Phú Nhuận'], 'EARLY_BIRD', 5, 'RARELY', false, 'CAT_ONLY', 30, false, true, true, true, ARRAY['Trồng cây', 'Học ngoại ngữ', 'Viết lách', 'Trà chiều'], true, true, false)
    ON CONFLICT (user_id) DO UPDATE SET bio = EXCLUDED.bio, is_public = EXCLUDED.is_public;

    INSERT INTO user_consumables (user_id, swipes_left, free_swipes_left, boosts_left)
    VALUES (v_tenant_6_id, 15, 15, 0) ON CONFLICT (user_id) DO NOTHING;

    -- Tenant 7: Vũ Quốc Huy (Tìm bạn ở cùng - Kỹ sư AI / ĐH Bách Khoa)
    INSERT INTO users (id, email, password_hash, phone_number, full_name, role, status, trust_score, is_verified, avatar_url, created_at, updated_at)
    VALUES (v_tenant_7_id, 'tenant.huy@phongtroxanh.vn', v_password_hash, '0922000007', 'Vũ Quốc Huy', 'TENANT', 'ACTIVE', 87, true, 'https://plus.unsplash.com/premium_photo-1661754734117-7ac69adbe776?auto=format&fit=crop&w=400&q=80', NOW(), NOW())
    ON CONFLICT (id) DO UPDATE SET email = EXCLUDED.email, phone_number = EXCLUDED.phone_number, full_name = EXCLUDED.full_name, avatar_url = EXCLUDED.avatar_url;

    INSERT INTO user_profiles (user_id, school_or_company, birth_date, gender, bio, budget_min, budget_max, preferred_districts, sleep_schedule, cleanliness_level, guest_frequency, smoking_tolerance, pet_tolerance, noise_tolerance, allow_guests, early_sleeper, is_neat, non_smoking, interests, is_public, show_school, hide_active_status)
    VALUES (v_tenant_7_id, 'Zalo AI Lab', '2000-03-18', 'MALE', 'Làm việc trong mảng trí tuệ nhân tạo, lịch trình khoa học, thích tập gym mỗi sáng.', 3500000, 6000000, ARRAY['Quận 10', 'Quận 3', 'Quận 7'], 'EARLY_BIRD', 5, 'RARELY', false, 'NO_PETS', 30, false, true, true, true, ARRAY['AI', 'Thể thao', 'Sách khoa học', 'Bơi lội'], true, true, false)
    ON CONFLICT (user_id) DO UPDATE SET bio = EXCLUDED.bio, is_public = EXCLUDED.is_public;

    INSERT INTO user_consumables (user_id, swipes_left, free_swipes_left, boosts_left)
    VALUES (v_tenant_7_id, 15, 15, 0) ON CONFLICT (user_id) DO NOTHING;

    -- Tenant 8: Bùi Khánh Linh (CHỈ THUÊ PHÒNG Ở RIÊNG - is_public = FALSE)
    INSERT INTO users (id, email, password_hash, phone_number, full_name, role, status, trust_score, is_verified, avatar_url, created_at, updated_at)
    VALUES (v_tenant_8_id, 'tenant.linh@phongtroxanh.vn', v_password_hash, '0922000008', 'Bùi Khánh Linh', 'TENANT', 'ACTIVE', 91, true, 'https://images.unsplash.com/photo-1769961982389-bb243681421a?auto=format&fit=crop&w=400&q=80', NOW(), NOW())
    ON CONFLICT (id) DO UPDATE SET email = EXCLUDED.email, phone_number = EXCLUDED.phone_number, full_name = EXCLUDED.full_name, avatar_url = EXCLUDED.avatar_url;

    INSERT INTO user_profiles (user_id, school_or_company, birth_date, gender, bio, budget_min, budget_max, preferred_districts, sleep_schedule, cleanliness_level, guest_frequency, smoking_tolerance, pet_tolerance, noise_tolerance, allow_guests, early_sleeper, is_neat, non_smoking, interests, is_public, show_school, hide_active_status)
    VALUES (v_tenant_8_id, 'Ngân hàng Vietcombank', '1999-10-30', 'FEMALE', 'Chuyên viên tài chính ngân hàng, tìm studio hoặc căn hộ 1 phòng ngủ khép kín ở riêng.', 5000000, 8000000, ARRAY['Quận 1', 'Bình Thạnh', 'Quận 3'], 'EARLY_BIRD', 5, 'NEVER', false, 'NO_PETS', 10, false, true, true, true, ARRAY['Tài chính', 'Pilates', 'Du lịch nghỉ dưỡng'], false, true, false)
    ON CONFLICT (user_id) DO UPDATE SET bio = EXCLUDED.bio, is_public = EXCLUDED.is_public;

    INSERT INTO user_consumables (user_id, swipes_left, free_swipes_left, boosts_left)
    VALUES (v_tenant_8_id, 15, 15, 0) ON CONFLICT (user_id) DO NOTHING;

    -- Tenant 9: Đinh Tuấn Kiệt (CHỈ THUÊ PHÒNG Ở RIÊNG - is_public = FALSE)
    INSERT INTO users (id, email, password_hash, phone_number, full_name, role, status, trust_score, is_verified, avatar_url, created_at, updated_at)
    VALUES (v_tenant_9_id, 'tenant.kiet@phongtroxanh.vn', v_password_hash, '0922000009', 'Đinh Tuấn Kiệt', 'TENANT', 'ACTIVE', 84, true, 'https://images.unsplash.com/photo-1773899337978-b8d83bd9b783?auto=format&fit=crop&w=400&q=80', NOW(), NOW())
    ON CONFLICT (id) DO UPDATE SET email = EXCLUDED.email, phone_number = EXCLUDED.phone_number, full_name = EXCLUDED.full_name, avatar_url = EXCLUDED.avatar_url;

    INSERT INTO user_profiles (user_id, school_or_company, birth_date, gender, bio, budget_min, budget_max, preferred_districts, sleep_schedule, cleanliness_level, guest_frequency, smoking_tolerance, pet_tolerance, noise_tolerance, allow_guests, early_sleeper, is_neat, non_smoking, interests, is_public, show_school, hide_active_status)
    VALUES (v_tenant_9_id, 'Shopee Vietnam', '1998-12-14', 'MALE', 'Quản lý vận hành eCommerce, cần phòng trọ yên tĩnh riêng tư để làm việc remote kết hợp lên công ty.', 4500000, 7500000, ARRAY['Quận 7', 'Quận 4', 'Quận 1'], 'FLEXIBLE', 4, 'RARELY', false, 'NO_PETS', 20, false, false, true, true, ARRAY['Thương mại điện tử', 'Chạy bộ', 'Cà phê'], false, true, false)
    ON CONFLICT (user_id) DO UPDATE SET bio = EXCLUDED.bio, is_public = EXCLUDED.is_public;

    INSERT INTO user_consumables (user_id, swipes_left, free_swipes_left, boosts_left)
    VALUES (v_tenant_9_id, 15, 15, 0) ON CONFLICT (user_id) DO NOTHING;

    -- Tenant 10: Trịnh Mai Phương (CHỈ THUÊ PHÒNG Ở RIÊNG - is_public = FALSE)
    INSERT INTO users (id, email, password_hash, phone_number, full_name, role, status, trust_score, is_verified, avatar_url, created_at, updated_at)
    VALUES (v_tenant_10_id, 'tenant.phuong@phongtroxanh.vn', v_password_hash, '0922000010', 'Trịnh Mai Phương', 'TENANT', 'ACTIVE', 86, true, 'https://images.unsplash.com/photo-1761933808230-9a2e78956daa?auto=format&fit=crop&w=400&q=80', NOW(), NOW())
    ON CONFLICT (id) DO UPDATE SET email = EXCLUDED.email, phone_number = EXCLUDED.phone_number, full_name = EXCLUDED.full_name, avatar_url = EXCLUDED.avatar_url;

    INSERT INTO user_profiles (user_id, school_or_company, birth_date, gender, bio, budget_min, budget_max, preferred_districts, sleep_schedule, cleanliness_level, guest_frequency, smoking_tolerance, pet_tolerance, noise_tolerance, allow_guests, early_sleeper, is_neat, non_smoking, interests, is_public, show_school, hide_active_status)
    VALUES (v_tenant_10_id, 'VNG Corporation', '2001-04-09', 'FEMALE', 'Product Specialist tại VNG Campus, cần thuê chung cư mini 1 phòng ngủ độc lập.', 5000000, 8500000, ARRAY['Quận 7', 'Nhà Bè', 'Quận 4'], 'EARLY_BIRD', 5, 'NEVER', false, 'CAT_ONLY', 15, false, true, true, true, ARRAY['Công nghệ', 'Bơi lội', 'Điện ảnh'], false, true, false)
    ON CONFLICT (user_id) DO UPDATE SET bio = EXCLUDED.bio, is_public = EXCLUDED.is_public;

    INSERT INTO user_consumables (user_id, swipes_left, free_swipes_left, boosts_left)
    VALUES (v_tenant_10_id, 15, 15, 0) ON CONFLICT (user_id) DO NOTHING;


    -- -------------------------------------------------------------------------
    -- 3. TẠO 10 PHÒNG TRỌ MẪU (10 ROOMS + 40 ẢNH PHÒNG CHÂU Á KHÔNG TRÙNG LẶP)
    -- -------------------------------------------------------------------------

    -- PHÒNG 1: Studio Gác Lửng Hiện Đại - Bình Thạnh (Chủ: Bác Ba)
    INSERT INTO rooms (id, landlord_id, title, description, room_type, price, deposit_amount, area_sqm, floor_number, max_occupants, address_street, district, city, latitude, longitude, location, status, is_verified, amenities, expires_at)
    VALUES (
        v_room_1_id,
        v_landlord_1_id,
        'Studio Gác Lửng Cao Cấp - Gần ĐH HUTECH & Ngoại Thương CS2',
        'Phòng trọ mới xây 100%, gác lửng đúc kiên cố cao 1m8 không đụng đầu. Cửa khóa vân tay, camera 24/7, giờ giấc tự do không chung chủ. Ban công thoáng mát, view Landmark 81 cực đẹp về đêm.',
        'PHONG_KHEP_KIN',
        4200000, 4200000, 28.0, 3, 2,
        '125/42 Đường D2 (Nguyễn Gia Trí)', 'Bình Thạnh', 'TP.HCM',
        10.8031, 106.7144, ST_SetSRID(ST_MakePoint(106.7144, 10.8031), 4326),
        'AVAILABLE', true,
        ARRAY['WIFI', 'AIR_CONDITIONER', 'WATER_HEATER', 'MEZZANINE', 'FINGERPRINT_LOCK', 'BALCONY', 'CAMERA', 'FREE_HOURS', 'PRIVATE_BATHROOM'],
        NOW() + INTERVAL '60 days'
    ) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, price = EXCLUDED.price, status = EXCLUDED.status;

    DELETE FROM room_images WHERE room_id = v_room_1_id;
    INSERT INTO room_images (room_id, image_url, is_primary, display_order) VALUES
        (v_room_1_id, 'https://images.unsplash.com/photo-1522708323590-d24dbb6b0267?auto=format&fit=crop&w=1000&q=80', true, 0),
        (v_room_1_id, 'https://images.unsplash.com/photo-1502672260266-1c1ef2d93688?auto=format&fit=crop&w=1000&q=80', false, 1),
        (v_room_1_id, 'https://images.unsplash.com/photo-1560448204-e02f11c3d0e2?auto=format&fit=crop&w=1000&q=80', false, 2),
        (v_room_1_id, 'https://images.unsplash.com/photo-1584622650111-993a426fbf0a?auto=format&fit=crop&w=1000&q=80', false, 3);

    DELETE FROM room_fees WHERE room_id = v_room_1_id;
    INSERT INTO room_fees (room_id, fee_label, fee_value) VALUES
        (v_room_1_id, 'Điện', '3.800 đ/kWh'),
        (v_room_1_id, 'Nước máy', '100.000 đ/người'),
        (v_room_1_id, 'Internet wifi', '100.000 đ/phòng'),
        (v_room_1_id, 'Phí giữ xe', 'Miễn phí');

    -- PHÒNG 2: Chung cư mini Ban công đón nắng - Quận 10 (Chủ: Cô Mai)
    INSERT INTO rooms (id, landlord_id, title, description, room_type, price, deposit_amount, area_sqm, floor_number, max_occupants, address_street, district, city, latitude, longitude, location, status, is_verified, amenities, expires_at)
    VALUES (
        v_room_2_id,
        v_landlord_2_id,
        'Căn Hộ Mini Ban Công Riêng - Liền Kề ĐH Bách Khoa & Vạn Hạnh Mall',
        'Phòng khép kín đầy đủ tiện nghi: máy lạnh Inverter, tủ lạnh 150L, máy giặt riêng, bếp từ âm. Khu dân trí cao, yên tĩnh, hẻm xe hơi thông ra đường Tô Hiến Thành và 3 Tháng 2.',
        'CHUNG_CU_MINI',
        5200000, 5200000, 32.0, 2, 2,
        '284/15 Lý Thường Kiệt, Phường 14', 'Quận 10', 'TP.HCM',
        10.7725, 106.6578, ST_SetSRID(ST_MakePoint(106.6578, 10.7725), 4326),
        'AVAILABLE', true,
        ARRAY['WIFI', 'AIR_CONDITIONER', 'REFRIGERATOR', 'WASHING_MACHINE', 'BALCONY', 'KITCHEN', 'ELEVATOR', 'PRIVATE_BATHROOM'],
        NOW() + INTERVAL '60 days'
    ) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, price = EXCLUDED.price, status = EXCLUDED.status;

    DELETE FROM room_images WHERE room_id = v_room_2_id;
    INSERT INTO room_images (room_id, image_url, is_primary, display_order) VALUES
        (v_room_2_id, 'https://images.unsplash.com/photo-1554995207-c18c203602cb?auto=format&fit=crop&w=1000&q=80', true, 0),
        (v_room_2_id, 'https://images.unsplash.com/photo-1513694203232-719a280e022f?auto=format&fit=crop&w=1000&q=80', false, 1),
        (v_room_2_id, 'https://images.unsplash.com/photo-1493809842364-78817add7ffb?auto=format&fit=crop&w=1000&q=80', false, 2),
        (v_room_2_id, 'https://images.unsplash.com/photo-1583847268964-b28dc8f51f92?auto=format&fit=crop&w=1000&q=80', false, 3);

    DELETE FROM room_fees WHERE room_id = v_room_2_id;
    INSERT INTO room_fees (room_id, fee_label, fee_value) VALUES
        (v_room_2_id, 'Điện', '4.000 đ/kWh'),
        (v_room_2_id, 'Nước máy', '20.000 đ/m3'),
        (v_room_2_id, 'Internet', 'Miễn phí'),
        (v_room_2_id, 'Dịch vụ & Thang máy', '150.000 đ/phòng');

    -- PHÒNG 3: Căn Hộ Dịch Vụ View Phú Mỹ Hưng - Quận 7 (Chủ: Anh Nam)
    INSERT INTO rooms (id, landlord_id, title, description, room_type, price, deposit_amount, area_sqm, floor_number, max_occupants, address_street, district, city, latitude, longitude, location, status, is_verified, amenities, expires_at)
    VALUES (
        v_room_3_id,
        v_landlord_3_id,
        'Căn Hộ Dịch Vụ Full Nội Thất - Cách ĐH Tôn Đức Thắng & RMIT 5 Phút',
        'Căn hộ dịch vụ cao cấp chuẩn khách sạn, có dọn phòng tuần 1 lần. Trang bị Smart TV 55 inch, nệm lò xo túi Dunlopillo, hệ thống bếp cao cấp. Thích hợp cho chuyên gia và sinh viên quốc tế.',
        'CHUNG_CU_MINI',
        6500000, 6500000, 38.0, 4, 2,
        '65 Đường số 79, Phường Tân Quy', 'Quận 7', 'TP.HCM',
        10.7428, 106.7082, ST_SetSRID(ST_MakePoint(106.7082, 10.7428), 4326),
        'AVAILABLE', true,
        ARRAY['WIFI', 'AIR_CONDITIONER', 'TELEVISION', 'REFRIGERATOR', 'WASHING_MACHINE', 'KITCHEN', 'ELEVATOR', 'SECURITY_24_7', 'CLEANING_SERVICE'],
        NOW() + INTERVAL '60 days'
    ) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, price = EXCLUDED.price, status = EXCLUDED.status;

    DELETE FROM room_images WHERE room_id = v_room_3_id;
    INSERT INTO room_images (room_id, image_url, is_primary, display_order) VALUES
        (v_room_3_id, 'https://images.unsplash.com/photo-1484154218962-a197022b5858?auto=format&fit=crop&w=1000&q=80', true, 0),
        (v_room_3_id, 'https://images.unsplash.com/photo-1507089947368-19c1da9775ae?auto=format&fit=crop&w=1000&q=80', false, 1),
        (v_room_3_id, 'https://images.unsplash.com/photo-1512917774080-9991f1c4c750?auto=format&fit=crop&w=1000&q=80', false, 2),
        (v_room_3_id, 'https://images.unsplash.com/photo-1616486338812-3dadae4b4ace?auto=format&fit=crop&w=1000&q=80', false, 3);

    DELETE FROM room_fees WHERE room_id = v_room_3_id;
    INSERT INTO room_fees (room_id, fee_label, fee_value) VALUES
        (v_room_3_id, 'Điện', '4.200 đ/kWh'),
        (v_room_3_id, 'Nước máy', '120.000 đ/người'),
        (v_room_3_id, 'Phí dịch vụ', '200.000 đ/tháng (bao gồm Wifi, rác, dọn phòng)');

    -- PHÒNG 4: Phòng Khép Kín Trung Tâm - Quận 1 (Chủ: Cô Mai)
    INSERT INTO rooms (id, landlord_id, title, description, room_type, price, deposit_amount, area_sqm, floor_number, max_occupants, address_street, district, city, latitude, longitude, location, status, is_verified, amenities, expires_at)
    VALUES (
        v_room_4_id,
        v_landlord_2_id,
        'Phòng Khép Kín Cao Cấp Trung Tâm Quận 1 - Gần Phố Đi Bộ & ĐH KHXH&NV',
        'Vị trí đắc địa ngay trung tâm Sài Gòn, đi bộ ra chợ Bến Thành và công viên 23/9. Phòng có cửa sổ lớn đón gió tự nhiên, nội thất gỗ ấm cúng, an ninh tuyệt đối.',
        'PHONG_KHEP_KIN',
        5800000, 5800000, 26.0, 2, 2,
        '18A/42 Nguyễn Thị Minh Khai, Phường Đa Kao', 'Quận 1', 'TP.HCM',
        10.7876, 106.6998, ST_SetSRID(ST_MakePoint(106.6998, 10.7876), 4326),
        'AVAILABLE', true,
        ARRAY['WIFI', 'AIR_CONDITIONER', 'WATER_HEATER', 'BED', 'WARDROBE', 'WINDOW', 'FINGERPRINT_LOCK'],
        NOW() + INTERVAL '60 days'
    ) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, price = EXCLUDED.price, status = EXCLUDED.status;

    DELETE FROM room_images WHERE room_id = v_room_4_id;
    INSERT INTO room_images (room_id, image_url, is_primary, display_order) VALUES
        (v_room_4_id, 'https://images.unsplash.com/photo-1598928506311-c55ded91a20c?auto=format&fit=crop&w=1000&q=80', true, 0),
        (v_room_4_id, 'https://images.unsplash.com/photo-1505691938895-1758d7feb511?auto=format&fit=crop&w=1000&q=80', false, 1),
        (v_room_4_id, 'https://images.unsplash.com/photo-1618221195710-dd6b41faaea6?auto=format&fit=crop&w=1000&q=80', false, 2),
        (v_room_4_id, 'https://images.unsplash.com/photo-1618219908412-a29a1bb7b86e?auto=format&fit=crop&w=1000&q=80', false, 3);

    DELETE FROM room_fees WHERE room_id = v_room_4_id;
    INSERT INTO room_fees (room_id, fee_label, fee_value) VALUES
        (v_room_4_id, 'Điện', '4.000 đ/kWh'),
        (v_room_4_id, 'Nước máy', '120.000 đ/người'),
        (v_room_4_id, 'Internet', '100.000 đ/phòng');

    -- PHÒNG 5: Căn Hộ Vintage Phong Cách Bắc Âu - Quận 3 (Chủ: Cô Mai)
    INSERT INTO rooms (id, landlord_id, title, description, room_type, price, deposit_amount, area_sqm, floor_number, max_occupants, address_street, district, city, latitude, longitude, location, status, is_verified, amenities, expires_at)
    VALUES (
        v_room_5_id,
        v_landlord_2_id,
        'Căn Hộ Vintage Scandinavian - Góc Võ Văn Tần & Nam Kỳ Khởi Nghĩa',
        'Phòng thiết kế phong cách trang nhã, ban công ngập tràn ánh nắng và cây xanh. Trang bị đầy đủ bếp nấu, lò vi sóng, tủ lạnh hai cánh. Giờ giấc tự do, khóa thông minh.',
        'CHUNG_CU_MINI',
        6000000, 6000000, 35.0, 3, 2,
        '212/8 Võ Văn Tần, Phường 5', 'Quận 3', 'TP.HCM',
        10.7741, 106.6872, ST_SetSRID(ST_MakePoint(106.6872, 10.7741), 4326),
        'AVAILABLE', true,
        ARRAY['WIFI', 'AIR_CONDITIONER', 'REFRIGERATOR', 'MICROWAVE', 'BALCONY', 'KITCHEN', 'FREE_HOURS'],
        NOW() + INTERVAL '60 days'
    ) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, price = EXCLUDED.price, status = EXCLUDED.status;

    DELETE FROM room_images WHERE room_id = v_room_5_id;
    INSERT INTO room_images (room_id, image_url, is_primary, display_order) VALUES
        (v_room_5_id, 'https://images.unsplash.com/photo-1536376072261-38c75010e6c9?auto=format&fit=crop&w=1000&q=80', true, 0),
        (v_room_5_id, 'https://images.unsplash.com/photo-1505693416388-ac5ce068fe85?auto=format&fit=crop&w=1000&q=80', false, 1),
        (v_room_5_id, 'https://images.unsplash.com/photo-1567496898669-ee935f5f647a?auto=format&fit=crop&w=1000&q=80', false, 2),
        (v_room_5_id, 'https://images.unsplash.com/photo-1586023492125-27b2c045efd7?auto=format&fit=crop&w=1000&q=80', false, 3);

    DELETE FROM room_fees WHERE room_id = v_room_5_id;
    INSERT INTO room_fees (room_id, fee_label, fee_value) VALUES
        (v_room_5_id, 'Điện', '3.900 đ/kWh'),
        (v_room_5_id, 'Nước máy', '100.000 đ/người'),
        (v_room_5_id, 'Internet', 'Miễn phí');

    -- PHÒNG 6: Phòng Trọ Cao Cấp - Phú Nhuận (Chủ: Anh Nam)
    INSERT INTO rooms (id, landlord_id, title, description, room_type, price, deposit_amount, area_sqm, floor_number, max_occupants, address_street, district, city, latitude, longitude, location, status, is_verified, amenities, expires_at)
    VALUES (
        v_room_6_id,
        v_landlord_3_id,
        'Phòng Trọ Ban Công Lớn - Phan Xích Long, Phú Nhuận (Gần Sân Bay)',
        'Ngay trung tâm ẩm thực Phan Xích Long, thuận tiện di chuyển sang Quận 1, Tân Bình, Gò Vấp. Tòa nhà có thang máy, máy giặt sấy chung trên sân thượng, bãi xe rộng rãi.',
        'PHONG_TRO',
        3800000, 3800000, 24.0, 3, 2,
        '88/12 Hoa Lan, Phường 2', 'Phú Nhuận', 'TP.HCM',
        10.7963, 106.6912, ST_SetSRID(ST_MakePoint(106.6912, 10.7963), 4326),
        'AVAILABLE', true,
        ARRAY['WIFI', 'AIR_CONDITIONER', 'WATER_HEATER', 'BALCONY', 'ELEVATOR', 'PARKING', 'CAMERA'],
        NOW() + INTERVAL '60 days'
    ) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, price = EXCLUDED.price, status = EXCLUDED.status;

    DELETE FROM room_images WHERE room_id = v_room_6_id;
    INSERT INTO room_images (room_id, image_url, is_primary, display_order) VALUES
        (v_room_6_id, 'https://images.unsplash.com/photo-1512918728675-ed5a9ecdebfd?auto=format&fit=crop&w=1000&q=80', true, 0),
        (v_room_6_id, 'https://images.unsplash.com/photo-1522771739844-6a9f6d5f14af?auto=format&fit=crop&w=1000&q=80', false, 1),
        (v_room_6_id, 'https://images.unsplash.com/photo-1598928636135-d146006ff4be?auto=format&fit=crop&w=1000&q=80', false, 2),
        (v_room_6_id, 'https://images.unsplash.com/photo-1540518614846-7ede433c4ef4?auto=format&fit=crop&w=1000&q=80', false, 3);

    DELETE FROM room_fees WHERE room_id = v_room_6_id;
    INSERT INTO room_fees (room_id, fee_label, fee_value) VALUES
        (v_room_6_id, 'Điện', '3.800 đ/kWh'),
        (v_room_6_id, 'Nước máy', '80.000 đ/người'),
        (v_room_6_id, 'Xe máy', '120.000 đ/chiếc');

    -- PHÒNG 7: Kén Ngủ Sleepbox Sang Xịn - Tân Bình (Chủ: Anh Nam)
    INSERT INTO rooms (id, landlord_id, title, description, room_type, price, deposit_amount, area_sqm, floor_number, max_occupants, address_street, district, city, latitude, longitude, location, status, is_verified, amenities, expires_at)
    VALUES (
        v_room_7_id,
        v_landlord_3_id,
        'KTX Kén Ngủ Sleepbox Riêng Tư - Gần CV Hoàng Văn Thụ & Sân Bay',
        'Mô hình Sleepbox riêng tư có cửa khóa riêng từng box, điều hòa trung tâm mát lạnh 24/24. Bao trọn gói tiền điện nước, internet tốc độ cao, tủ để đồ cá nhân rộng rãi.',
        'KTX_SLEEPBOX',
        1800000, 1800000, 8.0, 2, 1,
        '45/6 Bạch Đằng, Phường 2', 'Tân Bình', 'TP.HCM',
        10.8122, 106.6698, ST_SetSRID(ST_MakePoint(106.6698, 10.8122), 4326),
        'AVAILABLE', true,
        ARRAY['WIFI', 'AIR_CONDITIONER', 'LOCKER', 'SHARED_KITCHEN', 'FREE_HOURS', 'WATER_HEATER'],
        NOW() + INTERVAL '60 days'
    ) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, price = EXCLUDED.price, status = EXCLUDED.status;

    DELETE FROM room_images WHERE room_id = v_room_7_id;
    INSERT INTO room_images (room_id, image_url, is_primary, display_order) VALUES
        (v_room_7_id, 'https://images.unsplash.com/photo-1555854877-bab0e564b8d5?auto=format&fit=crop&w=1000&q=80', true, 0),
        (v_room_7_id, 'https://images.unsplash.com/photo-1595526114035-0d45ed16cfbf?auto=format&fit=crop&w=1000&q=80', false, 1),
        (v_room_7_id, 'https://images.unsplash.com/photo-1560185007-c5ca9d2c014d?auto=format&fit=crop&w=1000&q=80', false, 2),
        (v_room_7_id, 'https://images.unsplash.com/photo-1507652313519-d4e9174996dd?auto=format&fit=crop&w=1000&q=80', false, 3);

    DELETE FROM room_fees WHERE room_id = v_room_7_id;
    INSERT INTO room_fees (room_id, fee_label, fee_value) VALUES
        (v_room_7_id, 'Trọn gói Điện, Nước, Wifi', 'Đã bao gồm trong giá phòng'),
        (v_room_7_id, 'Gửi xe máy', '100.000 đ/tháng');

    -- PHÒNG 8: Phòng Trọ Sinh Viên Có Gác - Gò Vấp (Chủ: Bác Ba)
    INSERT INTO rooms (id, landlord_id, title, description, room_type, price, deposit_amount, area_sqm, floor_number, max_occupants, address_street, district, city, latitude, longitude, location, status, is_verified, amenities, expires_at)
    VALUES (
        v_room_8_id,
        v_landlord_1_id,
        'Phòng Trọ Sinh Viên Có Gác Cao - Gần ĐH Công Nghiệp TP.HCM (IUH)',
        'Phòng trọ rộng rãi ở được 2-3 bạn, gác lửng ốp gạch sạch sẽ. Khu an ninh có bảo vệ trực đêm, giờ đóng cổng 23h30 hoặc nhận vân tay mở khóa tự do.',
        'PHONG_TRO',
        2800000, 2800000, 22.0, 1, 3,
        '366/28 Phan Văn Trị, Phường 5', 'Gò Vấp', 'TP.HCM',
        10.8258, 106.6904, ST_SetSRID(ST_MakePoint(106.6904, 10.8258), 4326),
        'AVAILABLE', true,
        ARRAY['WIFI', 'MEZZANINE', 'WATER_HEATER', 'PARKING', 'CAMERA', 'SECURITY_24_7'],
        NOW() + INTERVAL '60 days'
    ) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, price = EXCLUDED.price, status = EXCLUDED.status;

    DELETE FROM room_images WHERE room_id = v_room_8_id;
    INSERT INTO room_images (room_id, image_url, is_primary, display_order) VALUES
        (v_room_8_id, 'https://images.unsplash.com/photo-1560185127-6ed189bf02f4?auto=format&fit=crop&w=1000&q=80', true, 0),
        (v_room_8_id, 'https://images.unsplash.com/photo-1560185893-a55cbc8c57e8?auto=format&fit=crop&w=1000&q=80', false, 1),
        (v_room_8_id, 'https://images.unsplash.com/photo-1505691723518-36a5ac3be353?auto=format&fit=crop&w=1000&q=80', false, 2),
        (v_room_8_id, 'https://images.unsplash.com/photo-1616046229478-9901c5536a45?auto=format&fit=crop&w=1000&q=80', false, 3);

    DELETE FROM room_fees WHERE room_id = v_room_8_id;
    INSERT INTO room_fees (room_id, fee_label, fee_value) VALUES
        (v_room_8_id, 'Điện', '3.500 đ/kWh'),
        (v_room_8_id, 'Nước máy', '70.000 đ/người'),
        (v_room_8_id, 'Rác & Vệ sinh', '30.000 đ/phòng');

    -- PHÒNG 9: Studio Đầy Đủ Tiện Nghi - TP. Thủ Đức (Chủ: Bác Ba)
    INSERT INTO rooms (id, landlord_id, title, description, room_type, price, deposit_amount, area_sqm, floor_number, max_occupants, address_street, district, city, latitude, longitude, location, status, is_verified, amenities, expires_at)
    VALUES (
        v_room_9_id,
        v_landlord_1_id,
        'Studio Cửa Sổ Thoáng Gần ĐHQG & Khu Công Nghệ Cao - TP. Thủ Đức',
        'Căn hộ mini mới hoàn thiện, đầy đủ máy lạnh, tủ lạnh, bếp nấu ăn, máy giặt. Nằm trong khu dân cư yên tĩnh, gần trạm xe buýt và tuyến Metro số 1 Bến Thành - Suối Tiên.',
        'PHONG_KHEP_KIN',
        4000000, 4000000, 30.0, 2, 2,
        '52 Đường số 4, Phường Linh Chiểu', 'Thành phố Thủ Đức', 'TP.HCM',
        10.8524, 106.7628, ST_SetSRID(ST_MakePoint(106.7628, 10.8524), 4326),
        'AVAILABLE', true,
        ARRAY['WIFI', 'AIR_CONDITIONER', 'REFRIGERATOR', 'KITCHEN', 'WINDOW', 'FINGERPRINT_LOCK'],
        NOW() + INTERVAL '60 days'
    ) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, price = EXCLUDED.price, status = EXCLUDED.status;

    DELETE FROM room_images WHERE room_id = v_room_9_id;
    INSERT INTO room_images (room_id, image_url, is_primary, display_order) VALUES
        (v_room_9_id, 'https://images.unsplash.com/photo-1502005229762-ee1b2b8ab00f?auto=format&fit=crop&w=1000&q=80', true, 0),
        (v_room_9_id, 'https://images.unsplash.com/photo-1600210492486-724fe5c67fb0?auto=format&fit=crop&w=1000&q=80', false, 1),
        (v_room_9_id, 'https://images.unsplash.com/photo-1600607687939-ce8a6c25118c?auto=format&fit=crop&w=1000&q=80', false, 2),
        (v_room_9_id, 'https://images.unsplash.com/photo-1600566753376-12c8ab7fb75b?auto=format&fit=crop&w=1000&q=80', false, 3);

    DELETE FROM room_fees WHERE room_id = v_room_9_id;
    INSERT INTO room_fees (room_id, fee_label, fee_value) VALUES
        (v_room_9_id, 'Điện', '3.700 đ/kWh'),
        (v_room_9_id, 'Nước máy', '80.000 đ/người'),
        (v_room_9_id, 'Internet wifi', '80.000 đ/phòng');

    -- PHÒNG 10: Phòng Ở Ghép Tiện Nghi - Quận 5 (Chủ: Anh Nam)
    INSERT INTO rooms (id, landlord_id, title, description, room_type, price, deposit_amount, area_sqm, floor_number, max_occupants, address_street, district, city, latitude, longitude, location, status, is_verified, amenities, expires_at)
    VALUES (
        v_room_10_id,
        v_landlord_3_id,
        'Phòng Ở Ghép Sinh Viên Gần ĐH Sư Phạm & ĐH Khoa Học Tự Nhiên - Quận 5',
        'Phòng sạch sẽ, thông thoáng, đã có sẵn 1 bạn sinh viên KHTN năm 2 đang ở cần tìm thêm 1 bạn nam nữa ghép cùng chia đôi chi phí. Có máy lạnh, tủ lạnh, chỗ để xe rộng rãi.',
        'O_GHEP',
        1900000, 1900000, 25.0, 2, 2,
        '105/22 Trần Hưng Đạo, Phường 5', 'Quận 5', 'TP.HCM',
        10.7554, 106.6719, ST_SetSRID(ST_MakePoint(106.6719, 10.7554), 4326),
        'AVAILABLE', true,
        ARRAY['WIFI', 'AIR_CONDITIONER', 'REFRIGERATOR', 'WATER_HEATER', 'PARKING', 'FREE_HOURS'],
        NOW() + INTERVAL '60 days'
    ) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, price = EXCLUDED.price, status = EXCLUDED.status;

    DELETE FROM room_images WHERE room_id = v_room_10_id;
    INSERT INTO room_images (room_id, image_url, is_primary, display_order) VALUES
        (v_room_10_id, 'https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=1000&q=80', true, 0),
        (v_room_10_id, 'https://images.unsplash.com/photo-1600573472591-ee6b68d14c68?auto=format&fit=crop&w=1000&q=80', false, 1),
        (v_room_10_id, 'https://images.unsplash.com/photo-1600566753190-17f0baa2a6c3?auto=format&fit=crop&w=1000&q=80', false, 2),
        (v_room_10_id, 'https://images.unsplash.com/photo-1600585154526-990dced4db0d?auto=format&fit=crop&w=1000&q=80', false, 3);

    DELETE FROM room_fees WHERE room_id = v_room_10_id;
    INSERT INTO room_fees (room_id, fee_label, fee_value) VALUES
        (v_room_10_id, 'Điện', '3.800 đ/kWh (chia đôi)'),
        (v_room_10_id, 'Nước máy', '80.000 đ/người'),
        (v_room_10_id, 'Internet wifi', '50.000 đ/người');

END $$;
