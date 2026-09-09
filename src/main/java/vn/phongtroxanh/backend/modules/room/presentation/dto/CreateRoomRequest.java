package vn.phongtroxanh.backend.modules.room.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRoomRequest {

    @NotBlank(message = "Tiêu đề không được để trống")
    private String title;

    @NotBlank(message = "Mô tả không được để trống")
    private String description;

    @NotBlank(message = "Loại phòng không được để trống")
    private String roomType;

    @NotNull(message = "Giá thuê không được để trống")
    @Positive(message = "Giá thuê phải lớn hơn 0")
    private BigDecimal price;

    @NotNull(message = "Tiền đặt cọc không được để trống")
    private BigDecimal depositAmount;

    @NotNull(message = "Diện tích không được để trống")
    @Positive(message = "Diện tích phải lớn hơn 0")
    private BigDecimal areaSqm;

    private Integer floorNumber;
    private Integer maxOccupants;

    @NotBlank(message = "Địa chỉ đường/số nhà không được để trống")
    private String addressStreet;

    @NotBlank(message = "Quận/Huyện không được để trống")
    private String district;

    private String city;
    private Double latitude;
    private Double longitude;

    private List<RoomFeeDTO> fees;
}
