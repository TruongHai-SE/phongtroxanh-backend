package vn.phongtroxanh.backend.modules.rental.presentation.dto;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckInQrResponse {

    private UUID rentalId;
    private String qrCodePayload;
    private Instant expiresAt;
}
