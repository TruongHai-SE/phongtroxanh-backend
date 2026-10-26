package vn.phongtroxanh.backend.modules.admin.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import vn.phongtroxanh.backend.common.exception.BadRequestException;
import vn.phongtroxanh.backend.common.exception.ResourceNotFoundException;
import vn.phongtroxanh.backend.common.security.SecurityUtils;
import vn.phongtroxanh.backend.modules.admin.domain.*;
import vn.phongtroxanh.backend.modules.admin.infrastructure.repository.ReportRepository;
import vn.phongtroxanh.backend.modules.admin.infrastructure.repository.SystemAuditLogRepository;
import vn.phongtroxanh.backend.modules.admin.presentation.dto.*;
import vn.phongtroxanh.backend.modules.matching.infrastructure.repository.MatchRepository;
import vn.phongtroxanh.backend.modules.matching.infrastructure.repository.SwipeRepository;
import vn.phongtroxanh.backend.modules.monetization.domain.PaymentStatus;
import vn.phongtroxanh.backend.modules.monetization.domain.PaymentTransaction;
import vn.phongtroxanh.backend.modules.monetization.infrastructure.repository.PaymentTransactionRepository;
import vn.phongtroxanh.backend.modules.rental.domain.Rental;
import vn.phongtroxanh.backend.modules.rental.domain.RentalStatus;
import vn.phongtroxanh.backend.modules.rental.infrastructure.repository.RentalRepository;
import vn.phongtroxanh.backend.modules.review.domain.Review;
import vn.phongtroxanh.backend.modules.review.domain.ReviewDispute;
import vn.phongtroxanh.backend.modules.review.domain.ReviewDisputeStatus;
import vn.phongtroxanh.backend.modules.review.infrastructure.repository.ReviewDisputeRepository;
import vn.phongtroxanh.backend.modules.review.infrastructure.repository.ReviewRepository;
import vn.phongtroxanh.backend.modules.review.infrastructure.repository.SystemFeedbackRepository;
import vn.phongtroxanh.backend.modules.room.domain.Room;
import vn.phongtroxanh.backend.modules.room.domain.RoomStatus;
import vn.phongtroxanh.backend.modules.room.infrastructure.repository.RoomRepository;
import vn.phongtroxanh.backend.modules.room.infrastructure.repository.RoomSwipeRepository;
import vn.phongtroxanh.backend.modules.swap.domain.SwapRequest;
import vn.phongtroxanh.backend.modules.swap.infrastructure.repository.SwapRequestRepository;
import vn.phongtroxanh.backend.modules.user.application.service.UserService;
import vn.phongtroxanh.backend.modules.user.domain.*;
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.*;

import org.springframework.security.crypto.password.PasswordEncoder;
import vn.phongtroxanh.backend.common.mail.EmailNotificationPort;
import vn.phongtroxanh.backend.modules.notification.application.service.NotificationService;
import vn.phongtroxanh.backend.modules.notification.domain.NotificationType;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserConsumableRepository userConsumableRepository;
    private final UserVerificationRepository userVerificationRepository;
    private final TrustScoreLogRepository trustScoreLogRepository;
    private final RoomRepository roomRepository;
    private final RoomSwipeRepository roomSwipeRepository;
    private final MatchRepository matchRepository;
    private final SwipeRepository swipeRepository;
    private final SwapRequestRepository swapRequestRepository;
    private final RentalRepository rentalRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewDisputeRepository reviewDisputeRepository;
    private final SystemFeedbackRepository systemFeedbackRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final ReportRepository reportRepository;
    private final SystemAuditLogRepository systemAuditLogRepository;
    private final EntityManager entityManager;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    private final EmailNotificationPort emailNotificationPort;
    private final UserService userService;

    public AdminDashboardResponse getDashboard() {
        List<User> allUsers = userRepository.findAll();
        long totalUsers = allUsers.size();
        long totalLandlords = allUsers.stream().filter(u -> u.getRole() == UserRole.LANDLORD).count();
        long totalTenants = allUsers.stream().filter(u -> u.getRole() == UserRole.TENANT).count();
        long totalRooms = roomRepository.count();
        long activeRooms = roomRepository.findAll().stream().filter(r -> r.getStatus() == RoomStatus.AVAILABLE).count();
        long totalMatches = matchRepository.count();
        long totalRentals = rentalRepository.count();
        long activeRentals = rentalRepository.findAll().stream().filter(r -> r.getStatus() == RentalStatus.CHECKED_IN).count();
        long pendingKyc = userVerificationRepository.findByStatus(VerificationStatus.PENDING, Pageable.unpaged()).getTotalElements();
        long pendingDisputes = reviewDisputeRepository.findByStatus("PENDING_REVIEW", Pageable.unpaged()).getTotalElements();
        long pendingReports = reportRepository.findByStatusOrderByCreatedAtDesc(ReportStatus.NEW, Pageable.unpaged()).getTotalElements()
                + reportRepository.findByStatusOrderByCreatedAtDesc(ReportStatus.INVESTIGATING, Pageable.unpaged()).getTotalElements();
        long totalSwipes = swipeRepository.count() + roomSwipeRepository.count();
        List<SwapRequest> allSwaps = swapRequestRepository.findAll();
        long totalSwapRequests = allSwaps.size();

        List<PaymentTransaction> successfulTx = paymentTransactionRepository.findAll().stream()
                .filter(t -> t.getStatus() == PaymentStatus.SUCCESS)
                .toList();

        long totalTransactions = successfulTx.size();

        BigDecimal totalRevenue = successfulTx.stream()
                .map(PaymentTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Review> allReviews = reviewRepository.findAll();
        long totalReviews = allReviews.size();
        double averageRating = allReviews.stream()
                .mapToInt(Review::getRating)
                .average()
                .orElse(0.0);
        averageRating = Math.round(averageRating * 10.0) / 10.0;

        double landlordAverageRating = allReviews.stream()
                .filter(r -> {
                    if (r.getRoomId() != null) return true;
                    User reviewee = userRepository.findById(r.getRevieweeId()).orElse(null);
                    return reviewee != null && reviewee.getRole() == UserRole.LANDLORD;
                })
                .mapToInt(Review::getRating)
                .average()
                .orElse(averageRating > 0 ? averageRating : 0.0);
        landlordAverageRating = Math.round(landlordAverageRating * 10.0) / 10.0;

        long verifiedUsersCount = allUsers.stream()
                .filter(u -> Boolean.TRUE.equals(u.getIsVerified()))
                .count();

        // 1. Phân bố điểm uy tín TrustScore thật
        long c0_49 = allUsers.stream().filter(u -> u.getTrustScore() < 50).count();
        long c50_69 = allUsers.stream().filter(u -> u.getTrustScore() >= 50 && u.getTrustScore() < 70).count();
        long c70_84 = allUsers.stream().filter(u -> u.getTrustScore() >= 70 && u.getTrustScore() < 85).count();
        long c85_100 = allUsers.stream().filter(u -> u.getTrustScore() >= 85).count();
        List<AdminDashboardResponse.TrustScoreRangeDTO> trustScoreDistribution = List.of(
                new AdminDashboardResponse.TrustScoreRangeDTO("Dưới 50 (Cần cải thiện)", c0_49, totalUsers > 0 ? Math.round(c0_49 * 100.0 / totalUsers) : 0),
                new AdminDashboardResponse.TrustScoreRangeDTO("50 - 69 (Tiêu chuẩn)", c50_69, totalUsers > 0 ? Math.round(c50_69 * 100.0 / totalUsers) : 0),
                new AdminDashboardResponse.TrustScoreRangeDTO("70 - 84 (Uy tín cao)", c70_84, totalUsers > 0 ? Math.round(c70_84 * 100.0 / totalUsers) : 0),
                new AdminDashboardResponse.TrustScoreRangeDTO("85 - 100 (Xuất sắc)", c85_100, totalUsers > 0 ? Math.round(c85_100 * 100.0 / totalUsers) : 0)
        );

        // 2. Thống kê tăng trưởng 6 tháng gần nhất từ DB
        List<AdminDashboardResponse.MonthlyStatDTO> monthlyStats = new ArrayList<>();
        java.time.YearMonth currentYm = java.time.YearMonth.now();
        List<Rental> allRentals = rentalRepository.findAll();
        for (int i = 5; i >= 0; i--) {
            java.time.YearMonth ym = currentYm.minusMonths(i);
            Instant start = ym.atDay(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
            Instant end = ym.plusMonths(1).atDay(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();

            long newUsers = allUsers.stream().filter(u -> u.getCreatedAt() != null && !u.getCreatedAt().isBefore(start) && u.getCreatedAt().isBefore(end)).count();
            long newRentals = allRentals.stream().filter(r -> r.getCreatedAt() != null && !r.getCreatedAt().isBefore(start) && r.getCreatedAt().isBefore(end)).count();
            BigDecimal rev = successfulTx.stream()
                    .filter(t -> t.getCreatedAt() != null && !t.getCreatedAt().isBefore(start) && t.getCreatedAt().isBefore(end))
                    .map(PaymentTransaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            monthlyStats.add(new AdminDashboardResponse.MonthlyStatDTO("T" + ym.getMonthValue(), newUsers, newRentals, rev));
        }

        // 3. Phân bố sao đánh giá thật
        List<AdminDashboardResponse.RatingBreakdownDTO> ratingDistribution = new ArrayList<>();
        for (int star = 5; star >= 1; star--) {
            int finalStar = star;
            long count = allReviews.stream().filter(r -> r.getRating() != null && r.getRating() == finalStar).count();
            double pct = totalReviews > 0 ? Math.round(count * 100.0 / totalReviews) : 0;
            ratingDistribution.add(new AdminDashboardResponse.RatingBreakdownDTO(star, count, pct));
        }

        // 4. Danh sách đánh giá từ DB (lấy tối đa 100 đánh giá gần đây nhất để admin lọc và theo dõi)
        List<AdminDashboardResponse.RecentReviewDTO> recentReviews = allReviews.stream()
                .sorted((a, b) -> (b.getCreatedAt() != null ? b.getCreatedAt() : Instant.MIN)
                        .compareTo(a.getCreatedAt() != null ? a.getCreatedAt() : Instant.MIN))
                .limit(100)
                .map(r -> {
                    User reviewer = userRepository.findById(r.getReviewerId()).orElse(null);
                    String name = reviewer != null ? reviewer.getFullName() : "Người dùng Phòng Trọ Xanh";
                    String roleStr = (reviewer != null && reviewer.getRole() == UserRole.LANDLORD) ? "Chủ trọ" : "Người thuê";

                    User landlord = r.getRevieweeId() != null ? userRepository.findById(r.getRevieweeId()).orElse(null) : null;
                    String lName = landlord != null ? landlord.getFullName() : "Chủ trọ hệ thống";

                    String rTitle = "Phòng trọ cao cấp";
                    if (r.getRoomId() != null) {
                        Room room = roomRepository.findById(r.getRoomId()).orElse(null);
                        if (room != null && room.getTitle() != null) {
                            rTitle = room.getTitle();
                        }
                    }
                    return AdminDashboardResponse.RecentReviewDTO.builder()
                            .id(r.getId())
                            .reviewerName(name)
                            .reviewerRole(roleStr)
                            .landlordName(lName)
                            .isVerifiedStay(r.getIsVerifiedStay() != null ? r.getIsVerifiedStay() : true)
                            .rating(r.getRating() != null ? r.getRating() : 5)
                            .comment(r.getComment())
                            .landlordReply(r.getLandlordReply())
                            .repliedAt(r.getRepliedAt())
                            .roomTitle(rTitle)
                            .createdAt(r.getCreatedAt())
                            .build();
                })
                .toList();

        // 5. Thống kê doanh thu theo gói dịch vụ
        Map<String, List<PaymentTransaction>> byPlan = successfulTx.stream()
                .collect(java.util.stream.Collectors.groupingBy(t -> t.getItemName() != null ? t.getItemName() : "Gói dịch vụ"));
        List<AdminDashboardResponse.PackageRevenueDTO> packageStats = new ArrayList<>();
        byPlan.forEach((planName, txList) -> {
            BigDecimal sum = txList.stream().map(PaymentTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            packageStats.add(new AdminDashboardResponse.PackageRevenueDTO(planName, planName, sum, (long) txList.size()));
        });

        List<Room> allRoomsList = roomRepository.findAll();
        long activeBoostedRooms = allRoomsList.stream().filter(r -> Boolean.TRUE.equals(r.getIsBoosted())).count();

        // 6. Phân bố loại phòng thực tế từ DB
        Map<String, Long> roomTypeCounts = allRoomsList.stream()
                .filter(r -> r.getRoomType() != null)
                .collect(java.util.stream.Collectors.groupingBy(r -> {
                    String t = r.getRoomType();
                    if ("PHONG_KHEP_KIN".equalsIgnoreCase(t)) return "Phòng khép kín";
                    if ("KTX_SLEEPBOX".equalsIgnoreCase(t) || "KTX".equalsIgnoreCase(t)) return "KTX / Sleepbox";
                    if ("CAN_HO_MINI".equalsIgnoreCase(t) || "CHUNG_CU_MINI".equalsIgnoreCase(t)) return "Căn hộ mini";
                    if ("NHA_NGUYEN_CAN".equalsIgnoreCase(t)) return "Nhà nguyên căn";
                    if ("PHONG_OGHEP".equalsIgnoreCase(t)) return "Phòng ở ghép";
                    if ("PHONG_TRO".equalsIgnoreCase(t)) return "Phòng trọ truyền thống";
                    return t;
                }, java.util.stream.Collectors.counting()));

        List<AdminDashboardResponse.CategoryStatDTO> roomTypeDistribution = new ArrayList<>();
        roomTypeCounts.forEach((name, count) -> {
            double pct = totalRooms > 0 ? Math.round(count * 100.0 / totalRooms) : 0;
            roomTypeDistribution.add(new AdminDashboardResponse.CategoryStatDTO(name, count, pct));
        });
        roomTypeDistribution.sort((a, b) -> Long.compare(b.getCount(), a.getCount()));

        // 7. Phân bố phòng theo Quận/Huyện thực tế từ DB
        Map<String, Long> districtCounts = allRoomsList.stream()
                .filter(r -> r.getDistrict() != null && !r.getDistrict().isBlank())
                .collect(java.util.stream.Collectors.groupingBy(Room::getDistrict, java.util.stream.Collectors.counting()));

        List<AdminDashboardResponse.CategoryStatDTO> districtDistribution = new ArrayList<>();
        districtCounts.forEach((name, count) -> {
            double pct = totalRooms > 0 ? Math.round(count * 100.0 / totalRooms) : 0;
            districtDistribution.add(new AdminDashboardResponse.CategoryStatDTO(name, count, pct));
        });
        districtDistribution.sort((a, b) -> Long.compare(b.getCount(), a.getCount()));
        List<AdminDashboardResponse.CategoryStatDTO> topDistricts = districtDistribution.stream().limit(6).toList();

        // 8. Phân bố trạng thái phòng
        long stAvailable = allRoomsList.stream().filter(r -> r.getStatus() == RoomStatus.AVAILABLE).count();
        long stRented = allRoomsList.stream().filter(r -> r.getStatus() == RoomStatus.RENTED).count();
        long stHidden = totalRooms - stAvailable - stRented;
        List<AdminDashboardResponse.CategoryStatDTO> roomStatusDistribution = List.of(
                new AdminDashboardResponse.CategoryStatDTO("Sẵn sàng cho thuê (Available)", stAvailable, totalRooms > 0 ? Math.round(stAvailable * 100.0 / totalRooms) : 0),
                new AdminDashboardResponse.CategoryStatDTO("Đang có người thuê (Rented)", stRented, totalRooms > 0 ? Math.round(stRented * 100.0 / totalRooms) : 0),
                new AdminDashboardResponse.CategoryStatDTO("Tạm ẩn / Đang bảo trì", stHidden, totalRooms > 0 ? Math.round(stHidden * 100.0 / totalRooms) : 0)
        );

        // 9. Thống kê duyệt CCCD toàn hệ thống
        List<UserVerification> allVerifications = userVerificationRepository.findAll();
        long kycPending = allVerifications.stream().filter(v -> v.getStatus() == VerificationStatus.PENDING).count();
        long kycApproved = allVerifications.stream().filter(v -> v.getStatus() == VerificationStatus.APPROVED).count();
        long kycRejected = allVerifications.stream().filter(v -> v.getStatus() == VerificationStatus.REJECTED).count();
        double approvalRate = (kycApproved + kycRejected) > 0 ? Math.round(kycApproved * 100.0 / (kycApproved + kycRejected)) : 100.0;
        AdminDashboardResponse.KycOverviewDTO kycOverview = AdminDashboardResponse.KycOverviewDTO.builder()
                .pendingCount(kycPending)
                .approvedCount(kycApproved)
                .rejectedCount(kycRejected)
                .approvalRate(approvalRate)
                .build();

        // 10. Thống kê hồ sơ cá nhân hóa (UserProfile) & Tương quan Cung - Cầu theo Quận
        List<UserProfile> allProfiles = userProfileRepository.findAll();
        List<UserProfile> surveyedProfiles = allProfiles.stream()
                .filter(p -> p.getNonSmoking() != null || p.getEarlySleeper() != null
                        || p.getIsNeat() != null || p.getAllowGuests() != null
                        || p.getProximitySchool() != null || p.getProximityMarket() != null)
                .toList();
        long profileTotal = surveyedProfiles.size();
        long nonSmokingCount = surveyedProfiles.stream().filter(p -> Boolean.TRUE.equals(p.getNonSmoking())).count();
        long earlySleeperCount = surveyedProfiles.stream().filter(p -> Boolean.TRUE.equals(p.getEarlySleeper())).count();
        long neatCount = surveyedProfiles.stream().filter(p -> Boolean.TRUE.equals(p.getIsNeat()) || (p.getCleanlinessLevel() != null && p.getCleanlinessLevel() >= 4)).count();
        long allowGuestsCount = surveyedProfiles.stream().filter(p -> Boolean.TRUE.equals(p.getAllowGuests())).count();
        long proxSchoolCount = surveyedProfiles.stream().filter(p -> Boolean.TRUE.equals(p.getProximitySchool())).count();
        long proxMarketCount = surveyedProfiles.stream().filter(p -> Boolean.TRUE.equals(p.getProximityMarket())).count();
        long proxBusCount = surveyedProfiles.stream().filter(p -> Boolean.TRUE.equals(p.getProximityBus())).count();
        long noiseTolerantCount = surveyedProfiles.stream().filter(p -> p.getNoiseTolerance() != null && p.getNoiseTolerance() >= 50).count();

        List<AdminDashboardResponse.CategoryStatDTO> lifestyleStats = List.of(
                new AdminDashboardResponse.CategoryStatDTO("Không hút thuốc", nonSmokingCount, profileTotal > 0 ? Math.round(nonSmokingCount * 100.0 / profileTotal) : 0),
                new AdminDashboardResponse.CategoryStatDTO("Ngủ sớm (<23h)", earlySleeperCount, profileTotal > 0 ? Math.round(earlySleeperCount * 100.0 / profileTotal) : 0),
                new AdminDashboardResponse.CategoryStatDTO("Ưa gọn gàng", neatCount, profileTotal > 0 ? Math.round(neatCount * 100.0 / profileTotal) : 0),
                new AdminDashboardResponse.CategoryStatDTO("Cho phép khách", allowGuestsCount, profileTotal > 0 ? Math.round(allowGuestsCount * 100.0 / profileTotal) : 0),
                new AdminDashboardResponse.CategoryStatDTO("Gần Trường học", proxSchoolCount, profileTotal > 0 ? Math.round(proxSchoolCount * 100.0 / profileTotal) : 0),
                new AdminDashboardResponse.CategoryStatDTO("Gần Chợ / Siêu thị", proxMarketCount, profileTotal > 0 ? Math.round(proxMarketCount * 100.0 / profileTotal) : 0),
                new AdminDashboardResponse.CategoryStatDTO("Gần Trạm Bus", proxBusCount, profileTotal > 0 ? Math.round(proxBusCount * 100.0 / profileTotal) : 0),
                new AdminDashboardResponse.CategoryStatDTO("Chịu ồn (>=50%)", noiseTolerantCount, profileTotal > 0 ? Math.round(noiseTolerantCount * 100.0 / profileTotal) : 0)
        );

        long baseTenants = Math.max(totalTenants, 1);
        List<AdminDashboardResponse.CategoryStatDTO> personalizationFunnel = List.of(
                new AdminDashboardResponse.CategoryStatDTO("Người thuê đăng ký", totalTenants, totalTenants > 0 ? 100 : 0),
                new AdminDashboardResponse.CategoryStatDTO("Hoàn tất khảo sát", profileTotal, Math.min(100, Math.round(profileTotal * 100.0 / baseTenants))),
                new AdminDashboardResponse.CategoryStatDTO("Đã xác thực CCCD", verifiedUsersCount, Math.min(100, Math.round(verifiedUsersCount * 100.0 / Math.max(totalUsers, 1)))),
                new AdminDashboardResponse.CategoryStatDTO("Ghép đôi thành công", totalMatches, Math.min(100, Math.round(totalMatches * 100.0 / baseTenants))),
                new AdminDashboardResponse.CategoryStatDTO("Hợp đồng thuê", totalRentals, Math.min(100, Math.round(totalRentals * 100.0 / baseTenants)))
        );

        Map<String, Long> demandByDistrict = new HashMap<>();
        for (UserProfile p : allProfiles) {
            if (p.getPreferredDistricts() != null) {
                for (String d : p.getPreferredDistricts()) {
                    if (d != null && !d.isBlank()) {
                        demandByDistrict.merge(d.trim(), 1L, Long::sum);
                    }
                }
            }
        }
        for (SwapRequest sw : allSwaps) {
            if (sw.getTargetDistricts() != null) {
                for (String d : sw.getTargetDistricts()) {
                    if (d != null && !d.isBlank()) {
                        demandByDistrict.merge(d.trim(), 1L, Long::sum);
                    }
                }
            }
        }
        Set<String> allDistrictNames = new HashSet<>(districtCounts.keySet());
        allDistrictNames.addAll(demandByDistrict.keySet());
        List<AdminDashboardResponse.DistrictSupplyDemandDTO> districtSupplyDemand = allDistrictNames.stream()
                .map(d -> new AdminDashboardResponse.DistrictSupplyDemandDTO(
                        d,
                        districtCounts.getOrDefault(d, 0L),
                        demandByDistrict.getOrDefault(d, 0L)
                ))
                .sorted((a, b) -> Long.compare(b.getSupply() + b.getDemand(), a.getSupply() + a.getDemand()))
                .limit(6)
                .toList();

        List<AdminDashboardResponse.SystemFeedbackItemDTO> systemFeedbackDTOs = systemFeedbackRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 100)).getContent().stream()
                .map(fb -> {
                    String userName = "Người dùng ẩn danh";
                    String userRole = "TENANT";
                    if (fb.getUserId() != null) {
                        User u = userRepository.findById(fb.getUserId()).orElse(null);
                        if (u != null) {
                            userName = u.getFullName() != null ? u.getFullName() : u.getEmail();
                            userRole = u.getRole() != null ? u.getRole().name() : "TENANT";
                        }
                    }
                    return AdminDashboardResponse.SystemFeedbackItemDTO.builder()
                            .id(fb.getId())
                            .userId(fb.getUserId())
                            .userName(userName)
                            .userRole(userRole)
                            .rating(fb.getRating())
                            .category(fb.getCategory())
                            .comment(fb.getComment())
                            .createdAt(fb.getCreatedAt())
                            .build();
                })
                .toList();

        return AdminDashboardResponse.builder()
                .totalUsers(totalUsers)
                .totalLandlords(totalLandlords)
                .totalTenants(totalTenants)
                .totalRooms(totalRooms)
                .activeRooms(activeRooms)
                .totalMatches(totalMatches)
                .totalRentals(totalRentals)
                .activeRentals(activeRentals)
                .pendingKycCount(pendingKyc)
                .pendingDisputesCount(pendingDisputes)
                .pendingReportsCount(pendingReports)
                .totalSwipes(totalSwipes)
                .totalSwapRequests(totalSwapRequests)
                .activeBoostedRooms(activeBoostedRooms)
                .totalRevenue(totalRevenue)
                .totalTransactions(totalTransactions)
                .totalReviews(totalReviews)
                .averageRating(averageRating)
                .landlordAverageRating(landlordAverageRating)
                .verifiedUsersCount(verifiedUsersCount)
                .trustScoreDistribution(trustScoreDistribution)
                .monthlyStats(monthlyStats)
                .ratingDistribution(ratingDistribution)
                .recentReviews(recentReviews)
                .systemFeedbacks(systemFeedbackDTOs)
                .packageStats(packageStats)
                .roomTypeDistribution(roomTypeDistribution)
                .districtDistribution(topDistricts)
                .roomStatusDistribution(roomStatusDistribution)
                .lifestyleStats(lifestyleStats)
                .personalizationFunnel(personalizationFunnel)
                .districtSupplyDemand(districtSupplyDemand)
                .kycOverview(kycOverview)
                .build();
    }

    public Page<KycAuditItemDTO> getPendingKycPage(String search, UserRole role, int page, int limit) {
        List<UserVerification> list = userVerificationRepository.findByStatusOrderByCreatedAtAsc(VerificationStatus.PENDING, Pageable.unpaged()).getContent();
        List<KycAuditItemDTO> filtered = new ArrayList<>();
        String searchLower = (search != null && !search.isBlank()) ? search.trim().toLowerCase() : null;

        for (UserVerification v : list) {
            User user = userRepository.findById(v.getUserId()).orElse(null);
            if (role != null && (user == null || user.getRole() != role)) {
                continue;
            }

            String name = user != null ? user.getFullName() : "";
            String email = user != null ? user.getEmail() : "";
            String phone = user != null && user.getPhoneNumber() != null ? user.getPhoneNumber() : "";
            String idCard = v.getIdCardNumber() != null ? v.getIdCardNumber() : "";

            if (searchLower != null) {
                boolean match = (name != null && name.toLowerCase().contains(searchLower))
                        || (email != null && email.toLowerCase().contains(searchLower))
                        || phone.contains(searchLower)
                        || idCard.contains(searchLower);
                if (!match) continue;
            }

            filtered.add(KycAuditItemDTO.builder()
                    .verificationId(v.getId())
                    .userId(v.getUserId())
                    .userFullName(user != null ? user.getFullName() : "N/A")
                    .userEmail(user != null ? user.getEmail() : "N/A")
                    .userPhone(user != null ? user.getPhoneNumber() : null)
                    .userRole(user != null ? user.getRole() : null)
                    .idCardNumberDecrypted(v.getIdCardNumber()) // auto-decrypted via AES AttributeConverter
                    .idCardFrontUrl(v.getIdCardFrontUrl())
                    .idCardBackUrl(v.getIdCardBackUrl())
                    .status(v.getStatus())
                    .createdAt(v.getCreatedAt())
                    .build());
        }

        int pageSize = limit > 0 ? limit : 10;
        int total = filtered.size();
        int start = Math.min(page * pageSize, total);
        int end = Math.min(start + pageSize, total);
        List<KycAuditItemDTO> pagedList = filtered.subList(start, end);

        return new PageImpl<>(pagedList, PageRequest.of(page, pageSize), total);
    }

    public List<KycAuditItemDTO> getPendingKycList() {
        return getPendingKycPage(null, null, 0, 1000).getContent();
    }

    @Transactional
    public void approveKyc(UUID verificationId) {
        UserVerification verification = userVerificationRepository.findById(verificationId)
                .orElseThrow(() -> new ResourceNotFoundException("VERIFICATION_NOT_FOUND", "Không tìm thấy hồ sơ xác thực"));

        entityManager.refresh(verification, LockModeType.PESSIMISTIC_WRITE);
        if (verification.getStatus() != VerificationStatus.PENDING)
            throw new BadRequestException("KYC_ALREADY_REVIEWED", "Hồ sơ xác thực đã được xử lý");

        verification.setStatus(VerificationStatus.APPROVED);
        verification.setReviewedBy(SecurityUtils.getCurrentUserId());
        verification.setReviewedAt(Instant.now());
        userVerificationRepository.save(verification);

        // Update User TrustScore (+25 points) and mark verified
        userRepository.findById(verification.getUserId()).ifPresent(user -> {
            entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
            if (!Boolean.TRUE.equals(user.getIsVerified())) {
                user.setIsVerified(true);
                userRepository.save(user);
                int newScore = userService.recalculateAndSyncTrustScore(user.getId());

                trustScoreLogRepository.save(TrustScoreLog.builder()
                        .userId(user.getId())
                        .delta(25)
                        .finalScore(newScore)
                        .reason("Xác thực Căn cước công dân thành công (+25 điểm)")
                        .build());
            }

            List<Room> landlordRooms = roomRepository.findByLandlordIdOrderByCreatedAtDesc(user.getId());
            if (!landlordRooms.isEmpty()) {
                landlordRooms.forEach(r -> r.setIsVerified(true));
                roomRepository.saveAll(landlordRooms);
            }
        });

        recordAuditLog("APPROVE_KYC", "USER_VERIFICATION", verificationId, null, "APPROVED");
        log.info("Approved KYC verification {}", verificationId);
    }

    @Transactional
    public void rejectKyc(UUID verificationId, KycRejectRequest request) {
        UserVerification verification = userVerificationRepository.findById(verificationId)
                .orElseThrow(() -> new ResourceNotFoundException("VERIFICATION_NOT_FOUND", "Không tìm thấy hồ sơ xác thực"));

        entityManager.refresh(verification, LockModeType.PESSIMISTIC_WRITE);
        if (verification.getStatus() != VerificationStatus.PENDING)
            throw new BadRequestException("KYC_ALREADY_REVIEWED", "Hồ sơ xác thực đã được xử lý");

        verification.setStatus(VerificationStatus.REJECTED);
        verification.setReviewedBy(SecurityUtils.getCurrentUserId());
        verification.setRejectionReason(request.getReason());
        verification.setReviewedAt(Instant.now());
        userVerificationRepository.save(verification);

        recordAuditLog("REJECT_KYC", "USER_VERIFICATION", verificationId, null, "REJECTED: " + request.getReason());
        log.info("Rejected KYC verification {} with reason {}", verificationId, request.getReason());
    }

    public Page<AdminUserResponse> getUsers(UserRole role, UserStatus status, int page, int limit) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, limit), 100));
        Page<User> users;
        if (role != null) {
            users = userRepository.findByRole(role, pageable);
        } else {
            users = userRepository.findAll(pageable);
        }

        return users.map(u -> AdminUserResponse.builder()
                .id(u.getId())
                .email(u.getEmail())
                .phoneNumber(u.getPhoneNumber())
                .fullName(u.getFullName())
                .avatarUrl(u.getAvatarUrl())
                .role(u.getRole())
                .status(u.getStatus())
                .trustScore(u.getTrustScore())
                .strikeCount(u.getStrikeCount() != null ? u.getStrikeCount() : 0)
                .isVerified(u.getIsVerified())
                .createdAt(u.getCreatedAt())
                .build());
    }

    @Transactional
    public void updateUserStatus(UUID userId, UpdateUserStatusRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "Không tìm thấy người dùng"));

        UserStatus prevStatus = user.getStatus();
        int prevScore = user.getTrustScore() != null ? user.getTrustScore() : 50;
        String customReason = (request.getReason() != null && !request.getReason().isBlank())
                ? request.getReason().trim()
                : null;

        if (request.getStatus() == UserStatus.WARNED) {
            applyUserStrike(user, customReason != null ? customReason : "Quản trị viên gửi nhắc nhở tuân thủ quy định cộng đồng", false);
        } else if (request.getStatus() == UserStatus.LOCKED) {
            applyUserStrike(user, customReason != null ? customReason : "Quản trị viên khóa tài khoản do vi phạm quy định", true);
        } else if (request.getStatus() == UserStatus.ACTIVE) {
            user.setStatus(UserStatus.ACTIVE);
            user.setStrikeCount(0);
            userRepository.save(user);

            // Khôi phục hiển thị hồ sơ ghép đôi cho Người thuê
            userProfileRepository.findById(userId).ifPresent(p -> {
                p.setIsPublic(true);
                userProfileRepository.save(p);
            });

            // Khôi phục các phòng còn hạn bị ẩn khi khóa tài khoản Chủ trọ
            if (prevStatus == UserStatus.LOCKED) {
                Instant now = Instant.now();
                List<Room> hiddenRooms = roomRepository.findByLandlordIdOrderByCreatedAtDesc(userId);
                boolean changed = false;
                for (Room room : hiddenRooms) {
                    if (room.getStatus() == RoomStatus.HIDDEN
                            && (room.getExpiresAt() == null || room.getExpiresAt().isAfter(now))) {
                        room.setStatus(RoomStatus.AVAILABLE);
                        changed = true;
                    }
                }
                if (changed) {
                    roomRepository.saveAll(hiddenRooms);
                }
            }

            int restoredScore = userService.recalculateAndSyncTrustScore(userId);
            int delta = restoredScore - prevScore;
            String restoreNote = customReason != null
                    ? "Gỡ cảnh báo vi phạm / Mở khóa tài khoản: " + customReason
                    : "Quản trị viên gỡ cảnh báo vi phạm và khôi phục trạng thái hoạt động bình thường";

            trustScoreLogRepository.save(TrustScoreLog.builder()
                    .userId(userId)
                    .delta(delta)
                    .finalScore(restoredScore)
                    .reason(restoreNote)
                    .build());

            notificationService.create(userId,
                    "Tài khoản đã được gỡ cảnh báo & khôi phục quyền lợi",
                    restoreNote + ". Mức cảnh báo đã đặt lại về 0/3 và điểm uy tín hiện tại của bạn là " + restoredScore + "/100.",
                    NotificationType.SYSTEM,
                    Map.of("strikeCount", 0, "status", "ACTIVE", "trustScore", restoredScore));
        } else {
            user.setStatus(request.getStatus());
            userRepository.save(user);
        }

        recordAuditLog("UPDATE_USER_STATUS", "USER", userId, prevStatus.name(), user.getStatus().name());
        log.info("Updated user {} status to {}", userId, user.getStatus());
    }

    @Transactional
    public void verifyUser(UUID userId, boolean verified) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "Không tìm thấy người dùng"));

        user.setIsVerified(verified);
        userRepository.save(user);
        userService.recalculateAndSyncTrustScore(userId);

        List<Room> landlordRooms = roomRepository.findByLandlordIdOrderByCreatedAtDesc(userId);
        if (!landlordRooms.isEmpty()) {
            landlordRooms.forEach(r -> r.setIsVerified(verified));
            roomRepository.saveAll(landlordRooms);
        }

        recordAuditLog(verified ? "GRANT_USER_VERIFIED" : "REVOKE_USER_VERIFIED", "USER", userId, null,
                verified ? "Cấp tích xanh xác minh thủ công" : "Thu hồi tích xanh xác minh");

        notificationService.create(userId,
                verified ? "Tài khoản của bạn đã được xác minh" : "Trạng thái xác minh đã thay đổi",
                verified ? "Ban quản trị đã xác minh căn cước cho hồ sơ của bạn (+25 điểm uy tín)." : "Ban quản trị đã thu hồi trạng thái xác minh của hồ sơ.",
                NotificationType.SYSTEM,
                Map.of("isVerified", verified));
    }

    @Transactional
    public String adminResetPassword(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "Không tìm thấy người dùng"));

        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new BadRequestException("USER_NO_EMAIL", "Người dùng không có địa chỉ email hợp lệ để nhận mật khẩu");
        }

        String chars = "0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder("Ptx@");
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        String tempPassword = sb.toString();

        user.setPasswordHash(passwordEncoder.encode(tempPassword));
        userRepository.save(user);

        // Gửi email chứa mật khẩu mới an toàn trực tiếp tới hòm thư người dùng (Zero-knowledge for Admin)
        emailNotificationPort.sendAdminResetPasswordEmail(user.getEmail(), tempPassword);

        String maskedEmail = maskEmail(user.getEmail());

        recordAuditLog("ADMIN_RESET_PASSWORD", "USER", userId, null,
                "Quản trị viên đặt lại mật khẩu và gửi trực tiếp qua email tới " + maskedEmail);

        notificationService.create(userId,
                "Mật khẩu tài khoản đã được đặt lại",
                "Mật khẩu tài khoản của bạn đã được đặt lại bởi Ban Quản Trị và gửi an toàn đến email " + maskedEmail + ". Vui lòng kiểm tra hộp thư và đổi mật khẩu mới ngay sau khi đăng nhập.",
                NotificationType.SYSTEM,
                Map.of("action", "RESET_PASSWORD"));

        log.info("[AdminService] Password reset successfully for user {} and emailed directly to {}", userId, maskedEmail);
        return maskedEmail;
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email != null ? email : "";
        int atIndex = email.indexOf('@');
        String name = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (name.length() <= 2) {
            return name.charAt(0) + "***" + domain;
        }
        return name.substring(0, 2) + "***" + domain;
    }

    @Transactional
    public void sendUserNotification(UUID userId, String title, String message) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "Không tìm thấy người dùng"));

        String safeTitle = (title != null && !title.isBlank()) ? title : "Thông báo từ Ban Quản Trị";

        notificationService.create(userId,
                safeTitle,
                message,
                NotificationType.SYSTEM,
                Map.of("fromAdmin", true));

        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            try {
                emailNotificationPort.sendNotificationEmail(user.getEmail(), safeTitle, message);
            } catch (Exception e) {
                log.warn("[AdminService] Could not send notification email to {}: {}", user.getEmail(), e.getMessage());
            }
        }

        recordAuditLog("ADMIN_SEND_NOTIFICATION", "USER", userId, null, "Gửi thông báo: " + message);
    }

    public Page<AdminRoomResponse> getRooms(RoomStatus status, int page, int limit) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, limit), 100));
        Page<Room> rooms;
        if (status != null) {
            rooms = roomRepository.findByStatus(status, pageable);
        } else {
            rooms = roomRepository.findAll(pageable);
        }

        return rooms.map(r -> AdminRoomResponse.builder()
                .id(r.getId())
                .landlordId(r.getLandlordId())
                .title(r.getTitle())
                .description(r.getDescription())
                .roomType(r.getRoomType())
                .price(r.getPrice())
                .depositAmount(r.getDepositAmount())
                .areaSqm(r.getAreaSqm())
                .floorNumber(r.getFloorNumber())
                .maxOccupants(r.getMaxOccupants())
                .addressStreet(r.getAddressStreet())
                .district(r.getDistrict())
                .city(r.getCity())
                .latitude(r.getLatitude())
                .longitude(r.getLongitude())
                .status(r.getStatus())
                .isVerified(r.getIsVerified())
                .isBoosted(r.getIsBoosted())
                .viewCount(r.getViewCount())
                .createdAt(r.getCreatedAt())
                .build());
    }

    @Transactional
    public void verifyRoom(UUID roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("ROOM_NOT_FOUND", "Không tìm thấy phòng"));

        room.setIsVerified(true);
        roomRepository.save(room);
        recordAuditLog("VERIFY_ROOM", "ROOM", roomId, null, "VERIFIED");
        log.info("Admin verified room {}", roomId);
    }

    @Transactional
    public void takeDownRoom(UUID roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("ROOM_NOT_FOUND", "Không tìm thấy phòng"));

        room.setStatus(RoomStatus.HIDDEN);
        roomRepository.save(room);
        recordAuditLog("TAKE_DOWN_ROOM", "ROOM", roomId, null, "HIDDEN");
        log.info("Admin took down room {}", roomId);
    }

    public Page<AdminReviewDisputeResponse> getDisputes(int page, int limit) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, limit), 100));
        Page<ReviewDispute> disputes = reviewDisputeRepository.findByStatus("PENDING_REVIEW", pageable);

        return disputes.map(d -> AdminReviewDisputeResponse.builder()
                .id(d.getId())
                .reviewId(d.getReviewId())
                .appellantId(d.getAppellantId())
                .reason(d.getReason())
                .evidenceImages(d.getEvidenceImages())
                .status(d.getStatus())
                .adminNotes(d.getAdminNotes())
                .resolvedBy(d.getResolvedBy())
                .createdAt(d.getCreatedAt())
                .resolvedAt(d.getResolvedAt())
                .build());
    }

    @Transactional
    public void resolveDispute(UUID disputeId, ResolveDisputeRequest request) {
        ReviewDispute dispute = reviewDisputeRepository.findById(disputeId)
                .orElseGet(() -> reviewDisputeRepository.findByReviewId(disputeId).orElse(null));

        if (dispute == null) {
            throw new ResourceNotFoundException("DISPUTE_NOT_FOUND", "Không tìm thấy khiếu nại");
        }

        entityManager.refresh(dispute, LockModeType.PESSIMISTIC_WRITE);
        if (!"PENDING_REVIEW".equals(dispute.getStatus()))
            throw new BadRequestException("DISPUTE_ALREADY_RESOLVED", "Khiếu nại đã được xử lý");
        if (request.getDecision() != ReviewDisputeStatus.RESOLVED_REMOVED && request.getDecision() != ReviewDisputeStatus.RESOLVED_UPHELD)
            throw new BadRequestException("INVALID_DISPUTE_DECISION", "Quyết định xử lý khiếu nại không hợp lệ");

        Review review = reviewRepository.findById(dispute.getReviewId())
                .orElseThrow(() -> new ResourceNotFoundException("REVIEW_NOT_FOUND", "Không tìm thấy đánh giá"));

        UUID currentAdminId = SecurityUtils.getCurrentUserId();

        if (request.getDecision() == ReviewDisputeStatus.RESOLVED_REMOVED) {
            review.setStatus("REMOVED");
            dispute.setStatus("RESOLVED_UPHELD");
            dispute.setResolvedBy(currentAdminId);
            dispute.setResolvedAt(Instant.now());
            dispute.setAdminNotes(request.getAdminNotes());
            reviewDisputeRepository.save(dispute);

            // Restore TrustScore to reviewee
            if (review.getRating() <= 2) userRepository.findById(review.getRevieweeId()).ifPresent(u -> {
                int restoredScore = Math.min(100, u.getTrustScore() + 10);
                u.setTrustScore(restoredScore);
                userRepository.save(u);

                trustScoreLogRepository.save(TrustScoreLog.builder()
                        .userId(u.getId())
                        .delta(10)
                        .finalScore(restoredScore)
                        .reason("Admin chấp thuận khiếu nại và gỡ bỏ đánh giá ác ý")
                        .build());
            });
        } else {
            review.setStatus("ACTIVE");
            dispute.setStatus("RESOLVED_REJECTED");
            dispute.setResolvedBy(currentAdminId);
            dispute.setResolvedAt(Instant.now());
            dispute.setAdminNotes(request.getAdminNotes());
            reviewDisputeRepository.save(dispute);
        }

        reviewRepository.save(review);
        recordAuditLog("RESOLVE_DISPUTE", "REVIEW_DISPUTE", dispute.getId(), null, request.getDecision().name());
        log.info("Admin resolved dispute {} with decision {}", dispute.getId(), request.getDecision());
    }

    public Page<AdminPaymentTransactionResponse> getTransactions(int page, int limit) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, limit), 100));
        Page<PaymentTransaction> txs = paymentTransactionRepository.findAllByOrderByCreatedAtDesc(pageable);

        return txs.map(t -> AdminPaymentTransactionResponse.builder()
                .id(t.getId())
                .userId(t.getUserId())
                .itemType(t.getItemType())
                .itemName(t.getItemName())
                .amount(t.getAmount())
                .paymentMethod(t.getPaymentMethod())
                .status(t.getStatus())
                .idempotencyKey(t.getIdempotencyKey())
                .gatewayOrderId(t.getGatewayOrderId())
                .qrCodeUrl(t.getQrCodeUrl())
                .qrExpiredAt(t.getQrExpiredAt())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build());
    }

    public Page<AdminReportResponse> getReports(ReportStatus status, ReportSeverity severity, int page, int limit) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, limit), 100));
        Page<Report> reports;
        if (status != null && severity != null) {
            reports = reportRepository.findByStatusAndSeverityOrderByCreatedAtDesc(status, severity, pageable);
        } else if (status != null) {
            reports = reportRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        } else if (severity != null) {
            reports = reportRepository.findBySeverityOrderByCreatedAtDesc(severity, pageable);
        } else {
            reports = reportRepository.findAllByOrderByCreatedAtDesc(pageable);
        }

        return reports.map(this::mapToReportResponse);
    }

    public AdminReportResponse getReportDetail(UUID id) {
        Report r = reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("REPORT_NOT_FOUND", "Không tìm thấy báo cáo vi phạm"));
        return mapToReportResponse(r);
    }

    @Transactional
    public AdminReportResponse handleReportAction(UUID id, AdminReportActionRequest request) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("REPORT_NOT_FOUND", "Không tìm thấy báo cáo vi phạm"));

        UUID currentAdminId = SecurityUtils.getCurrentUserId();
        report.setHandlerId(currentAdminId);

        String action = request.getAction().toLowerCase();
        String note = (request.getNote() != null && !request.getNote().isBlank())
                ? request.getNote().trim()
                : report.getReportType();
        switch (action) {
            case "resolve" -> report.setStatus(ReportStatus.RESOLVED);
            case "dismiss" -> report.setStatus(ReportStatus.DISMISSED);
            case "warn" -> {
                report.setStatus(ReportStatus.RESOLVED);
                resolveReportTargetUser(report).ifPresent(u -> applyUserStrike(u, note, false));
            }
            case "ban" -> {
                report.setStatus(ReportStatus.RESOLVED);
                resolveReportTargetUser(report).ifPresent(u -> applyUserStrike(u, note, true));
            }
            case "remove_content" -> {
                report.setStatus(ReportStatus.RESOLVED);
                if ("ROOM".equalsIgnoreCase(report.getTargetType())) {
                    roomRepository.findById(report.getTargetId()).ifPresent(r -> {
                        r.setStatus(RoomStatus.HIDDEN);
                        r.setIsBoosted(false);
                        r.setBoostExpiresAt(null);
                        roomRepository.save(r);
                        userRepository.findById(r.getLandlordId()).ifPresent(u -> applyUserStrike(u, note, false));
                    });
                } else {
                    resolveReportTargetUser(report).ifPresent(u -> {
                        userProfileRepository.findById(u.getId()).ifPresent(p -> {
                            p.setBio(null);
                            userProfileRepository.save(p);
                        });
                        List<SwapRequest> openSwaps = swapRequestRepository.findByRequesterIdOrderByCreatedAtDesc(u.getId());
                        for (SwapRequest sr : openSwaps) {
                            if (sr.getStatus() == vn.phongtroxanh.backend.modules.swap.domain.SwapStatus.OPEN) {
                                sr.setStatus(vn.phongtroxanh.backend.modules.swap.domain.SwapStatus.CANCELLED);
                            }
                        }
                        if (!openSwaps.isEmpty()) {
                            swapRequestRepository.saveAll(openSwaps);
                        }
                        applyUserStrike(u, note, false);
                    });
                }
            }
            default -> throw new BadRequestException("INVALID_ACTION", "Hành động không hợp lệ: " + action);
        }

        report = reportRepository.save(report);
        recordAuditLog("HANDLE_REPORT", "REPORT", report.getId(), null, action + ": " + request.getNote());
        return mapToReportResponse(report);
    }

    @Transactional
    public AdminReportResponse createReport(CreateReportRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();

        Report report = Report.builder()
                .reporterId(currentUserId)
                .targetType(request.getTargetType().toUpperCase())
                .targetId(request.getTargetId())
                .reportType(request.getReportType())
                .detail(request.getDetail())
                .evidenceImages(request.getEvidenceImages())
                .status(ReportStatus.NEW)
                .severity(request.getSeverity() != null ? request.getSeverity() : ReportSeverity.MEDIUM)
                .build();

        report = reportRepository.save(report);
        log.info("User {} submitted report {} for {}/{}", currentUserId, report.getId(), report.getTargetType(), report.getTargetId());
        return mapToReportResponse(report);
    }

    public Page<AdminAuditLogResponse> getAuditLogs(int page, int limit) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, limit), 100));
        Page<SystemAuditLog> logs = systemAuditLogRepository.findAllByOrderByCreatedAtDesc(pageable);

        return logs.map(l -> AdminAuditLogResponse.builder()
                .id(l.getId())
                .actorId(l.getActorId())
                .action(l.getAction())
                .targetEntity(l.getTargetEntity())
                .targetId(l.getTargetId())
                .ipAddress(l.getIpAddress())
                .userAgent(l.getUserAgent())
                .createdAt(l.getCreatedAt())
                .build());
    }

    private void recordAuditLog(String action, String targetEntity, UUID targetId, String before, String after) {
        try {
            UUID actorId = SecurityUtils.getCurrentUserId();
            systemAuditLogRepository.save(SystemAuditLog.builder()
                    .actorId(actorId)
                    .action(action)
                    .targetEntity(targetEntity)
                    .targetId(targetId)
                    .payloadBefore(before != null ? "\"" + before + "\"" : null)
                    .payloadAfter(after != null ? "\"" + after + "\"" : null)
                    .ipAddress("127.0.0.1")
                    .userAgent("Internal Admin Console")
                    .build());
        } catch (Exception e) {
            log.warn("Failed to record system audit log: {}", e.getMessage());
        }
    }

    private java.util.Optional<User> resolveReportTargetUser(Report r) {
        if ("USER".equalsIgnoreCase(r.getTargetType())) {
            return userRepository.findById(r.getTargetId());
        }
        if ("ROOM".equalsIgnoreCase(r.getTargetType())) {
            return roomRepository.findById(r.getTargetId())
                    .flatMap(room -> userRepository.findById(room.getLandlordId()));
        }
        return java.util.Optional.empty();
    }

    private void applyUserStrike(User user, String reason, boolean forceBan) {
        int currentStrikes = user.getStrikeCount() != null ? user.getStrikeCount() : 0;
        int newStrikes = forceBan ? Math.max(3, currentStrikes + 1) : currentStrikes + 1;
        int oldScore = user.getTrustScore() != null ? user.getTrustScore() : 50;
        String safeReason = (reason != null && !reason.isBlank()) ? reason.trim() : "Vi phạm quy định cộng đồng";

        user.setStrikeCount(newStrikes);
        if (newStrikes >= 3 || forceBan) {
            user.setStatus(UserStatus.LOCKED);
            user.setTrustScore(0);
            userRepository.save(user);

            List<Room> userRooms = roomRepository.findByLandlordIdOrderByCreatedAtDesc(user.getId());
            for (Room room : userRooms) {
                if (room.getStatus() == RoomStatus.AVAILABLE) {
                    room.setStatus(RoomStatus.HIDDEN);
                }
                room.setIsBoosted(false);
                room.setBoostExpiresAt(null);
            }
            if (!userRooms.isEmpty()) {
                roomRepository.saveAll(userRooms);
            }

            // Đối với Người thuê (Tenant): ẩn hồ sơ khỏi danh sách ghép đôi, tắt đẩy hồ sơ 24h và đóng các tin sang nhượng đang mở
            userProfileRepository.findById(user.getId()).ifPresent(p -> {
                p.setIsPublic(false);
                userProfileRepository.save(p);
            });
            userConsumableRepository.findById(user.getId()).ifPresent(c -> {
                c.setProfileBoostExpiresAt(null);
                userConsumableRepository.save(c);
            });
            List<SwapRequest> tenantSwaps = swapRequestRepository.findByRequesterIdOrderByCreatedAtDesc(user.getId());
            for (SwapRequest sr : tenantSwaps) {
                if (sr.getStatus() == vn.phongtroxanh.backend.modules.swap.domain.SwapStatus.OPEN) {
                    sr.setStatus(vn.phongtroxanh.backend.modules.swap.domain.SwapStatus.CANCELLED);
                }
            }
            if (!tenantSwaps.isEmpty()) {
                swapRequestRepository.saveAll(tenantSwaps);
            }

            trustScoreLogRepository.save(TrustScoreLog.builder()
                    .userId(user.getId())
                    .delta(-oldScore)
                    .finalScore(0)
                    .reason("Khóa tài khoản do vi phạm quy định cộng đồng (Mức 3/3): " + safeReason)
                    .build());

            notificationService.create(user.getId(),
                    "Tài khoản đã bị tạm ngừng hoạt động",
                    "Tài khoản của bạn đã bị khóa do vi phạm quy định cộng đồng (mức " + Math.min(3, newStrikes) + "/3): " + safeReason + ". Các tin đăng liên quan đã được ẩn theo điều khoản dịch vụ.",
                    NotificationType.SYSTEM,
                    Map.of("strikeCount", newStrikes, "status", "LOCKED"));
        } else {
            user.setStatus(UserStatus.WARNED);
            userRepository.save(user);

            if (newStrikes == 2) {
                List<Room> userRooms = roomRepository.findByLandlordIdOrderByCreatedAtDesc(user.getId());
                for (Room room : userRooms) {
                    room.setIsBoosted(false);
                    room.setBoostExpiresAt(null);
                }
                if (!userRooms.isEmpty()) {
                    roomRepository.saveAll(userRooms);
                }
                // Đối với Người thuê (Tenant): tạm dừng hiệu ứng Đẩy hồ sơ tìm bạn ưu tiên (24 giờ)
                userConsumableRepository.findById(user.getId()).ifPresent(c -> {
                    c.setProfileBoostExpiresAt(null);
                    userConsumableRepository.save(c);
                });
            }

            int newScore = userService.recalculateAndSyncTrustScore(user.getId());
            int delta = newScore - oldScore;
            int logDelta = delta != 0 ? delta : (newStrikes == 1 ? -15 : -25);

            trustScoreLogRepository.save(TrustScoreLog.builder()
                    .userId(user.getId())
                    .delta(logDelta)
                    .finalScore(newScore)
                    .reason("Nhắc nhở vi phạm quy định lần " + newStrikes + "/3: " + safeReason)
                    .build());

            notificationService.create(user.getId(),
                    "Nhắc nhở tuân thủ quy định cộng đồng (Lần " + newStrikes + "/3)",
                    "Tài khoản của bạn nhận được nhắc nhở lần " + newStrikes + "/3 với lý do: " + safeReason + ". Nếu vi phạm đến lần 3/3, tài khoản sẽ bị khóa và ngừng các gói dịch vụ đang sử dụng.",
                    NotificationType.SYSTEM,
                    Map.of("strikeCount", newStrikes, "status", "WARNED"));
        }
    }

    private AdminReportResponse mapToReportResponse(Report r) {
        int targetStrikes = resolveReportTargetUser(r)
                .map(u -> u.getStrikeCount() != null ? u.getStrikeCount() : 0)
                .orElse(0);
        return AdminReportResponse.builder()
                .id(r.getId())
                .reporterId(r.getReporterId())
                .targetType(r.getTargetType())
                .targetId(r.getTargetId())
                .targetStrikeCount(targetStrikes)
                .reportType(r.getReportType())
                .detail(r.getDetail())
                .evidenceImages(r.getEvidenceImages())
                .status(r.getStatus().name())
                .severity(r.getSeverity().name())
                .handlerId(r.getHandlerId())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
