package vn.phongtroxanh.backend.modules.room.presentation.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;
import vn.phongtroxanh.backend.modules.room.domain.RoomStatus;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRoomRequest {

    private String title;
    private String description;
    private String roomType;

    @Positive(message = "Giá thuê phải lớn hơn 0")
    private BigDecimal price;

    @PositiveOrZero(message = "Tiền đặt cọc không được âm")
    private BigDecimal depositAmount;

    @Positive(message = "Diện tích phải lớn hơn 0")
    private BigDecimal areaSqm;

    private Integer floorNumber;
    private Integer maxOccupants;
    private String addressStreet;
    private String district;
    private String city;
    private Double latitude;
    private Double longitude;
    private RoomStatus status;
    private List<RoomFeeDTO> fees;
}
