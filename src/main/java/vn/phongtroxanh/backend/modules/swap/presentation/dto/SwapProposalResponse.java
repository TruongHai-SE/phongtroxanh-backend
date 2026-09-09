package vn.phongtroxanh.backend.modules.swap.presentation.dto;

import lombok.*;
import vn.phongtroxanh.backend.modules.swap.domain.SwapStatus;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SwapProposalResponse {

    private UUID id;
    private UUID swapPostId;
    private UUID requesterId;
    private String requesterName;
    private UUID offeredRoomId;
    private String message;
    private SwapStatus status;
    private Instant createdAt;
}
