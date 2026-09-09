package vn.phongtroxanh.backend.modules.room.presentation.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomCompareResponse {
    private List<RoomDetailResponse> rooms;
}
