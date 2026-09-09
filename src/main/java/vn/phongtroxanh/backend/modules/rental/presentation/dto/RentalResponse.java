package vn.phongtroxanh.backend.modules.rental.presentation.dto;

import lombok.*;
import vn.phongtroxanh.backend.modules.rental.domain.RentalStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentalResponse {

    private UUID id;
    private UUID roomId;
    private String roomTitle;
    private String roomAddress;

    private UUID tenantId;
    private String tenantName;
    private String tenantPhone;
    private String tenantAvatar;

    private UUID landlordId;
    private String landlordName;
    private String landlordPhone;

    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal monthlyRent;
    private BigDecimal depositAmount;
    private RentalStatus status;
    private Instant checkInAt;
    private Instant createdAt;
}
