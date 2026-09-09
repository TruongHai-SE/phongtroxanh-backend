package vn.phongtroxanh.backend.modules.matching.domain;

import lombok.Builder;
import lombok.Getter;
import org.springframework.stereotype.Component;
import vn.phongtroxanh.backend.modules.user.domain.UserProfile;

import java.math.BigDecimal;
import java.util.*;

@Component
public class MatchingEngine {

    @Getter
    @Builder
    public static class CompatibilityResult {
        private int totalScore;
        private int budgetScore;
        private int locationScore;
        private int sleepScore;
        private int neatScore;
        private int guestScore;
        private int smokeScore;
        private int noiseScore;
        private int interestScore;
        private boolean hasDealbreaker;
        private List<String> matchHighlights;
    }

    public CompatibilityResult calculateCompatibility(UserProfile pA, UserProfile pB) {
        if (pA == null || pB == null) {
            return CompatibilityResult.builder().totalScore(50).build();
        }

        List<String> highlights = new ArrayList<>();

        // 1. Budget Score (25%)
        int budgetScore = calculateBudgetScore(pA, pB);
        if (budgetScore >= 80) highlights.add("Mức ngân sách thuê phòng rất tương đồng");

        // 2. Location Score (20%)
        int locationScore = calculateJaccard(pA.getPreferredDistricts(), pB.getPreferredDistricts());
        if (locationScore >= 50) highlights.add("Cùng khu vực quận/huyện mong muốn");

        // 3. Sleep Schedule (15%)
        int sleepScore = 50;
        if (pA.getEarlySleeper() != null && pB.getEarlySleeper() != null) {
            sleepScore = pA.getEarlySleeper().equals(pB.getEarlySleeper()) ? 100 : 20;
            if (sleepScore == 100) highlights.add("Giờ giấc sinh hoạt và ngủ nghỉ trùng khớp");
        }

        // 4. Neatness (10%)
        int neatScore = 50;
        if (pA.getIsNeat() != null && pB.getIsNeat() != null) {
            neatScore = pA.getIsNeat().equals(pB.getIsNeat()) ? 100 : 40;
            if (neatScore == 100) highlights.add("Đồng điệu về tiêu chuẩn giữ gìn vệ sinh");
        }

        // 5. Guest Policy (10%)
        int guestScore = 50;
        if (pA.getAllowGuests() != null && pB.getAllowGuests() != null) {
            guestScore = pA.getAllowGuests().equals(pB.getAllowGuests()) ? 100 : 30;
        }

        // 6. Smoking (10% - Dealbreaker)
        int smokeScore = 50;
        boolean smokingDealbreaker = false;
        if (pA.getNonSmoking() != null && pB.getNonSmoking() != null) {
            if (pA.getNonSmoking() && pB.getNonSmoking()) {
                smokeScore = 100;
                highlights.add("Cả hai cùng không hút thuốc");
            } else if (!pA.getNonSmoking() && !pB.getNonSmoking()) {
                smokeScore = 80;
            } else {
                smokeScore = 0;
                smokingDealbreaker = true;
            }
        }

        // 7. Noise Tolerance (5%)
        int noiseScore = 70;
        if (pA.getNoiseTolerance() != null && pB.getNoiseTolerance() != null) {
            int diff = Math.abs(pA.getNoiseTolerance() - pB.getNoiseTolerance());
            noiseScore = Math.max(0, 100 - diff * 25);
        }

        // 8. Interests (5%)
        int interestScore = calculateJaccard(pA.getInterests(), pB.getInterests());
        if (interestScore >= 40) highlights.add("Có nhiều sở thích và gu giải trí chung");

        // Weighted sum
        double rawScore = (budgetScore * 0.25)
                + (locationScore * 0.20)
                + (sleepScore * 0.15)
                + (neatScore * 0.10)
                + (guestScore * 0.10)
                + (smokeScore * 0.10)
                + (noiseScore * 0.05)
                + (interestScore * 0.05);

        if (smokingDealbreaker) {
            rawScore = rawScore * 0.5; // Dealbreaker penalty
        }

        int finalScore = (int) Math.round(Math.min(100.0, Math.max(0.0, rawScore)));

        return CompatibilityResult.builder()
                .totalScore(finalScore)
                .budgetScore(budgetScore)
                .locationScore(locationScore)
                .sleepScore(sleepScore)
                .neatScore(neatScore)
                .guestScore(guestScore)
                .smokeScore(smokeScore)
                .noiseScore(noiseScore)
                .interestScore(interestScore)
                .hasDealbreaker(smokingDealbreaker)
                .matchHighlights(highlights)
                .build();
    }

    private int calculateBudgetScore(UserProfile a, UserProfile b) {
        BigDecimal minA = a.getBudgetMin() != null ? a.getBudgetMin() : BigDecimal.ZERO;
        BigDecimal maxA = a.getBudgetMax() != null ? a.getBudgetMax() : BigDecimal.valueOf(10_000_000);
        BigDecimal minB = b.getBudgetMin() != null ? b.getBudgetMin() : BigDecimal.ZERO;
        BigDecimal maxB = b.getBudgetMax() != null ? b.getBudgetMax() : BigDecimal.valueOf(10_000_000);

        BigDecimal overlapMin = minA.max(minB);
        BigDecimal overlapMax = maxA.min(maxB);

        if (overlapMax.compareTo(overlapMin) <= 0) {
            return 20; // No overlap but small distance
        }

        BigDecimal overlap = overlapMax.subtract(overlapMin);
        BigDecimal union = maxA.max(maxB).subtract(minA.min(minB));

        if (union.compareTo(BigDecimal.ZERO) == 0) return 100;

        double ratio = overlap.doubleValue() / union.doubleValue();
        return (int) Math.round(ratio * 100);
    }

    private int calculateJaccard(List<String> listA, List<String> listB) {
        if (listA == null || listB == null || listA.isEmpty() || listB.isEmpty()) {
            return 40; // Neutral baseline
        }
        Set<String> setA = new HashSet<>(listA);
        Set<String> setB = new HashSet<>(listB);

        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);

        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);

        if (union.isEmpty()) return 50;

        return (int) Math.round(((double) intersection.size() / union.size()) * 100);
    }
}
