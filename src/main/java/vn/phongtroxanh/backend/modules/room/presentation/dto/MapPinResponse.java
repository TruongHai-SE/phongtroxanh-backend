package vn.phongtroxanh.backend.modules.room.presentation.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MapPinResponse {

    private UUID id;
    private String title;
    private BigDecimal price;
    private Double latitude;
    private Double longitude;
    private String primaryImageUrl;
    private String roomType;
    private Boolean isBoosted;
    private String district;
    private BigDecimal areaSqm;

    public Double getLat() {
        return latitude;
    }

    public Double getLng() {
        return longitude;
    }

    public String getImage() {
        return primaryImageUrl;
    }

    public String getType() {
        return roomType;
    }
}
