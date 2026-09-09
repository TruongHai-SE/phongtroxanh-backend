package vn.phongtroxanh.backend.modules.admin.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "system_audit_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "action", length = 100, nullable = false)
    private String action;

    @Column(name = "target_entity", length = 50, nullable = false)
    private String targetEntity;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_before", columnDefinition = "jsonb")
    private String payloadBefore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_after", columnDefinition = "jsonb")
    private String payloadAfter;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();
}
