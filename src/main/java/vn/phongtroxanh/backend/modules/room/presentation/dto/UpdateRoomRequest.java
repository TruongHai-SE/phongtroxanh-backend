package vn.phongtroxanh.backend.modules.room.presentation.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
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

    @Size(min = 1, max = 255)
    private String title;
    private String description;
    @Size(min = 1, max = 50)
    private String roomType;

    @Positive(message = "Giá thuê phải lớn hơn 0")
    private BigDecimal price;

    @PositiveOrZero(message = "Tiền đặt cọc không được âm")
    private BigDecimal depositAmount;

    @Positive(message = "Diện tích phải lớn hơn 0")
    private BigDecimal areaSqm;

    @PositiveOrZero
    private Integer floorNumber;
    @Positive
    private Integer maxOccupants;
    @Size(min = 1, max = 255)
    private String addressStreet;
    @Size(min = 1, max = 100)
    private String district;
    @Size(min = 1, max = 100)
    private String city;
    private Double latitude;
    private Double longitude;
    private RoomStatus status;
    @Valid
    private List<RoomFeeDTO> fees;

    private List<String> amenities;
    private List<String> images;
}
