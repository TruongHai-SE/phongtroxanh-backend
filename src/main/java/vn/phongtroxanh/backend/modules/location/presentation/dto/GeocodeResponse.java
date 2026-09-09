package vn.phongtroxanh.backend.modules.location.presentation.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeocodeResponse {
    private double latitude;
    private double longitude;
    private String formattedAddress;
}
