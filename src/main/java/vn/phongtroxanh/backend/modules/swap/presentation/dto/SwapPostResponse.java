package vn.phongtroxanh.backend.modules.swap.presentation.dto;

import lombok.*;
import vn.phongtroxanh.backend.modules.swap.domain.SwapStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SwapPostResponse {

    private UUID id;
    private UUID requesterId;
    private UUID userId;
    private String authorName;
    private String authorAvatar;
    private UUID currentRoomId;
    private Boolean isLeaseholder;
    private String title;
    private String description;
    private String reason;
    private List<String> targetDistricts;
    private List<String> desiredDistricts;
    private String targetRoomType;
    private BigDecimal targetBudgetMax;
    private BigDecimal desiredPriceMax;
    private List<String> habits;
    private LocalDate targetMoveInDate;
    private LocalDate moveInDate;
    private UUID matchedTenantId;
    private UUID landlordId;
    private SwapStatus landlordDecision;
    private SwapStatus status;
    private String currentRoomTitle;
    private BigDecimal currentRoomPrice;
    private String currentRoomDistrict;
    private BigDecimal currentRoomArea;
    private List<String> currentRoomImages;
    private String authorSchool;
    private Instant createdAt;
}
