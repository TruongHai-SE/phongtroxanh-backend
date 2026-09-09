package vn.phongtroxanh.backend.modules.user.presentation.dto;

import lombok.*;
import vn.phongtroxanh.backend.modules.user.domain.VerificationStatus;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycStatusResponse {

    private UUID verificationId;
    private VerificationStatus status;
    private String rejectionReason;
    private Instant createdAt;
    private Instant reviewedAt;
}
