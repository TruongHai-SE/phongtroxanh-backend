package vn.phongtroxanh.backend.modules.admin.presentation.dto;

import lombok.*;
import vn.phongtroxanh.backend.modules.room.domain.RoomStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminRoomResponse {

    private UUID id;
    private UUID landlordId;
    private String title;
    private String description;
    private String roomType;
    private BigDecimal price;
    private BigDecimal depositAmount;
    private BigDecimal areaSqm;
    private Integer floorNumber;
    private Integer maxOccupants;
    private String addressStreet;
    private String district;
    private String city;
    private Double latitude;
    private Double longitude;
    private RoomStatus status;
    private Boolean isVerified;
    private Boolean isBoosted;
    private Long viewCount;
    private Instant createdAt;
}
