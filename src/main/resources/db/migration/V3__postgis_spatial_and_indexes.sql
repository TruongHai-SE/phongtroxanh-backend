-- ==============================================================================
-- PHÒNG TRỌ XANH (PhongTroXanh.vn) - ENTERPRISE CONSOLIDATED MIGRATION
-- MIGRATION: V3 - POSTGIS SPATIAL INDEXES, PERFORMANCE & INTEGRITY CONSTRAINTS
-- ==============================================================================

-- 1. CHỈ MỤC KHÔNG GIAN POSTGIS (GIST SPATIAL INDEX)
CREATE INDEX IF NOT EXISTS idx_rooms_location_gist ON rooms USING GIST (location);

-- 2. CHỈ MỤC HIỆU NĂNG TÌM KIẾM PHÒNG TRỌ & ĐA THUỘC TÍNH
CREATE INDEX IF NOT EXISTS idx_rooms_district_status ON rooms (district, status, price);
CREATE INDEX IF NOT EXISTS idx_rooms_landlord_id ON rooms (landlord_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_rooms_creation_request ON rooms (landlord_id, creation_request_id) WHERE creation_request_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_room_images_phash ON room_images (phash) WHERE phash IS NOT NULL;

-- 3. CHỈ MỤC QUẸT PHÒNG, TƯƠNG TÁC & GHÉP BẠN
CREATE INDEX IF NOT EXISTS idx_room_swipes_user_room ON room_swipes (user_id, room_id);
CREATE INDEX IF NOT EXISTS idx_room_swipes_room_action ON room_swipes (room_id, action);
CREATE INDEX IF NOT EXISTS idx_swipes_swiper_target ON swipes (swiper_id, target_id);
CREATE INDEX IF NOT EXISTS idx_matches_users ON matches (user_a_id, user_b_id);

-- 4. CHỈ MỤC HỘI THOẠI, TIN NHẮN & THÔNG BÁO
CREATE INDEX IF NOT EXISTS idx_messages_conversation_created ON messages (conversation_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_user_unread ON notifications (user_id, is_read, created_at DESC);

-- 5. CHỈ MỤC TÍNH TOÀN VẸN GIAO DỊCH, HỢP ĐỒNG & THANH TOÁN
CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_gateway_order ON payment_transactions (gateway_order_id) WHERE gateway_order_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_rental_active_room ON rental_contracts (room_id) WHERE status IN ('PENDING_CHECKIN', 'CHECKED_IN');
CREATE UNIQUE INDEX IF NOT EXISTS uq_review_rental_reviewer ON reviews (rental_contract_id, reviewer_id);

-- 6. CHỈ MỤC HỆ THỐNG OUTBOX & AUDIT LOGS
CREATE INDEX IF NOT EXISTS idx_outbox_pending ON outbox_events (status, created_at) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_reports_status_severity ON reports (status, severity);
CREATE INDEX IF NOT EXISTS idx_audit_logs_actor ON system_audit_logs (actor_id, created_at DESC);

-- 7. TRIGGER TỰ ĐỘNG ĐỒNG BỘ GEOMETRY LOCATION TỪ LATITUDE & LONGITUDE
CREATE OR REPLACE FUNCTION fn_sync_room_location()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.longitude IS NOT NULL AND NEW.latitude IS NOT NULL THEN
        NEW.location := ST_SetSRID(ST_MakePoint(NEW.longitude, NEW.latitude), 4326);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sync_room_location ON rooms;
CREATE TRIGGER trg_sync_room_location
BEFORE INSERT OR UPDATE OF latitude, longitude ON rooms
FOR EACH ROW
EXECUTE FUNCTION fn_sync_room_location();

-- Cập nhật đồng bộ các phòng hiện có nếu chưa có geometry point
UPDATE rooms SET location = ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)
WHERE location IS NULL AND longitude IS NOT NULL AND latitude IS NOT NULL;
