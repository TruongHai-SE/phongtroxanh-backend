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
    private String roomDistrict;

    private UUID tenantId;
    private String tenantName;
    private String tenantPhone;
    private String tenantEmail;
    private String tenantAvatar;
    private Integer tenantTrustScore;
    private Boolean tenantVerified;
    private String tenantSchool;
    private String tenantGender;
    private Integer tenantBirthYear;
    private String tenantBio;
    private String tenantMessage;

    private UUID landlordId;
    private String landlordName;
    private String landlordPhone;
    private String landlordEmail;
    private String landlordAvatar;
    private Integer landlordTrustScore;
    private Boolean landlordVerified;

    private LocalDate viewingDate;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal monthlyRent;
    private BigDecimal depositAmount;
    private RentalStatus status;
    private String approvalStatus;
    private Instant checkInAt;
    private Instant createdAt;
}
