package vn.phongtroxanh.backend.modules.room.presentation.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomImageDTO {
    private UUID id;
    private String imageUrl;
    private Boolean isPrimary;
    private Integer displayOrder;
}
