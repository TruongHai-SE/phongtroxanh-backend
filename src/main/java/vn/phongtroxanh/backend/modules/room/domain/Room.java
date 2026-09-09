package vn.phongtroxanh.backend.modules.room.domain;

import jakarta.persistence.*;
import lombok.*;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import vn.phongtroxanh.backend.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "rooms")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Room extends BaseEntity {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Column(name = "landlord_id", nullable = false)
    private UUID landlordId;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(name = "room_type", length = 50, nullable = false)
    private String roomType;

    @Column(name = "price", precision = 12, scale = 2, nullable = false)
    private BigDecimal price;

    @Column(name = "deposit_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal depositAmount;

    @Column(name = "area_sqm", precision = 6, scale = 2, nullable = false)
    private BigDecimal areaSqm;

    @Column(name = "floor_number")
    @Builder.Default
    private Integer floorNumber = 1;

    @Column(name = "max_occupants")
    @Builder.Default
    private Integer maxOccupants = 2;

    @Column(name = "address_street", length = 255, nullable = false)
    private String addressStreet;

    @Column(name = "district", length = 100, nullable = false)
    private String district;


    @Column(name = "city", length = 100, nullable = false)
    @Builder.Default
    private String city = "TP.HCM";

    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name = "longitude", nullable = false)
    private Double longitude;

    @Column(name = "location", columnDefinition = "geometry(Point,4326)")
    private Point location;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    @Builder.Default
    private RoomStatus status = RoomStatus.AVAILABLE;

    @Column(name = "is_verified")
    @Builder.Default
    private Boolean isVerified = false;

    @Column(name = "is_boosted")
    @Builder.Default
    private Boolean isBoosted = false;

    @Column(name = "boost_expires_at")
    private Instant boostExpiresAt;

    @Column(name = "view_count")
    @Builder.Default
    private Long viewCount = 0L;

    @Version
    @Column(name = "version")
    @Builder.Default
    private Long version = 0L;

    @Column(name = "expires_at")
    @Builder.Default
    private Instant expiresAt = Instant.now().plus(30, ChronoUnit.DAYS);

    @org.hibernate.annotations.BatchSize(size = 50)
    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<RoomImage> images = new ArrayList<>();

    @org.hibernate.annotations.BatchSize(size = 50)
    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<RoomFee> fees = new ArrayList<>();

    @PrePersist
    @PreUpdate
    public void syncGeometry() {
        if (latitude != null && longitude != null) {
            this.location = GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
        }
    }
}
