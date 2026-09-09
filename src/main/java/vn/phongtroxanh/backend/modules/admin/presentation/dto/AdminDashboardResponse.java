package vn.phongtroxanh.backend.modules.admin.presentation.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardResponse {

    private long totalUsers;
    private long totalLandlords;
    private long totalTenants;
    private long totalRooms;
    private long activeRooms;
    private long totalMatches;
    private long totalRentals;
    private long activeRentals;
    private long pendingKycCount;
    private long pendingDisputesCount;
    private BigDecimal totalRevenue;
}
