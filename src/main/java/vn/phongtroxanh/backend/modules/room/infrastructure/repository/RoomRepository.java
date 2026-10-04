package vn.phongtroxanh.backend.modules.room.infrastructure.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.phongtroxanh.backend.modules.room.domain.Room;
import vn.phongtroxanh.backend.modules.room.domain.RoomStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface RoomRepository extends JpaRepository<Room, UUID>, JpaSpecificationExecutor<Room> {

    List<Room> findByLandlordIdOrderByCreatedAtDesc(UUID landlordId);

    Page<Room> findByStatus(RoomStatus status, Pageable pageable);

    @Query(value = """
        SELECT r.* FROM rooms r
        WHERE r.status = 'AVAILABLE'
          AND r.expires_at > CURRENT_TIMESTAMP
          AND EXISTS (SELECT 1 FROM users u WHERE u.id = r.landlord_id AND u.status NOT IN ('LOCKED', 'DELETED'))
          AND (CAST(:excludeUserId AS UUID) IS NULL OR NOT EXISTS (
              SELECT 1 FROM room_swipes rs WHERE rs.room_id = r.id AND rs.user_id = CAST(:excludeUserId AS UUID)
          ))
          AND (CAST(:district AS TEXT) IS NULL OR r.district = CAST(:district AS TEXT))
          AND (CAST(:roomType AS TEXT) IS NULL OR r.room_type = CAST(:roomType AS TEXT))
          AND (CAST(:minPrice AS NUMERIC) IS NULL OR r.price >= CAST(:minPrice AS NUMERIC))
          AND (CAST(:maxPrice AS NUMERIC) IS NULL OR r.price <= CAST(:maxPrice AS NUMERIC))
          AND (CAST(:keyword AS TEXT) IS NULL OR LOWER(r.title) LIKE LOWER(CONCAT('%', CAST(:keyword AS TEXT), '%')) OR LOWER(r.address_street) LIKE LOWER(CONCAT('%', CAST(:keyword AS TEXT), '%')))
        ORDER BY
          CASE WHEN CAST(:sortBy AS TEXT) = 'boost_first' AND r.is_boosted = TRUE AND r.boost_expires_at > CURRENT_TIMESTAMP THEN r.boost_expires_at END DESC NULLS LAST,
          CASE WHEN CAST(:sortBy AS TEXT) = 'price_asc' THEN r.price END ASC,
          CASE WHEN CAST(:sortBy AS TEXT) = 'price_desc' THEN r.price END DESC,
          r.created_at DESC
        """,
        countQuery = """
        SELECT count(*) FROM rooms r
        WHERE r.status = 'AVAILABLE'
          AND r.expires_at > CURRENT_TIMESTAMP
          AND EXISTS (SELECT 1 FROM users u WHERE u.id = r.landlord_id AND u.status NOT IN ('LOCKED', 'DELETED'))
          AND (CAST(:excludeUserId AS UUID) IS NULL OR NOT EXISTS (
              SELECT 1 FROM room_swipes rs WHERE rs.room_id = r.id AND rs.user_id = CAST(:excludeUserId AS UUID)
          ))
          AND (CAST(:district AS TEXT) IS NULL OR r.district = CAST(:district AS TEXT))
          AND (CAST(:roomType AS TEXT) IS NULL OR r.room_type = CAST(:roomType AS TEXT))
          AND (CAST(:minPrice AS NUMERIC) IS NULL OR r.price >= CAST(:minPrice AS NUMERIC))
          AND (CAST(:maxPrice AS NUMERIC) IS NULL OR r.price <= CAST(:maxPrice AS NUMERIC))
          AND (CAST(:keyword AS TEXT) IS NULL OR LOWER(r.title) LIKE LOWER(CONCAT('%', CAST(:keyword AS TEXT), '%')) OR LOWER(r.address_street) LIKE LOWER(CONCAT('%', CAST(:keyword AS TEXT), '%')))
        """,
        nativeQuery = true)
    Page<Room> searchRooms(
            @Param("district") String district,
            @Param("roomType") String roomType,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("keyword") String keyword,
            @Param("sortBy") String sortBy,
            @Param("excludeUserId") UUID excludeUserId,
            Pageable pageable);

    @Query(value = """
        SELECT r.* FROM rooms r
        WHERE r.status = 'AVAILABLE'
          AND r.expires_at > CURRENT_TIMESTAMP
          AND EXISTS (SELECT 1 FROM users u WHERE u.id = r.landlord_id AND u.status NOT IN ('LOCKED', 'DELETED'))
          AND ST_DWithin(r.location::geography, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography, :radiusMeters)
          AND (CAST(:minPrice AS NUMERIC) IS NULL OR r.price >= CAST(:minPrice AS NUMERIC))
          AND (CAST(:maxPrice AS NUMERIC) IS NULL OR r.price <= CAST(:maxPrice AS NUMERIC))
          AND (CAST(:district AS TEXT) IS NULL OR r.district = CAST(:district AS TEXT))
          AND (CAST(:roomType AS TEXT) IS NULL OR r.room_type = CAST(:roomType AS TEXT))
        LIMIT 100
        """, nativeQuery = true)
    List<Room> findRoomsInRadius(
            @Param("lat") double lat,
            @Param("lon") double lon,
            @Param("radiusMeters") double radiusMeters,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("district") String district,
            @Param("roomType") String roomType);

    long countByLandlordId(UUID landlordId);
    long countByLandlordIdAndStatus(UUID landlordId, RoomStatus status);
}
