package vn.phongtroxanh.backend.modules.admin.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.phongtroxanh.backend.common.entity.BaseEntity;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "reports")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Report extends BaseEntity {

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @Column(name = "target_type", length = 20, nullable = false)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "report_type", length = 100, nullable = false)
    private String reportType;

    @Column(name = "detail", columnDefinition = "TEXT", nullable = false)
    private String detail;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "evidence_images", columnDefinition = "varchar(500)[]")
    private List<String> evidenceImages;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    @Builder.Default
    private ReportStatus status = ReportStatus.NEW;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity")
    @Builder.Default
    private ReportSeverity severity = ReportSeverity.MEDIUM;

    @Column(name = "handler_id")
    private UUID handlerId;
}
