package vn.phongtroxanh.backend.modules.swap.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.phongtroxanh.backend.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "swap_requests")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SwapRequest extends BaseEntity {

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Column(name = "current_room_id", nullable = false)
    private UUID currentRoomId;

    @Column(name = "is_leaseholder")
    @Builder.Default
    private Boolean isLeaseholder = false;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "target_districts", columnDefinition = "varchar(100)[]")
    private List<String> targetDistricts;

    @Column(name = "target_room_type", length = 50)
    private String targetRoomType;

    @Column(name = "target_budget_max", precision = 12, scale = 2)
    private BigDecimal targetBudgetMax;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "habits", columnDefinition = "varchar(100)[]")
    private List<String> habits;

    @Column(name = "reason", columnDefinition = "TEXT", nullable = false)
    private String reason;

    @Column(name = "target_move_in_date")
    private LocalDate targetMoveInDate;

    @Column(name = "matched_tenant_id")
    private UUID matchedTenantId;

    @Column(name = "landlord_id")
    private UUID landlordId;

    @Enumerated(EnumType.STRING)
    @Column(name = "landlord_decision")
    @Builder.Default
    private SwapStatus landlordDecision = SwapStatus.PENDING_LANDLORD;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    @Builder.Default
    private SwapStatus status = SwapStatus.OPEN;
}
