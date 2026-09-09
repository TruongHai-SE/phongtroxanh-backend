package vn.phongtroxanh.backend.modules.swap.presentation.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendSwapProposalRequest {
    private UUID offeredRoomId;
    private String message;
}
