-- ==============================================================================
-- PHÒNG TRỌ XANH (PhongTroXanh.vn)
-- MIGRATION: V7 - NORMALIZE SUPABASE AMENITIES TO VIETNAMESE, FIX THU DUC DISTRICT & DAKAO GPS
-- Automatically executed by Flyway on Render/Supabase startup
-- ==============================================================================

-- 1. Restore exact GPS coordinates for Room at 18A/42 Nguyễn Thị Minh Khai, Phường Đa Kao, Quận 1
UPDATE rooms
SET latitude = 10.7876,
    longitude = 106.6998
WHERE id = '33333333-3333-3333-3333-333333333304'
   OR address_street ILIKE '%18A/42 Nguyễn Thị Minh Khai%';

-- 2. Normalize district labels ('Thành phố Thủ Đức' / 'TP. Thủ Đức') to 'Thủ Đức' for filter & analytics consistency
UPDATE rooms
SET district = 'Thủ Đức'
WHERE district IN ('Thành phố Thủ Đức', 'TP. Thủ Đức', 'TP Thủ Đức', 'Quận Thủ Đức');

-- 3. Convert all legacy uppercase English / unaccented amenity codes in rooms.amenities to canonical Vietnamese labels
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
                WHEN 'PRIVATE_BATHROOM' THEN 'WC riêng'
                WHEN 'WC RIENG' THEN 'WC riêng'
                WHEN 'FREE_HOURS' THEN 'Giờ tự do'
                WHEN 'FREE_TIME' THEN 'Giờ tự do'
                WHEN 'GIO TU DO' THEN 'Giờ tự do'
                WHEN 'PARKING' THEN 'Bãi xe'
                WHEN 'BAI XE' THEN 'Bãi xe'
                WHEN 'KITCHEN' THEN 'Bếp riêng'
                WHEN 'BEP RIENG' THEN 'Bếp riêng'
                WHEN 'BALCONY' THEN 'Ban công'
                WHEN 'BAN CONG' THEN 'Ban công'
                WHEN 'ELEVATOR' THEN 'Thang máy'
                WHEN 'THANG MAY' THEN 'Thang máy'
                WHEN 'MEZZANINE' THEN 'Gác lửng'
                WHEN 'GAC LUNG' THEN 'Gác lửng'
                WHEN 'WATER_HEATER' THEN 'Máy nước nóng'
                WHEN 'MAY NUOC NONG' THEN 'Máy nước nóng'
                WHEN 'REFRIGERATOR' THEN 'Tủ lạnh'
                WHEN 'FRIDGE' THEN 'Tủ lạnh'
                WHEN 'TU LANH' THEN 'Tủ lạnh'
                WHEN 'WASHING_MACHINE' THEN 'Máy giặt'
                WHEN 'MAY GIAT' THEN 'Máy giặt'
                WHEN 'FINGERPRINT_LOCK' THEN 'Khóa vân tay'
                WHEN 'KHOA VAN TAY' THEN 'Khóa vân tay'
                WHEN 'CAMERA' THEN 'Camera an ninh'
                WHEN 'SECURITY_CAMERA' THEN 'Camera an ninh'
                WHEN 'SECURITY_24_7' THEN 'Bảo vệ 24/7'
                WHEN 'SECURITY' THEN 'An ninh 24/7'
                WHEN 'WINDOW' THEN 'Cửa sổ thoáng'
                WHEN 'BED' THEN 'Giường nệm'
                WHEN 'MATTRESS' THEN 'Giường nệm'
                WHEN 'WARDROBE' THEN 'Tủ quần áo'
                WHEN 'CLOSET' THEN 'Tủ quần áo'
                WHEN 'MICROWAVE' THEN 'Lò vi sóng'
                WHEN 'TELEVISION' THEN 'Tivi'
                WHEN 'TV' THEN 'Tivi'
                WHEN 'CLEANING_SERVICE' THEN 'Dọn vệ sinh'
                WHEN 'LOCKER' THEN 'Tủ khóa cá nhân'
                WHEN 'SHARED_KITCHEN' THEN 'Bếp chung'
                WHEN 'DESK' THEN 'Bàn làm việc'
                WHEN 'PET_FRIENDLY' THEN 'Cho nuôi thú cưng'
                WHEN 'DRYER' THEN 'Máy sấy quần áo'
                ELSE TRIM(raw_item)
            END AS mapped_label
            FROM unnest(r.amenities) AS raw_item
        ) t
        WHERE mapped_label IS NOT NULL AND mapped_label <> ''
    )
)
WHERE amenities IS NOT NULL AND cardinality(amenities) > 0;

-- 4. Synchronize rooms.is_verified with landlord KYC / verified status
UPDATE rooms r
SET is_verified = true
FROM users u
WHERE r.landlord_id = u.id
  AND u.is_verified = true
  AND (r.is_verified IS NULL OR r.is_verified = false);

-- 5. Expire stale PENDING PayOS payment transactions past their QR expiration timestamp
UPDATE payment_transactions
SET status = 'EXPIRED'
WHERE status = 'PENDING'
  AND qr_expired_at IS NOT NULL
  AND qr_expired_at < NOW();
