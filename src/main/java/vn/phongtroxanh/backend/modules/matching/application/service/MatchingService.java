package vn.phongtroxanh.backend.modules.matching.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phongtroxanh.backend.common.exception.BadRequestException;
import vn.phongtroxanh.backend.common.exception.ConflictException;
import vn.phongtroxanh.backend.common.exception.ForbiddenException;
import vn.phongtroxanh.backend.common.exception.ResourceNotFoundException;
import vn.phongtroxanh.backend.common.security.SecurityUtils;
import vn.phongtroxanh.backend.modules.chat.domain.Conversation;
import vn.phongtroxanh.backend.modules.chat.domain.ConversationType;
import vn.phongtroxanh.backend.modules.chat.infrastructure.repository.ConversationRepository;
import vn.phongtroxanh.backend.modules.matching.domain.*;
import vn.phongtroxanh.backend.modules.matching.infrastructure.repository.MatchRepository;
import vn.phongtroxanh.backend.modules.matching.infrastructure.repository.SwipeRepository;
import vn.phongtroxanh.backend.modules.matching.presentation.dto.*;
import vn.phongtroxanh.backend.modules.user.domain.*;
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.*;
import vn.phongtroxanh.backend.modules.user.presentation.dto.MatchingProfileRequest;
import vn.phongtroxanh.backend.modules.user.presentation.dto.MatchingProfileResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingService {

    private final SwipeRepository swipeRepository;
    private final MatchRepository matchRepository;
    private final MatchingEngine matchingEngine;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserConsumableRepository userConsumableRepository;
    private final ConversationRepository conversationRepository;

    public List<RoommateCardDTO> getDiscoveryFeed() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        UserProfile myProfile = userProfileRepository.findById(currentUserId).orElse(null);

        List<UUID> swipedIds = swipeRepository.findSwipedTargetIds(currentUserId);
        Set<UUID> excludedIds = new HashSet<>(swipedIds);
        excludedIds.add(currentUserId);

        List<User> activeTenants = userRepository.findByRoleAndStatus(UserRole.TENANT, UserStatus.ACTIVE);
        List<RoommateCardDTO> feed = new ArrayList<>();

        for (User u : activeTenants) {
            if (excludedIds.contains(u.getId())) continue;

            UserProfile theirProfile = userProfileRepository.findById(u.getId()).orElse(null);
            if (theirProfile == null || Boolean.FALSE.equals(theirProfile.getIsPublic())) continue;

            var compatibility = matchingEngine.calculateCompatibility(myProfile, theirProfile);

            feed.add(RoommateCardDTO.builder()
                    .userId(u.getId())
                    .fullName(u.getFullName())
                    .avatarUrl(u.getAvatarUrl())
                    .gender(theirProfile.getGender())
                    .birthDate(theirProfile.getBirthDate())
                    .schoolOrCompany(Boolean.TRUE.equals(theirProfile.getShowSchool()) ? theirProfile.getSchoolOrCompany() : null)
                    .bio(theirProfile.getBio())
                    .trustScore(u.getTrustScore())
                    .budgetMin(theirProfile.getBudgetMin())
                    .budgetMax(theirProfile.getBudgetMax())
                    .preferredDistricts(theirProfile.getPreferredDistricts())
                    .interests(theirProfile.getInterests())
                    .earlySleeper(theirProfile.getEarlySleeper())
                    .isNeat(theirProfile.getIsNeat())
                    .allowGuests(theirProfile.getAllowGuests())
                    .nonSmoking(theirProfile.getNonSmoking())
                    .noiseTolerance(theirProfile.getNoiseTolerance())
                    .compatibilityScore(compatibility.getTotalScore())
                    .matchHighlights(compatibility.getMatchHighlights())
                    .build());
        }

        // Sort by compatibility score descending
        feed.sort((a, b) -> Integer.compare(b.getCompatibilityScore(), a.getCompatibilityScore()));

        return feed;
    }

    @Transactional
    public SwipeResponse swipe(SwipeRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        UUID targetId = request.getTargetUserId();

        if (currentUserId.equals(targetId)) {
            throw new BadRequestException("SELF_SWIPE_NOT_ALLOWED", "Bạn không thể tự quẹt chính mình");
        }

        User targetUser = userRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "Người dùng không tồn tại"));

        if (targetUser.getStatus() != UserStatus.ACTIVE) {
            throw new BadRequestException("USER_INACTIVE", "Người dùng này hiện không còn hoạt động");
        }

        if (swipeRepository.existsBySwiperIdAndTargetId(currentUserId, targetId)) {
            throw new ConflictException("ALREADY_SWIPED", "Bạn đã quẹt người dùng này trước đó");
        }

        // Deduct 1 swipe atomically
        int updated = userConsumableRepository.decrementSwipeAtomic(currentUserId);
        if (updated == 0) {
            throw new BadRequestException("OUT_OF_SWIPES", "Bạn đã hết lượt vuốt hôm nay (15 lượt/ngày). Vui lòng quay lại ngày mai hoặc nâng cấp gói");
        }

        if (request.getAction() == SwipeAction.SUPER_LIKE) {
            int superUpdated = userConsumableRepository.decrementSuperMatchAtomic(currentUserId);
            if (superUpdated == 0) {
                throw new BadRequestException("OUT_OF_SUPER_MATCHES", "Bạn đã hết lượt Siêu Tương Thích");
            }
        }

        Swipe swipe = Swipe.builder()
                .swiperId(currentUserId)
                .targetId(targetId)
                .action(request.getAction())
                .build();
        swipeRepository.save(swipe);

        UserConsumable consumable = userConsumableRepository.findById(currentUserId).orElse(null);
        int swipesLeft = consumable != null ? consumable.getSwipesLeft() : 0;

        // Check mutual match
        if (request.getAction() == SwipeAction.LIKE || request.getAction() == SwipeAction.SUPER_LIKE) {
            Optional<Swipe> reverseSwipe = swipeRepository.findReverseSwipe(
                    currentUserId, targetId, List.of(SwipeAction.LIKE, SwipeAction.SUPER_LIKE));

            if (reverseSwipe.isPresent()) {
                // Calculate match score
                UserProfile pA = userProfileRepository.findById(currentUserId).orElse(null);
                UserProfile pB = userProfileRepository.findById(targetId).orElse(null);
                int score = matchingEngine.calculateCompatibility(pA, pB).getTotalScore();

                Match match = Match.builder()
                        .userAId(currentUserId)
                        .userBId(targetId)
                        .compatibilityScore(BigDecimal.valueOf(score))
                        .status(MatchStatus.MATCHED)
                        .build();
                match = matchRepository.save(match);

                // Create conversation between matched pair
                Conversation conversation = conversationRepository.findBetweenUsers(currentUserId, targetId, null)
                        .orElseGet(() -> conversationRepository.save(Conversation.builder()
                                .participantOneId(currentUserId)
                                .participantTwoId(targetId)
                                .type(ConversationType.ROOMMATE)
                                .lastMessageAt(Instant.now())
                                .build()));

                log.info("MUTUAL MATCH CREATED between {} and {}, score: {}", currentUserId, targetId, score);

                return SwipeResponse.builder()
                        .isMatch(true)
                        .matchedScore(score)
                        .matchId(match.getId())
                        .conversationId(conversation.getId())
                        .swipesLeft(swipesLeft)
                        .build();
            }
        }

        return SwipeResponse.builder()
                .isMatch(false)
                .swipesLeft(swipesLeft)
                .build();
    }

    public List<MatchItemDTO> getMatches() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        List<Match> matches = matchRepository.findActiveMatchesByUser(currentUserId);
        List<MatchItemDTO> results = new ArrayList<>();

        for (Match m : matches) {
            UUID partnerId = m.getUserAId().equals(currentUserId) ? m.getUserBId() : m.getUserAId();
            User partner = userRepository.findById(partnerId).orElse(null);
            UserProfile profile = userProfileRepository.findById(partnerId).orElse(null);

            UUID conversationId = conversationRepository.findBetweenUsers(currentUserId, partnerId, null)
                    .map(Conversation::getId)
                    .orElse(null);

            if (partner != null) {
                results.add(MatchItemDTO.builder()
                        .matchId(m.getId())
                        .matchedScore(m.getCompatibilityScore() != null ? m.getCompatibilityScore().intValue() : 0)
                        .partnerId(partnerId)
                        .partnerName(partner.getFullName())
                        .partnerAvatar(partner.getAvatarUrl())
                        .partnerSchool(profile != null ? profile.getSchoolOrCompany() : null)
                        .partnerTrustScore(partner.getTrustScore())
                        .conversationId(conversationId)
                        .matchedAt(m.getCreatedAt())
                        .build());
            }
        }

        return results;
    }

    @Transactional
    public void unmatch(UUID matchId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("MATCH_NOT_FOUND", "Không tìm thấy tương thích"));

        if (!match.getUserAId().equals(currentUserId) && !match.getUserBId().equals(currentUserId)) {
            throw new ForbiddenException("BOLA_FORBIDDEN", "Bạn không thể hủy tương thích của người khác");
        }

        if (match.getStatus() == MatchStatus.UNMATCHED) {
            throw new BadRequestException("ALREADY_UNMATCHED", "Tương thích này đã bị hủy trước đó");
        }

        match.setStatus(MatchStatus.UNMATCHED);
        matchRepository.save(match);
        log.info("Unmatched match {}", matchId);
    }

    @Transactional
    public void boostProfile() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        int updated = userConsumableRepository.decrementBoostAtomic(currentUserId);
        if (updated == 0) {
            throw new BadRequestException("OUT_OF_BOOSTS", "Bạn đã hết lượt đẩy hồ sơ");
        }
        log.info("Boosted profile for user {}", currentUserId);
    }

    public CompatibilityDetailResponse getCompatibility(UUID targetUserId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        UserProfile myProfile = userProfileRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("PROFILE_NOT_FOUND", "Chưa có hồ sơ"));
        UserProfile theirProfile = userProfileRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("PROFILE_NOT_FOUND", "Người dùng chưa có hồ sơ"));

        var result = matchingEngine.calculateCompatibility(myProfile, theirProfile);

        return CompatibilityDetailResponse.builder()
                .currentUserId(currentUserId)
                .targetUserId(targetUserId)
                .totalScore(result.getTotalScore())
                .budgetScore(result.getBudgetScore())
                .locationScore(result.getLocationScore())
                .sleepScore(result.getSleepScore())
                .neatScore(result.getNeatScore())
                .guestScore(result.getGuestScore())
                .smokeScore(result.getSmokeScore())
                .noiseScore(result.getNoiseScore())
                .interestScore(result.getInterestScore())
                .hasDealbreaker(result.isHasDealbreaker())
                .highlights(result.getMatchHighlights())
                .build();
    }

    public MatchingProfileResponse getPreferences() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        UserProfile profile = userProfileRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("PROFILE_NOT_FOUND", "Chưa có hồ sơ"));

        return mapToMatchingProfileResponse(currentUserId, profile);
    }

    @Transactional
    public MatchingProfileResponse updatePreferences(MatchingProfileRequest request) {
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

        return mapToMatchingProfileResponse(currentUserId, profile);
    }

    private MatchingProfileResponse mapToMatchingProfileResponse(UUID userId, UserProfile profile) {
        return MatchingProfileResponse.builder()
                .userId(userId)
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
}
