package vn.phongtroxanh.backend.modules.admin.presentation.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
    private long totalTransactions;
    private long totalReviews;
    private double averageRating;
    private double landlordAverageRating;
    private long verifiedUsersCount;
    private long pendingReportsCount;
    private long totalSwipes;
    private long totalSwapRequests;
    private long activeBoostedRooms;

    // Dữ liệu phân tích chuyên sâu cho các Tab (100% từ Database thực)
    private List<MonthlyStatDTO> monthlyStats;
    private List<TrustScoreRangeDTO> trustScoreDistribution;
    private List<RatingBreakdownDTO> ratingDistribution;
    private List<RecentReviewDTO> recentReviews;
    private List<SystemFeedbackItemDTO> systemFeedbacks;
    private List<PackageRevenueDTO> packageStats;
    private List<CategoryStatDTO> roomTypeDistribution;
    private List<CategoryStatDTO> districtDistribution;
    private List<CategoryStatDTO> roomStatusDistribution;
    private List<CategoryStatDTO> lifestyleStats;
    private List<CategoryStatDTO> personalizationFunnel;
    private List<DistrictSupplyDemandDTO> districtSupplyDemand;
    private KycOverviewDTO kycOverview;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DistrictSupplyDemandDTO {
        private String district;
        private long supply;
        private long demand;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SystemFeedbackItemDTO {
        private UUID id;
        private UUID userId;
        private String userName;
        private String userRole;
        private int rating;
        private String category;
        private String comment;
        private Instant createdAt;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryStatDTO {
        private String name;
        private long count;
        private double percentage;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KycOverviewDTO {
        private long pendingCount;
        private long approvedCount;
        private long rejectedCount;
        private double approvalRate;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyStatDTO {
        private String month;
        private long newUsers;
        private long newRentals;
        private BigDecimal revenue;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrustScoreRangeDTO {
        private String range;
        private long count;
        private double percentage;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RatingBreakdownDTO {
        private int star;
        private long count;
        private double percentage;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentReviewDTO {
        private UUID id;
        private String reviewerName;
        private String reviewerRole;
        private String landlordName;
        private Boolean isVerifiedStay;
        private int rating;
        private String comment;
        private String landlordReply;
        private Instant repliedAt;
        private String roomTitle;
        private Instant createdAt;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PackageRevenueDTO {
        private String planId;
        private String planName;
        private BigDecimal revenue;
        private long count;
    }
}
