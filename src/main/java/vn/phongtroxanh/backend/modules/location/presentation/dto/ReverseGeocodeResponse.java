package vn.phongtroxanh.backend.modules.location.presentation.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReverseGeocodeResponse {
    private String formattedAddress;
}
