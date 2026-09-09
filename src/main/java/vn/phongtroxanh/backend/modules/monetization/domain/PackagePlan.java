package vn.phongtroxanh.backend.modules.monetization.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.phongtroxanh.backend.modules.user.domain.UserRole;

import java.math.BigDecimal;

@Entity
@Table(name = "package_plans")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PackagePlan {

    @Id
    @Column(name = "id", length = 50, nullable = false)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_role", nullable = false)
    private UserRole targetRole;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "price_monthly", precision = 12, scale = 2, nullable = false)
    private BigDecimal priceMonthly;

    @Column(name = "price_yearly", precision = 12, scale = 2, nullable = false)
    private BigDecimal priceYearly;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "features", columnDefinition = "jsonb", nullable = false)
    private String features;
}
