-- ==============================================================================
-- PHÒNG TRỌ XANH (PhongTroXanh.vn)
-- MIGRATION: V6 - CALIBRATE REAL STREET GPS COORDINATES & AMENITIES FOR SEEDED ROOMS
-- Ensures PostGIS pins match exact street addresses & districts in Ho Chi Minh City
-- ==============================================================================

-- 1. Đường số 14, KDC Him Lam, Phường Tân Hưng, Quận 7
UPDATE rooms
SET latitude = 10.7396,
    longitude = 106.7014,
    amenities = CASE WHEN amenities IS NULL OR cardinality(amenities) = 0 THEN ARRAY['Máy lạnh', 'WC riêng', 'Wi-Fi', 'Bãi xe', 'Giờ tự do'] ELSE amenities END
WHERE id = '839da43e-fc8e-4bcc-b376-367c47ad9c78' OR address_street ILIKE '%14%Tân Hưng%';

-- 2. 48 Võ Văn Ngân, TP. Thủ Đức - Gần ĐH Sư Phạm Kỹ Thuật
UPDATE rooms
SET latitude = 10.8505,
    longitude = 106.7668,
    amenities = CASE WHEN amenities IS NULL OR cardinality(amenities) = 0 THEN ARRAY['Máy lạnh', 'WC riêng', 'Wi-Fi', 'Giờ tự do', 'Bãi xe'] ELSE amenities END
WHERE id = 'a525dd2a-a4be-4e99-ad4f-ad0e209e90ac' OR address_street ILIKE '%48%Võ Văn Ngân%';

-- 3. Đường số 6, Linh Xuân, TP. Thủ Đức - Gần Làng Đại Học Quốc Gia
UPDATE rooms
SET latitude = 10.8752,
    longitude = 106.7685,
    description = CASE WHEN description = 'Mo ta phong tro day du tien nghi' THEN 'Studio mới xây sạch thoáng gần Làng Đại Học Quốc Gia Thủ Đức, có máy lạnh, WC riêng và giờ giấc tự do.' ELSE description END,
    amenities = CASE WHEN amenities IS NULL OR cardinality(amenities) = 0 THEN ARRAY['Máy lạnh', 'WC riêng', 'Wi-Fi', 'Giờ tự do'] ELSE amenities END
WHERE id = '05f90ed9-a684-42f0-a05a-4a685dd700ad' OR address_street ILIKE '%Linh Xuân%';

-- 4. Phạm Văn Đồng, Hiệp Bình Chánh, TP. Thủ Đức - Gần TTTM Giga Mall
UPDATE rooms
SET latitude = 10.8278,
    longitude = 106.7214,
    description = CASE WHEN description = 'Mo ta phong tro day du tien nghi' THEN 'Căn hộ mini có ban công thoáng mát ngay trục Phạm Văn Đồng gần Giga Mall Thủ Đức, đầy đủ nội thất.' ELSE description END,
    amenities = CASE WHEN amenities IS NULL OR cardinality(amenities) = 0 THEN ARRAY['Máy lạnh', 'WC riêng', 'Ban công', 'Bếp riêng', 'Wi-Fi'] ELSE amenities END
WHERE id = '79737906-a30b-4f63-9410-0050d4c724e5' OR address_street ILIKE '%Hiệp Bình Chánh%';

-- 5. Đường D5, Phường 25, Bình Thạnh - Gần ĐH Ngoại Thương CS2
UPDATE rooms
SET latitude = 10.8069,
    longitude = 106.7131,
    amenities = CASE WHEN amenities IS NULL OR cardinality(amenities) = 0 THEN ARRAY['Máy lạnh', 'WC riêng', 'Ban công', 'Wi-Fi', 'Giờ tự do'] ELSE amenities END
WHERE id = 'cfbec99d-6a19-4a87-ace0-c633472ba087' OR address_street ILIKE '%D5%';

-- 6. Xô Viết Nghệ Tĩnh, Bình Thạnh - Gần Cầu Thị Nghè
UPDATE rooms
SET latitude = 10.7932,
    longitude = 106.7076,
    amenities = CASE WHEN amenities IS NULL OR cardinality(amenities) = 0 THEN ARRAY['Máy lạnh', 'WC riêng', 'Bếp riêng', 'Wi-Fi', 'Bãi xe'] ELSE amenities END
WHERE id = 'bb6844cc-54ba-4659-b415-719a3e5add67' OR address_street ILIKE '%Xô Viết Nghệ Tĩnh%';

-- 7. 475A Điện Biên Phủ, Phường 25, Bình Thạnh - Gần ĐH HUTECH
UPDATE rooms
SET latitude = 10.8018,
    longitude = 106.7145,
    amenities = CASE WHEN amenities IS NULL OR cardinality(amenities) = 0 THEN ARRAY['Máy lạnh', 'WC riêng', 'Wi-Fi', 'Giờ tự do', 'Thang máy'] ELSE amenities END
WHERE id = 'af6b0078-cd73-428a-a135-2d2a6ad59c51' OR address_street ILIKE '%475A%';

-- 8. Nguyễn Tri Phương, Phường 8, Quận 10 - Gần ĐH Kinh Tế (UEH)
UPDATE rooms
SET latitude = 10.7614,
    longitude = 106.6683,
    amenities = CASE WHEN amenities IS NULL OR cardinality(amenities) = 0 THEN ARRAY['Máy lạnh', 'WC riêng', 'Ban công', 'Bếp riêng', 'Wi-Fi'] ELSE amenities END
WHERE id = 'ea949708-c17a-441d-8cf2-ce21215cf899' OR address_street ILIKE '%Nguyễn Tri Phương%';

-- 9. 268 Lý Thường Kiệt, Phường 14, Quận 10 - Gần ĐH Bách Khoa
UPDATE rooms
SET latitude = 10.7725,
    longitude = 106.6578,
    amenities = CASE WHEN amenities IS NULL OR cardinality(amenities) = 0 THEN ARRAY['Máy lạnh', 'WC riêng', 'Wi-Fi', 'Giờ tự do', 'Bãi xe'] ELSE amenities END
WHERE id = 'a72990e6-9355-4b7e-ad3c-21e6440ab36e' OR address_street ILIKE '%268%';

-- 10. Tô Hiến Thành, Quận 10 - Gần ĐH Bách Khoa
UPDATE rooms
SET latitude = 10.7780,
    longitude = 106.6620,
    amenities = CASE WHEN amenities IS NULL OR cardinality(amenities) = 0 THEN ARRAY['Máy lạnh', 'Wi-Fi', 'Giờ tự do', 'Bãi xe'] ELSE amenities END
WHERE id = '8b6818b2-182e-4f2e-8d6a-bee0955eb521' OR address_street ILIKE '%Tô Hiến Thành%';

-- 11. Hai Bà Trưng, Đa Kao, Quận 1 - Gần Công viên Lê Văn Tám
UPDATE rooms
SET latitude = 10.7886,
    longitude = 106.6952,
    description = CASE WHEN description = 'Mô tả phòng VIP' THEN 'Studio hiện đại ngay Hai Bà Trưng, Phường Đa Kao, Quận 1, đối diện Công viên Lê Văn Tám, an ninh 24/7.' ELSE description END,
    amenities = CASE WHEN amenities IS NULL OR cardinality(amenities) = 0 THEN ARRAY['Máy lạnh', 'WC riêng', 'Wi-Fi', 'Bếp riêng', 'Thang máy', 'Giờ tự do'] ELSE amenities END
WHERE id = '4b8771e4-8794-4dea-a83f-9be7758065db' OR address_street ILIKE '%Đa Kao%';

-- 12. Lê Lợi, Phường Bến Thành, Quận 1
UPDATE rooms
SET latitude = 10.7728,
    longitude = 106.6984,
    description = CASE WHEN description = 'Mô tả phòng VIP' THEN 'Căn hộ mini ban công thoáng mát ngay trung tâm đường Lê Lợi, Quận 1, đầy đủ nội thất cao cấp.' ELSE description END,
    amenities = CASE WHEN amenities IS NULL OR cardinality(amenities) = 0 THEN ARRAY['Máy lạnh', 'WC riêng', 'Ban công', 'Bếp riêng', 'Thang máy', 'Wi-Fi'] ELSE amenities END
WHERE id = 'b62be2c2-29f9-41cf-9868-2e7f65fcf9f9' OR address_street ILIKE '%Lê Lợi%';

-- 13. Nguyễn Thị Thập, Quận 7 - Gần ĐH Tôn Đức Thắng (Normalize unaccented amenities)
UPDATE rooms
SET latitude = 10.7385,
    longitude = 106.7165,
    description = CASE WHEN description ILIKE 'Phong studio day du noi that%' THEN 'Phòng Studio cửa sổ lớn đầy đủ nội thất, giờ giấc tự do, gần ĐH Tôn Đức Thắng và Lotte Mart Quận 7.' ELSE description END,
    amenities = ARRAY['Máy lạnh', 'WC riêng', 'Wi-Fi', 'Giờ tự do', 'Bãi xe']
WHERE id = '157d7c1a-6eb7-48bb-aa62-7676645c06be' OR address_street ILIKE '%Nguyễn Thị Thập%';

-- 14. Đồng bộ trạng thái tích xanh (is_verified) cho các phòng hiện có theo trạng thái xác minh CCCD của chủ trọ
UPDATE rooms r
SET is_verified = u.is_verified
FROM users u
WHERE r.landlord_id = u.id
  AND r.is_verified IS DISTINCT FROM u.is_verified;

-- 15. Đảm bảo mọi phòng hiện có trên DB đều có max_occupants hợp lệ (mặc định 2 người nếu bị NULL)
UPDATE rooms
SET max_occupants = 2
WHERE max_occupants IS NULL OR max_occupants < 1;
