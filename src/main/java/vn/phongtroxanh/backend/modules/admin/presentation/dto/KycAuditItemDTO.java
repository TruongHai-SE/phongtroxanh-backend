package vn.phongtroxanh.backend.modules.admin.presentation.dto;

import lombok.*;
import vn.phongtroxanh.backend.modules.user.domain.UserRole;
import vn.phongtroxanh.backend.modules.user.domain.VerificationStatus;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycAuditItemDTO {

    private UUID verificationId;
    private UUID userId;
    private String userFullName;
    private String userEmail;
    private String userPhone;
    private UserRole userRole;
    private String idCardNumberDecrypted;
    private String idCardFrontUrl;
    private String idCardBackUrl;
    private VerificationStatus status;
    private Instant createdAt;
}
