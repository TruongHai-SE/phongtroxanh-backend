package vn.phongtroxanh.backend.modules.rental.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "rental_contracts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Rental {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "landlord_id", nullable = false)
    private UUID landlordId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    @Builder.Default
    private RentalStatus status = RentalStatus.PENDING_CHECKIN;

    @Column(name = "is_leaseholder")
    @Builder.Default
    private Boolean isLeaseholder = true;

    @Column(name = "check_in_code", length = 16, nullable = false)
    private String checkInCode;

    @Column(name = "check_in_qr_token", length = 255, unique = true, nullable = false)
    private String checkInQrToken;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Version
    @Column(name = "version")
    @Builder.Default
    private Long version = 0L;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();
}
