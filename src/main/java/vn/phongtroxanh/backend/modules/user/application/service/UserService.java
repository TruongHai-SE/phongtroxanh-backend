package vn.phongtroxanh.backend.modules.user.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.phongtroxanh.backend.common.exception.ResourceNotFoundException;
import vn.phongtroxanh.backend.common.security.SecurityUtils;
import vn.phongtroxanh.backend.common.storage.FileStoragePort;
import vn.phongtroxanh.backend.modules.rental.domain.Rental;
import vn.phongtroxanh.backend.modules.rental.domain.RentalStatus;
import vn.phongtroxanh.backend.modules.rental.infrastructure.repository.RentalRepository;
import vn.phongtroxanh.backend.modules.review.domain.Review;
import vn.phongtroxanh.backend.modules.review.infrastructure.repository.ReviewRepository;
import vn.phongtroxanh.backend.modules.user.domain.*;
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.*;
import vn.phongtroxanh.backend.modules.user.presentation.dto.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserConsumableRepository userConsumableRepository;
    private final UserVerificationRepository userVerificationRepository;
    private final TrustScoreLogRepository trustScoreLogRepository;
    private final ReviewRepository reviewRepository;
    private final RentalRepository rentalRepository;
    private final FileStoragePort fileStoragePort;

    public UserProfileResponse getMe() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "Không tìm thấy thông tin người dùng"));

        UserProfile profile = userProfileRepository.findById(currentUserId).orElse(null);
        UserConsumable consumable = userConsumableRepository.findById(currentUserId).orElse(null);

        return mapToProfileResponse(user, profile, consumable);
    }

    @Transactional
    public UserProfileResponse updateMe(UpdateUserRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "Không tìm thấy thông tin người dùng"));

        user.setFullName(request.getFullName());
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        UserProfile profile = userProfileRepository.findById(currentUserId)
                .orElseGet(() -> UserProfile.builder().userId(currentUserId).build());

        if (request.getBirthDate() != null) profile.setBirthDate(request.getBirthDate());
        if (request.getGender() != null) profile.setGender(request.getGender());
        if (request.getSchoolOrCompany() != null) profile.setSchoolOrCompany(request.getSchoolOrCompany());
        if (request.getBio() != null) profile.setBio(request.getBio());

        userProfileRepository.save(profile);
        UserConsumable consumable = userConsumableRepository.findById(currentUserId).orElse(null);

        return mapToProfileResponse(user, profile, consumable);
    }

    @Transactional
    public String uploadAvatar(MultipartFile file) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "Không tìm thấy thông tin người dùng"));

        String avatarUrl = fileStoragePort.uploadFile(file, "avatars");
        user.setAvatarUrl(avatarUrl);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        return avatarUrl;
    }

    public MatchingProfileResponse getMatchingProfile() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        UserProfile profile = userProfileRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("PROFILE_NOT_FOUND", "Chưa có hồ sơ tìm bạn ở ghép"));

        return MatchingProfileResponse.builder()
                .userId(currentUserId)
                .budgetMin(profile.getBudgetMin())
                .budgetMax(profile.getBudgetMax())
                .preferredDistricts(profile.getPreferredDistricts())
                .preferredGender(profile.getPreferredGender())
                .preferredRoomType(profile.getPreferredRoomType())
                .earlySleeper(profile.getEarlySleeper())
                .isNeat(profile.getIsNeat())
                .allowGuests(profile.getAllowGuests())
                .nonSmoking(profile.getNonSmoking())
                .noiseTolerance(profile.getNoiseTolerance())
                .proximitySchool(profile.getProximitySchool())
                .proximityWork(profile.getProximityWork())
                .proximityMarket(profile.getProximityMarket())
                .proximityBus(profile.getProximityBus())
                .interests(profile.getInterests())
                .build();
    }

    @Transactional
    public MatchingProfileResponse updateMatchingProfile(MatchingProfileRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        UserProfile profile = userProfileRepository.findById(currentUserId)
                .orElseGet(() -> UserProfile.builder().userId(currentUserId).build());

        if (request.getBudgetMin() != null) profile.setBudgetMin(request.getBudgetMin());
        if (request.getBudgetMax() != null) profile.setBudgetMax(request.getBudgetMax());
        if (request.getPreferredDistricts() != null) profile.setPreferredDistricts(request.getPreferredDistricts());
        if (request.getPreferredGender() != null) profile.setPreferredGender(request.getPreferredGender());
        if (request.getPreferredRoomType() != null) profile.setPreferredRoomType(request.getPreferredRoomType());

        if (request.getEarlySleeper() != null) profile.setEarlySleeper(request.getEarlySleeper());
        if (request.getIsNeat() != null) profile.setIsNeat(request.getIsNeat());
        if (request.getAllowGuests() != null) profile.setAllowGuests(request.getAllowGuests());
        if (request.getNonSmoking() != null) profile.setNonSmoking(request.getNonSmoking());
        if (request.getNoiseTolerance() != null) profile.setNoiseTolerance(request.getNoiseTolerance());

        if (request.getProximitySchool() != null) profile.setProximitySchool(request.getProximitySchool());
        if (request.getProximityWork() != null) profile.setProximityWork(request.getProximityWork());
        if (request.getProximityMarket() != null) profile.setProximityMarket(request.getProximityMarket());
        if (request.getProximityBus() != null) profile.setProximityBus(request.getProximityBus());

        if (request.getInterests() != null) profile.setInterests(request.getInterests());

        userProfileRepository.save(profile);

        return MatchingProfileResponse.builder()
                .userId(currentUserId)
                .budgetMin(profile.getBudgetMin())
                .budgetMax(profile.getBudgetMax())
                .preferredDistricts(profile.getPreferredDistricts())
                .preferredGender(profile.getPreferredGender())
                .preferredRoomType(profile.getPreferredRoomType())
                .earlySleeper(profile.getEarlySleeper())
                .isNeat(profile.getIsNeat())
                .allowGuests(profile.getAllowGuests())
                .nonSmoking(profile.getNonSmoking())
                .noiseTolerance(profile.getNoiseTolerance())
                .proximitySchool(profile.getProximitySchool())
                .proximityWork(profile.getProximityWork())
                .proximityMarket(profile.getProximityMarket())
                .proximityBus(profile.getProximityBus())
                .interests(profile.getInterests())
                .build();
    }

    public TrustScoreResponse getTrustScore() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "Không tìm thấy người dùng"));

        List<TrustScoreLog> logs = trustScoreLogRepository.findTop20ByUserIdOrderByCreatedAtDesc(currentUserId);
        List<TrustScoreResponse.TrustScoreLogItem> history = logs.stream()
                .map(l -> TrustScoreResponse.TrustScoreLogItem.builder()
                        .id(l.getId())
                        .delta(l.getDelta())
                        .finalScore(l.getFinalScore())
                        .reason(l.getReason())
                        .createdAt(l.getCreatedAt())
                        .build())
                .toList();

        List<String> badges = new ArrayList<>();
        if (Boolean.TRUE.equals(user.getIsVerified())) {
            badges.add("ĐÃ_XÁC_MINH_CCCD");
        }
        if (user.getTrustScore() >= 80) {
            badges.add("NGƯỜI_DÙNG_UY_TÍN_CAO");
        }

        // 1. Điểm xác minh CCCD/KYC: Tối đa 30 điểm
        int kycScore = Boolean.TRUE.equals(user.getIsVerified()) ? 30 : 0;

        // 2. Điểm đánh giá Review: Truy vấn thực tế từ reviewRepository (Tối đa 30 điểm)
        List<Review> reviews = reviewRepository.findByRevieweeIdOrderByCreatedAtDesc(currentUserId);
        int reviewScore = 0;
        if (!reviews.isEmpty()) {
            double avgRating = reviews.stream().mapToInt(Review::getRating).average().orElse(0.0);
            reviewScore = (int) Math.round((avgRating / 5.0) * 30);
        }

        // 3. Điểm lịch sử thuê phòng: Truy vấn thực tế hợp đồng đã CHECKED_IN hoặc TERMINATED (Tối đa 20 điểm)
        List<Rental> tenantRentals = rentalRepository.findByTenantIdOrderByCreatedAtDesc(currentUserId);
        List<Rental> landlordRentals = rentalRepository.findByLandlordIdOrderByCreatedAtDesc(currentUserId);
        long validRentalsCount = tenantRentals.stream().filter(r -> r.getStatus() == RentalStatus.CHECKED_IN || r.getStatus() == RentalStatus.TERMINATED).count()
                + landlordRentals.stream().filter(r -> r.getStatus() == RentalStatus.CHECKED_IN || r.getStatus() == RentalStatus.TERMINATED).count();
        int rentalDurationScore = (int) Math.min(20, validRentalsCount * 10);

        // 4. Điểm hoàn thiện hồ sơ & tần suất hoạt động (Tối đa 20 điểm)
        UserProfile profile = userProfileRepository.findById(currentUserId).orElse(null);
        int responseRateScore = 0;
        if (profile != null) {
            if (profile.getSchoolOrCompany() != null && !profile.getSchoolOrCompany().isBlank()) responseRateScore += 5;
            if (profile.getBio() != null && !profile.getBio().isBlank()) responseRateScore += 5;
            if (profile.getBudgetMin() != null && profile.getBudgetMax() != null) responseRateScore += 5;
        }
        if (user.getLastActiveAt() != null) {
            responseRateScore += 5;
        }
        responseRateScore = Math.min(20, responseRateScore);

        return TrustScoreResponse.builder()
                .currentScore(user.getTrustScore())
                .isVerified(user.getIsVerified())
                .badges(badges)
                .kycScore(kycScore)
                .reviewScore(reviewScore)
                .rentalDurationScore(rentalDurationScore)
                .responseRateScore(responseRateScore)
                .history(history)
                .build();
    }

    @Transactional
    public KycStatusResponse submitKyc(KycSubmitRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();

        UserVerification verification = UserVerification.builder()
                .userId(currentUserId)
                .idCardNumber(request.getIdCardNumber()) // encrypted via AttributeConverter
                .idCardFrontUrl(request.getIdCardFrontUrl())
                .idCardBackUrl(request.getIdCardBackUrl())
                .status(VerificationStatus.PENDING)
                .build();

        verification = userVerificationRepository.save(verification);

        return KycStatusResponse.builder()
                .verificationId(verification.getId())
                .status(verification.getStatus())
                .createdAt(verification.getCreatedAt())
                .build();
    }

    public KycStatusResponse getKycStatus() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        return userVerificationRepository.findTopByUserIdOrderByCreatedAtDesc(currentUserId)
                .map(v -> KycStatusResponse.builder()
                        .verificationId(v.getId())
                        .status(v.getStatus())
                        .rejectionReason(v.getRejectionReason())
                        .createdAt(v.getCreatedAt())
                        .reviewedAt(v.getReviewedAt())
                        .build())
                .orElseThrow(() -> new ResourceNotFoundException("KYC_NOT_FOUND", "Người dùng chưa gửi yêu cầu xác thực CCCD"));
    }

    public PublicUserProfileResponse getPublicProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "Không tìm thấy người dùng"));

        UserProfile profile = userProfileRepository.findById(userId).orElse(null);

        PublicUserProfileResponse.PublicUserProfileResponseBuilder builder = PublicUserProfileResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .isVerified(user.getIsVerified())
                .trustScore(user.getTrustScore());

        if (profile != null) {
            if (Boolean.TRUE.equals(profile.getShowSchool())) {
                builder.schoolOrCompany(profile.getSchoolOrCompany());
            }
            if (Boolean.FALSE.equals(profile.getHideActiveStatus())) {
                builder.lastActiveAt(user.getLastActiveAt());
            }

            builder.gender(profile.getGender())
                    .bio(profile.getBio())
                    .budgetMin(profile.getBudgetMin())
                    .budgetMax(profile.getBudgetMax())
                    .preferredDistricts(profile.getPreferredDistricts())
                    .preferredRoomType(profile.getPreferredRoomType())
                    .interests(profile.getInterests())
                    .earlySleeper(profile.getEarlySleeper())
                    .isNeat(profile.getIsNeat())
                    .allowGuests(profile.getAllowGuests())
                    .nonSmoking(profile.getNonSmoking())
                    .noiseTolerance(profile.getNoiseTolerance());
        }

        return builder.build();
    }

    public UserSettingsResponse getSettings() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        UserProfile profile = userProfileRepository.findById(currentUserId).orElse(null);
        if (profile == null) {
            return UserSettingsResponse.builder()
                    .isPublic(true)
                    .showSchool(true)
                    .hideActiveStatus(false)
                    .build();
        }
        return UserSettingsResponse.builder()
                .isPublic(profile.getIsPublic())
                .showSchool(profile.getShowSchool())
                .hideActiveStatus(profile.getHideActiveStatus())
                .build();
    }

    @Transactional
    public UserSettingsResponse updateSettings(UserSettingsRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        UserProfile profile = userProfileRepository.findById(currentUserId)
                .orElseGet(() -> UserProfile.builder().userId(currentUserId).build());

        if (request.getIsPublic() != null) profile.setIsPublic(request.getIsPublic());
        if (request.getShowSchool() != null) profile.setShowSchool(request.getShowSchool());
        if (request.getHideActiveStatus() != null) profile.setHideActiveStatus(request.getHideActiveStatus());

        userProfileRepository.save(profile);

        return UserSettingsResponse.builder()
                .isPublic(profile.getIsPublic())
                .showSchool(profile.getShowSchool())
                .hideActiveStatus(profile.getHideActiveStatus())
                .build();
    }

    @Transactional
    public void deleteAccount() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "Không tìm thấy người dùng"));

        user.setStatus(UserStatus.DELETED);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        log.info("Soft deleted account for user {}", currentUserId);
    }

    private UserProfileResponse mapToProfileResponse(User user, UserProfile profile, UserConsumable consumable) {
        UserProfileResponse.UserProfileResponseBuilder builder = UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .fullName(user.getFullName())
                .role(user.getRole())
                .status(user.getStatus())
                .avatarUrl(user.getAvatarUrl())
                .isVerified(user.getIsVerified())
                .trustScore(user.getTrustScore())
                .createdAt(user.getCreatedAt())
                .lastActiveAt(user.getLastActiveAt());

        if (consumable != null) {
            builder.swipesLeft(consumable.getSwipesLeft())
                    .boostsLeft(consumable.getBoostsLeft())
                    .superMatchesLeft(consumable.getSuperMatchesLeft());
        }

        if (profile != null) {
            builder.schoolOrCompany(profile.getSchoolOrCompany())
                    .birthDate(profile.getBirthDate())
                    .gender(profile.getGender())
                    .preferredGender(profile.getPreferredGender())
                    .bio(profile.getBio())
                    .budgetMin(profile.getBudgetMin())
                    .budgetMax(profile.getBudgetMax())
                    .preferredDistricts(profile.getPreferredDistricts())
                    .preferredRoomType(profile.getPreferredRoomType())
                    .interests(profile.getInterests())
                    .earlySleeper(profile.getEarlySleeper())
                    .isNeat(profile.getIsNeat())
                    .allowGuests(profile.getAllowGuests())
                    .nonSmoking(profile.getNonSmoking())
                    .noiseTolerance(profile.getNoiseTolerance())
                    .proximitySchool(profile.getProximitySchool())
                    .proximityWork(profile.getProximityWork())
                    .proximityMarket(profile.getProximityMarket())
                    .proximityBus(profile.getProximityBus())
                    .isPublic(profile.getIsPublic())
                    .showSchool(profile.getShowSchool())
                    .hideActiveStatus(profile.getHideActiveStatus());
        }

        return builder.build();
    }
}
