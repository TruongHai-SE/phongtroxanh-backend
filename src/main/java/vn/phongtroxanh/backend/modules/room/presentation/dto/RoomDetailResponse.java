package vn.phongtroxanh.backend.modules.room.presentation.dto;

import lombok.*;
import vn.phongtroxanh.backend.modules.room.domain.RoomStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomDetailResponse {

    private UUID id;
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
    private Instant updatedAt;
    private Instant expiresAt;

    private List<RoomImageDTO> images;
    private List<RoomFeeDTO> fees;
    private List<String> amenities;
    private LandlordSummaryDTO landlord;
    private Boolean isSaved;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LandlordSummaryDTO {
        private UUID id;
        private String fullName;
        private String avatarUrl;
        private String phoneNumber;
        private Integer trustScore;
        private Boolean isVerified;
    }
}
