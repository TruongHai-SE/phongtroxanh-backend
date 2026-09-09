package vn.phongtroxanh.backend.modules.matching.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.phongtroxanh.backend.modules.user.domain.UserProfile;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MatchingEngineTest {

    private MatchingEngine matchingEngine;

    @BeforeEach
    void setUp() {
        matchingEngine = new MatchingEngine();
    }

    @Test
    @DisplayName("Should return high score for identical profiles")
    void testIdenticalProfiles() {
        UserProfile u1 = UserProfile.builder()
                .budgetMin(BigDecimal.valueOf(2000000))
                .budgetMax(BigDecimal.valueOf(4000000))
                .preferredDistricts(List.of("Quận 1", "Bình Thạnh"))
                .earlySleeper(true)
                .isNeat(true)
                .allowGuests(false)
                .nonSmoking(true)
                .noiseTolerance(2)
                .interests(List.of("GYM", "READING", "CODING"))
                .build();

        UserProfile u2 = UserProfile.builder()
                .budgetMin(BigDecimal.valueOf(2000000))
                .budgetMax(BigDecimal.valueOf(4000000))
                .preferredDistricts(List.of("Quận 1", "Bình Thạnh"))
                .earlySleeper(true)
                .isNeat(true)
                .allowGuests(false)
                .nonSmoking(true)
                .noiseTolerance(2)
                .interests(List.of("GYM", "READING", "CODING"))
                .build();

        MatchingEngine.CompatibilityResult result = matchingEngine.calculateCompatibility(u1, u2);

        assertNotNull(result);
        assertTrue(result.getTotalScore() >= 90, "Expected score >= 90, got: " + result.getTotalScore());
        assertFalse(result.getMatchHighlights().isEmpty());
    }

    @Test
    @DisplayName("Should heavily penalize smoking dealbreaker (50% penalty)")
    void testSmokingDealbreaker() {
        UserProfile nonSmoker = UserProfile.builder()
                .budgetMin(BigDecimal.valueOf(2000000))
                .budgetMax(BigDecimal.valueOf(4000000))
                .preferredDistricts(List.of("Quận 1"))
                .earlySleeper(true)
                .isNeat(true)
                .allowGuests(false)
                .nonSmoking(true)
                .noiseTolerance(2)
                .interests(List.of("GYM"))
                .build();

        UserProfile smoker = UserProfile.builder()
                .budgetMin(BigDecimal.valueOf(2000000))
                .budgetMax(BigDecimal.valueOf(4000000))
                .preferredDistricts(List.of("Quận 1"))
                .earlySleeper(true)
                .isNeat(true)
                .allowGuests(false)
                .nonSmoking(false)
                .noiseTolerance(2)
                .interests(List.of("GYM"))
                .build();

        MatchingEngine.CompatibilityResult result = matchingEngine.calculateCompatibility(nonSmoker, smoker);

        assertNotNull(result);
        assertTrue(result.isHasDealbreaker());
        assertTrue(result.getTotalScore() <= 50, "Expected score <= 50 with dealbreaker penalty, got: " + result.getTotalScore());
    }
}
