package vn.phongtroxanh.backend.modules.room.presentation.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomFeeDTO {
    private UUID id;
    private String feeLabel;
    private String feeValue;
}
