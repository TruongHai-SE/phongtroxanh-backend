package vn.phongtroxanh.backend.modules.user.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.phongtroxanh.backend.common.exception.BadRequestException;
import vn.phongtroxanh.backend.common.exception.ResourceNotFoundException;
import vn.phongtroxanh.backend.common.exception.ConflictException;
import vn.phongtroxanh.backend.common.exception.UnauthorizedException;
import vn.phongtroxanh.backend.common.security.SecurityUtils;
import vn.phongtroxanh.backend.common.storage.FileStoragePort;
import vn.phongtroxanh.backend.modules.rental.domain.Rental;
import vn.phongtroxanh.backend.modules.matching.application.service.MatchingService;
import vn.phongtroxanh.backend.modules.rental.domain.RentalStatus;
import vn.phongtroxanh.backend.modules.rental.infrastructure.repository.RentalRepository;
import vn.phongtroxanh.backend.modules.review.domain.Review;
import vn.phongtroxanh.backend.modules.review.infrastructure.repository.ReviewRepository;
import vn.phongtroxanh.backend.modules.user.domain.*;
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.*;
import vn.phongtroxanh.backend.modules.user.presentation.dto.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final int MAX_GALLERY_IMAGES = 6;
    private static final String VOWELS_OR_KNOWN_ACRONYMS_REGEX =
            "(?iu).*(?:[aeiouyăâêôơưáàảãạắằẳẵặấầẩẫậéèẻẽẹếềểễệíìỉĩịóòỏõọốồổỗộớờởỡợúùủũụứừửữựýỳỷỹỵ]|\\b(?:đh|cđ|thpt|cty|fpt|vng|cmc|vnpt|kms|rmit|kpmg|pwc|dhl|cgv|bhd|acb|shb|tcb|mbb|vpb|bidv)\\b).*";

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserConsumableRepository userConsumableRepository;
    private final UserVerificationRepository userVerificationRepository;
    private final TrustScoreLogRepository trustScoreLogRepository;
    private final ReviewRepository reviewRepository;
    private final RentalRepository rentalRepository;
    private final FileStoragePort fileStoragePort;
    private final MatchingService matchingService;
    private final EntityManager entityManager;

    public static String validateAndCleanSchoolOrCompany(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("INVALID_SCHOOL_OR_COMPANY", "Vui lòng nhập tên trường học hoặc nơi làm việc");
        }
        String cleaned = raw.trim().replaceAll("\\s+", " ");
        if (cleaned.length() < 2 || cleaned.length() > 100) {
            throw new BadRequestException("INVALID_SCHOOL_OR_COMPANY", "Tên trường học hoặc nơi làm việc cần từ 2 đến 100 ký tự");
        }
        if (!cleaned.matches("^[\\p{L}\\d\\s.,\\-&()/+'']+$") || cleaned.replaceAll("[^\\p{L}]", "").length() < 2) {
            throw new BadRequestException("INVALID_SCHOOL_OR_COMPANY", "Tên trường học hoặc nơi làm việc chỉ gồm chữ cái, số và dấu câu cơ bản");
        }
        if (cleaned.matches("(?iu).*(.)\\1{3,}.*") || cleaned.matches("(?iu)^(.{2,3})\\1+$")
                || cleaned.matches("(?iu).*(asdf|qwer|zxcv|hjkl|bnm).*")
                || !cleaned.matches(VOWELS_OR_KNOWN_ACRONYMS_REGEX)) {
            throw new BadRequestException("INVALID_SCHOOL_OR_COMPANY", "Vui lòng nhập tên trường học hoặc nơi làm việc thực tế (ví dụ: ĐH FPT, Công ty VNG)");
        }
        return cleaned;
    }

    public static int computeProfileBaseTrustScore(User user, UserProfile profile) {
        if (user == null) return 50;
        int strikes = user.getStrikeCount() != null ? user.getStrikeCount() : 0;
        if (user.getStatus() == UserStatus.LOCKED || strikes >= 3) return 0;

        int kycScore = 0;
        boolean hasContact = (user.getEmail() != null && !user.getEmail().isBlank())
                || (user.getPhoneNumber() != null && !user.getPhoneNumber().isBlank());
        if (hasContact) kycScore += 10;
        if (Boolean.TRUE.equals(user.getIsVerified())) kycScore += 25;

        int profileScore = 0;
        boolean hasPhoto = (user.getAvatarUrl() != null && !user.getAvatarUrl().isBlank())
                || (profile != null && profile.getGalleryImages() != null && !profile.getGalleryImages().isEmpty());
        if (hasPhoto) profileScore += 5;
        if (profile != null) {
            if (profile.getBio() != null && !profile.getBio().isBlank()) profileScore += 10;
            if (user.getRole() == UserRole.LANDLORD) {
                if (profile.getAddress() != null && !profile.getAddress().isBlank()) profileScore += 10;
                if (profile.getPreferredDistricts() != null && !profile.getPreferredDistricts().isEmpty()) profileScore += 10;
            } else {
                if (profile.getSchoolOrCompany() != null && !profile.getSchoolOrCompany().isBlank()) profileScore += 10;
                if (profile.getBudgetMin() != null && profile.getBudgetMax() != null
                        && profile.getPreferredDistricts() != null && !profile.getPreferredDistricts().isEmpty()) {
                    profileScore += 10;
                }
            }
        }
        int baseCommunity = (strikes == 0 && user.getStatus() == UserStatus.ACTIVE) ? 5 : 0;
        int baseActivity = user.getLastActiveAt() != null ? 5 : 0;
        int penalty = strikes == 1 ? 15 : strikes == 2 ? 40 : 0;
        return Math.max(0, Math.min(100, kycScore + Math.min(35, profileScore) + baseCommunity + baseActivity - penalty));
    }

    @Transactional
    public UserProfileResponse getMe() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        userConsumableRepository.resetDailySwipesAtomic(currentUserId, LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")));
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "Không tìm thấy thông tin người dùng"));

        UserProfile profile = userProfileRepository.findById(currentUserId).orElse(null);
        UserConsumable consumable = userConsumableRepository.findById(currentUserId).orElse(null);
        recalculateAndSyncTrustScore(user, profile);

        return mapToProfileResponse(user, profile, consumable);
    }

    @Transactional
    public UserProfileResponse updateMe(UpdateUserRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "Không tìm thấy thông tin người dùng"));
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        userConsumableRepository.resetDailySwipesAtomic(currentUserId, LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")));

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            String cleanName = request.getFullName().trim();
            if (cleanName.matches("^\\d+$")) {
                throw new BadRequestException("INVALID_FULL_NAME", "Họ và tên không thể chỉ toàn chữ số");
            }
            user.setFullName(cleanName);
        }
        user.setUpdatedAt(Instant.now());

        UserProfile profile = userProfileRepository.findById(currentUserId)
                .orElseGet(() -> UserProfile.builder().userId(currentUserId).build());

        if (request.getBirthDate() != null) profile.setBirthDate(request.getBirthDate());
        if (request.getGender() != null) profile.setGender(request.getGender());
        if (request.getSchoolOrCompany() != null) {
            if (request.getSchoolOrCompany().isBlank()) {
                profile.setSchoolOrCompany(null);
            } else {
                profile.setSchoolOrCompany(validateAndCleanSchoolOrCompany(request.getSchoolOrCompany()));
            }
        }
        if (request.getBio() != null) profile.setBio(request.getBio().trim());
        if (request.getAddress() != null) profile.setAddress(request.getAddress().trim());
        if (request.getPreferredDistricts() != null) profile.setPreferredDistricts(request.getPreferredDistricts());

        userProfileRepository.save(profile);
        user.setTrustScore(computeTrustScoreBreakdown(user, profile).getCurrentScore());
        userRepository.save(user);

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
        UserProfile profile = userProfileRepository.findById(currentUserId).orElse(null);
        user.setTrustScore(computeTrustScoreBreakdown(user, profile).getCurrentScore());
        userRepository.save(user);

        return avatarUrl;
    }

    @Transactional
    public List<String> uploadGalleryImage(MultipartFile file) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "Không tìm thấy thông tin người dùng"));

        UserProfile profile = userProfileRepository.findById(currentUserId)
                .orElseGet(() -> UserProfile.builder().userId(currentUserId).build());

        List<String> gallery = profile.getGalleryImages() != null
                ? new ArrayList<>(profile.getGalleryImages())
                : new ArrayList<>();

        if (gallery.size() >= MAX_GALLERY_IMAGES) {
            throw new BadRequestException("GALLERY_LIMIT_REACHED", "Bạn chỉ có thể tải lên tối đa 6 ảnh giới thiệu cá nhân");
        }

        String uploadedUrl = fileStoragePort.uploadFile(file, "profiles");
        gallery.add(uploadedUrl);
        profile.setGalleryImages(gallery);
        userProfileRepository.save(profile);

        recalculateAndSyncTrustScore(user, profile);
        return gallery;
    }

    @Transactional
    public List<String> deleteGalleryImage(String imageUrl) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "Không tìm thấy thông tin người dùng"));

        UserProfile profile = userProfileRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("PROFILE_NOT_FOUND", "Không tìm thấy hồ sơ"));

        List<String> gallery = profile.getGalleryImages() != null
                ? new ArrayList<>(profile.getGalleryImages())
                : new ArrayList<>();

        if (imageUrl != null && gallery.remove(imageUrl)) {
            profile.setGalleryImages(gallery);
            userProfileRepository.save(profile);
            try {
                fileStoragePort.deleteFile(imageUrl);
            } catch (Exception e) {
                log.warn("Could not delete gallery image from storage: {}", e.getMessage());
            }
            recalculateAndSyncTrustScore(user, profile);
        }
        return gallery;
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
        MatchingProfileResponse response = matchingService.updatePreferences(request);
        userRepository.findById(currentUserId).ifPresent(u -> {
            UserProfile p = userProfileRepository.findById(currentUserId).orElse(null);
            recalculateAndSyncTrustScore(u, p);
        });
        return response;
    }

    @Transactional
    public int recalculateAndSyncTrustScore(UUID userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return 0;
        UserProfile profile = userProfileRepository.findById(userId).orElse(null);
        return recalculateAndSyncTrustScore(user, profile);
    }

    @Transactional
    public int recalculateAndSyncTrustScore(User user, UserProfile profile) {
        TrustScoreResponse breakdown = computeTrustScoreBreakdown(user, profile);
        int calculated = breakdown.getCurrentScore();
        if (user.getTrustScore() == null || !user.getTrustScore().equals(calculated)) {
            user.setTrustScore(calculated);
            userRepository.save(user);
        }
        return calculated;
    }

    @Transactional
    public TrustScoreResponse getTrustScore() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "Không tìm thấy người dùng"));
        UserProfile profile = userProfileRepository.findById(currentUserId).orElse(null);

        TrustScoreResponse response = computeTrustScoreBreakdown(user, profile);
        if (user.getTrustScore() == null || !user.getTrustScore().equals(response.getCurrentScore())) {
            user.setTrustScore(response.getCurrentScore());
            userRepository.save(user);
        }
        return response;
    }

    private TrustScoreResponse computeTrustScoreBreakdown(User user, UserProfile profile) {
        UUID userId = user.getId() != null ? user.getId() : SecurityUtils.getCurrentUserId();
        List<TrustScoreLog> logs = userId != null
                ? trustScoreLogRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId)
                : List.of();
        List<TrustScoreResponse.TrustScoreLogItem> history = logs.stream()
                .map(l -> TrustScoreResponse.TrustScoreLogItem.builder()
                        .id(l.getId())
                        .delta(l.getDelta())
                        .finalScore(l.getFinalScore())
                        .reason(l.getReason())
                        .createdAt(l.getCreatedAt())
                        .build())
                .toList();

        // 1. Nhóm Xác thực tài khoản (Tối đa 35 điểm): SĐT/Email (+10đ) + Căn cước (+25đ)
        int kycScore = 0;
        boolean hasContact = (user.getEmail() != null && !user.getEmail().isBlank())
                || (user.getPhoneNumber() != null && !user.getPhoneNumber().isBlank());
        if (hasContact) {
            kycScore += 10;
        }
        if (Boolean.TRUE.equals(user.getIsVerified())) {
            kycScore += 25;
        }

        // 2. Nhóm Thông tin hồ sơ (Tối đa 35 điểm)
        int responseRateScore = 0;
        boolean hasPhoto = (user.getAvatarUrl() != null && !user.getAvatarUrl().isBlank())
                || (profile != null && profile.getGalleryImages() != null && !profile.getGalleryImages().isEmpty());
        if (hasPhoto) {
            responseRateScore += 5;
        }
        if (profile != null) {
            if (profile.getBio() != null && !profile.getBio().isBlank()) {
                responseRateScore += 10;
            }
            if (user.getRole() == UserRole.LANDLORD) {
                if (profile.getAddress() != null && !profile.getAddress().isBlank()) {
                    responseRateScore += 10;
                }
                if (profile.getPreferredDistricts() != null && !profile.getPreferredDistricts().isEmpty()) {
                    responseRateScore += 10;
                }
            } else {
                if (profile.getSchoolOrCompany() != null && !profile.getSchoolOrCompany().isBlank()) {
                    responseRateScore += 10;
                }
                boolean hasRentalPrefs = profile.getBudgetMin() != null
                        && profile.getBudgetMax() != null
                        && profile.getPreferredDistricts() != null
                        && !profile.getPreferredDistricts().isEmpty();
                if (hasRentalPrefs) {
                    responseRateScore += 10;
                }
            }
        }
        responseRateScore = Math.min(35, responseRateScore);

        // 3. Nhóm Đánh giá & Tuân thủ
        int strikes = user.getStrikeCount() != null ? user.getStrikeCount() : 0;
        List<Review> reviews = userId != null
                ? reviewRepository.findByRevieweeIdOrderByCreatedAtDesc(userId).stream()
                        .filter(r -> "ACTIVE".equals(r.getStatus())).toList()
                : List.of();
        int reviewScore;
        if (!reviews.isEmpty()) {
            double avgRating = reviews.stream().mapToInt(Review::getRating).average().orElse(0.0);
            reviewScore = (int) Math.round((avgRating / 5.0) * 30);
        } else {
            reviewScore = (strikes == 0 && hasContact && user.getStatus() == UserStatus.ACTIVE) ? 5 : 0;
        }

        // 4. Nhóm Hoạt động & Giao dịch phòng (Tối đa 15 điểm)
        List<Rental> tenantRentals = userId != null
                ? rentalRepository.findByTenantIdOrderByCreatedAtDesc(userId)
                : List.of();
        List<Rental> landlordRentals = userId != null
                ? rentalRepository.findByLandlordIdOrderByCreatedAtDesc(userId)
                : List.of();
        long validRentalsCount = Stream.concat(tenantRentals.stream(), landlordRentals.stream())
                .filter(r -> r.getStatus() == RentalStatus.CHECKED_IN
                        || (r.getStatus() == RentalStatus.TERMINATED && r.getCheckedInAt() != null)).count();
        int rentalDurationScore = validRentalsCount > 0
                ? (int) Math.min(15, validRentalsCount * 10 + (user.getLastActiveAt() != null ? 5 : 0))
                : 0;
        int activityBonus = (hasContact && user.getLastActiveAt() != null && validRentalsCount == 0) ? 5 : 0;

        // Điểm trừ vi phạm (nếu có)
        int penaltyScore = 0;
        if (user.getStatus() == UserStatus.LOCKED || strikes >= 3) {
            penaltyScore = 100;
        } else if (strikes == 1) {
            penaltyScore = 15;
        } else if (strikes == 2) {
            penaltyScore = 40;
        }

        int effectiveReviewPoints = Math.min(15, reviewScore);
        int currentScore = (user.getStatus() == UserStatus.LOCKED || strikes >= 3)
                ? 0
                : Math.max(0, Math.min(100, kycScore + responseRateScore + effectiveReviewPoints + rentalDurationScore + activityBonus - penaltyScore));

        List<String> badges = new ArrayList<>();
        if (Boolean.TRUE.equals(user.getIsVerified())) {
            badges.add("ĐÃ_XÁC_MINH_CCCD");
        }
        if (currentScore >= 80) {
            badges.add("NGƯỜI_DÙNG_UY_TÍN_CAO");
        }

        return TrustScoreResponse.builder()
                .currentScore(currentScore)
                .isVerified(user.getIsVerified())
                .badges(badges)
                .kycScore(kycScore)
                .reviewScore(reviewScore)
                .rentalDurationScore(rentalDurationScore)
                .responseRateScore(responseRateScore)
                .strikeCount(strikes)
                .penaltyScore(penaltyScore)
                .history(history)
                .build();
    }

    @Transactional
    public KycStatusResponse submitKyc(KycSubmitRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        User user = entityManager.find(User.class, currentUserId, LockModeType.PESSIMISTIC_WRITE);
        if (user == null) {
            throw new ResourceNotFoundException("USER_NOT_FOUND", "Không tìm thấy người dùng");
        }
        if (Boolean.TRUE.equals(user.getIsVerified()) || userVerificationRepository.existsByUserIdAndStatusIn(
                currentUserId, List.of(VerificationStatus.PENDING, VerificationStatus.APPROVED))) {
            throw new ConflictException("KYC_ALREADY_SUBMITTED", "Bạn đã có hồ sơ đang chờ duyệt hoặc đã được xác minh");
        }

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
        if ((user.getStatus() == UserStatus.LOCKED || user.getStatus() == UserStatus.DELETED
                || profile == null || !Boolean.TRUE.equals(profile.getIsPublic())) && !canInspectProfile(userId)) {
            throw new ResourceNotFoundException("USER_NOT_FOUND", "Không tìm thấy hồ sơ công khai");
        }

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

            builder.birthDate(profile.getBirthDate())
                    .gender(profile.getGender())
                    .bio(profile.getBio())
                    .budgetMin(profile.getBudgetMin())
                    .budgetMax(profile.getBudgetMax())
                    .preferredDistricts(profile.getPreferredDistricts())
                    .preferredRoomType(profile.getPreferredRoomType())
                    .interests(profile.getInterests())
                    .galleryImages(profile.getGalleryImages())
                    .earlySleeper(profile.getEarlySleeper())
                    .isNeat(profile.getIsNeat())
                    .allowGuests(profile.getAllowGuests())
                    .nonSmoking(profile.getNonSmoking())
                    .noiseTolerance(profile.getNoiseTolerance())
                    .proximitySchool(profile.getProximitySchool())
                    .proximityWork(profile.getProximityWork())
                    .proximityMarket(profile.getProximityMarket())
                    .proximityBus(profile.getProximityBus());
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

        if (Stream.concat(rentalRepository.findByTenantIdOrderByCreatedAtDesc(currentUserId).stream(),
                rentalRepository.findByLandlordIdOrderByCreatedAtDesc(currentUserId).stream())
                .anyMatch(r -> r.getStatus() == RentalStatus.PENDING_CHECKIN || r.getStatus() == RentalStatus.CHECKED_IN)) {
            throw new ConflictException("ACTIVE_RENTAL_EXISTS", "Vui lòng kết thúc hoặc hủy hợp đồng đang còn hiệu lực trước khi xóa tài khoản");
        }

        user.setStatus(UserStatus.DELETED);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        log.info("Soft deleted account for user {}", currentUserId);
    }

    private boolean canInspectProfile(UUID ownerId) {
        if (SecurityUtils.hasRole("ADMIN")) return true;
        try {
            return SecurityUtils.getCurrentUserId().equals(ownerId);
        } catch (UnauthorizedException ignored) {
            return false;
        }
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
                .strikeCount(user.getStrikeCount() != null ? user.getStrikeCount() : 0)
                .createdAt(user.getCreatedAt())
                .lastActiveAt(user.getLastActiveAt());

        if (consumable != null) {
            builder.swipesLeft(consumable.getSwipesLeft())
                    .boostsLeft(consumable.getBoostsLeft());
        }

        if (profile != null) {
            builder.address(profile.getAddress())
                    .schoolOrCompany(profile.getSchoolOrCompany())
                    .birthDate(profile.getBirthDate())
                    .gender(profile.getGender())
                    .preferredGender(profile.getPreferredGender())
                    .bio(profile.getBio())
                    .budgetMin(profile.getBudgetMin())
                    .budgetMax(profile.getBudgetMax())
                    .preferredDistricts(profile.getPreferredDistricts())
                    .preferredRoomType(profile.getPreferredRoomType())
                    .interests(profile.getInterests())
                    .galleryImages(profile.getGalleryImages())
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
