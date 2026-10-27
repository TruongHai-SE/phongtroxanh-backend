-- ==============================================================================
-- PHÒNG TRỌ XANH (PhongTroXanh.vn)
-- MIGRATION: V8 - ENFORCE CANONICAL 10 AMENITIES & CHAT ATTACHMENT TEXT COLUMN
-- Automatically executed by Flyway on Local PostgreSQL and Supabase
-- ==============================================================================

-- 1. Normalize and filter rooms.amenities strictly to the 10 canonical options:
--    ('Máy lạnh', 'Wi-Fi', 'WC riêng', 'Giờ tự do', 'Bãi xe', 'Bếp riêng', 'Ban công', 'Thang máy', 'Gác lửng', 'An ninh 24/7')
UPDATE rooms r
SET amenities = (
    SELECT ARRAY(
        SELECT DISTINCT mapped_label
        FROM (
            SELECT CASE UPPER(TRIM(raw_item))
                WHEN 'WIFI' THEN 'Wi-Fi'
                WHEN 'WI-FI' THEN 'Wi-Fi'
                WHEN 'AIR_CONDITIONER' THEN 'Máy lạnh'
                WHEN 'MAY LANH' THEN 'Máy lạnh'
                WHEN 'MÁY LẠNH' THEN 'Máy lạnh'
                WHEN 'PRIVATE_BATHROOM' THEN 'WC riêng'
                WHEN 'WC RIENG' THEN 'WC riêng'
                WHEN 'WC RIÊNG' THEN 'WC riêng'
                WHEN 'FREE_HOURS' THEN 'Giờ tự do'
                WHEN 'FREE_TIME' THEN 'Giờ tự do'
                WHEN 'GIO TU DO' THEN 'Giờ tự do'
                WHEN 'GIỜ TỰ DO' THEN 'Giờ tự do'
                WHEN 'PARKING' THEN 'Bãi xe'
                WHEN 'BAI XE' THEN 'Bãi xe'
                WHEN 'BÃI XE' THEN 'Bãi xe'
                WHEN 'KITCHEN' THEN 'Bếp riêng'
                WHEN 'BEP RIENG' THEN 'Bếp riêng'
                WHEN 'BẾP RIÊNG' THEN 'Bếp riêng'
                WHEN 'SHARED_KITCHEN' THEN 'Bếp riêng'
                WHEN 'BẾP CHUNG' THEN 'Bếp riêng'
                WHEN 'BALCONY' THEN 'Ban công'
                WHEN 'BAN CONG' THEN 'Ban công'
                WHEN 'BAN CÔNG' THEN 'Ban công'
                WHEN 'ELEVATOR' THEN 'Thang máy'
                WHEN 'THANG MAY' THEN 'Thang máy'
                WHEN 'THANG MÁY' THEN 'Thang máy'
                WHEN 'MEZZANINE' THEN 'Gác lửng'
                WHEN 'GAC LUNG' THEN 'Gác lửng'
                WHEN 'GÁC LỬNG' THEN 'Gác lửng'
                WHEN 'SECURITY' THEN 'An ninh 24/7'
                WHEN 'SECURITY_24_7' THEN 'An ninh 24/7'
                WHEN 'AN NINH 24/7' THEN 'An ninh 24/7'
                WHEN 'BẢO VỆ 24/7' THEN 'An ninh 24/7'
                WHEN 'BAO VE 24/7' THEN 'An ninh 24/7'
                WHEN 'CAMERA' THEN 'An ninh 24/7'
                WHEN 'CAMERA AN NINH' THEN 'An ninh 24/7'
                WHEN 'FINGERPRINT_LOCK' THEN 'An ninh 24/7'
                WHEN 'KHÓA VÂN TAY' THEN 'An ninh 24/7'
                WHEN 'KHOA VAN TAY' THEN 'An ninh 24/7'
                ELSE NULL
            END AS mapped_label
            FROM unnest(r.amenities) AS raw_item
        ) t
        WHERE mapped_label IS NOT NULL
    )
)
WHERE amenities IS NOT NULL AND cardinality(amenities) > 0;

-- Ensure every room has at least default canonical amenities if empty after filtering
UPDATE rooms
SET amenities = ARRAY['Wi-Fi', 'Máy lạnh', 'WC riêng', 'Giờ tự do']
WHERE amenities IS NULL OR cardinality(amenities) = 0;

-- 2. Ensure messages.attachment_url is TEXT so Base64 chat image attachments work on Supabase & Local DB
ALTER TABLE messages
ALTER COLUMN attachment_url TYPE TEXT;
