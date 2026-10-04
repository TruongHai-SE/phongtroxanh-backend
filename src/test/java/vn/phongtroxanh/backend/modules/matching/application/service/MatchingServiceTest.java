package vn.phongtroxanh.backend.modules.matching.application.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.ArgumentCaptor;
import vn.phongtroxanh.backend.common.exception.BadRequestException;
import vn.phongtroxanh.backend.common.exception.ResourceNotFoundException;
import vn.phongtroxanh.backend.common.security.SecurityUtils;
import vn.phongtroxanh.backend.modules.chat.infrastructure.repository.ConversationRepository;
import vn.phongtroxanh.backend.modules.chat.domain.Conversation;
import vn.phongtroxanh.backend.modules.matching.domain.Match;
import vn.phongtroxanh.backend.modules.matching.domain.Swipe;
import vn.phongtroxanh.backend.modules.notification.application.service.NotificationService;
import vn.phongtroxanh.backend.modules.notification.domain.NotificationType;
import vn.phongtroxanh.backend.modules.matching.domain.MatchingEngine;
import vn.phongtroxanh.backend.modules.matching.domain.SwipeAction;
import vn.phongtroxanh.backend.modules.matching.infrastructure.repository.MatchRepository;
import vn.phongtroxanh.backend.modules.matching.infrastructure.repository.SwipeRepository;
import vn.phongtroxanh.backend.modules.matching.presentation.dto.SwipeRequest;
import vn.phongtroxanh.backend.modules.user.domain.*;
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.*;
import vn.phongtroxanh.backend.modules.user.presentation.dto.MatchingProfileRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MatchingServiceTest {
    private final UUID userId = UUID.randomUUID();
    private final UserRepository users = mock(UserRepository.class);
    private final UserProfileRepository profiles = mock(UserProfileRepository.class);
    private final UserConsumableRepository consumables = mock(UserConsumableRepository.class);
    private final SwipeRepository swipes = mock(SwipeRepository.class);
    private final MatchRepository matches = mock(MatchRepository.class);
    private final ConversationRepository conversations = mock(ConversationRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final MatchingService service = new MatchingService(swipes, matches,
            new MatchingEngine(), users, profiles, consumables, conversations, notifications, entityManager);
    private MockedStatic<SecurityUtils> security;

    @BeforeEach void authenticate() {
        security = mockStatic(SecurityUtils.class);
        security.when(SecurityUtils::getCurrentUserId).thenReturn(userId);
    }
    @AfterEach void clearAuthentication() { security.close(); }

    @Test void profileBoostPersistsActivationAfterSpendingCredit() {
        when(consumables.decrementBoostAtomic(userId)).thenReturn(1);
        when(consumables.findById(userId)).thenReturn(Optional.of(UserConsumable.builder().userId(userId).build()));
        Instant before = Instant.now();
        service.boostProfile();
        var saved = ArgumentCaptor.forClass(UserConsumable.class);
        verify(consumables).save(saved.capture());
        assertThat(saved.getValue().getProfileBoostExpiresAt()).isBetween(before.plus(24, ChronoUnit.HOURS),
                Instant.now().plus(24, ChronoUnit.HOURS));
    }

    @Test void cannotSwipePrivateProfileEvenByKnownId() {
        UUID target = UUID.randomUUID();
        when(users.findById(target)).thenReturn(Optional.of(User.builder().role(UserRole.TENANT).build()));
        when(profiles.findById(target)).thenReturn(Optional.of(UserProfile.builder().isPublic(false).build()));
        when(consumables.decrementSwipeAtomic(userId)).thenReturn(1);
        assertThatThrownBy(() -> service.swipe(SwipeRequest.builder().targetUserId(target).action(SwipeAction.DISLIKE).build()))
                .isInstanceOf(BadRequestException.class);
        verify(consumables, never()).decrementSwipeAtomic(any());
    }

    @Test void compatibilityCannotExposePrivateProfile() {
        UUID target = UUID.randomUUID();
        when(users.findById(target)).thenReturn(Optional.of(User.builder().role(UserRole.TENANT).build()));
        when(profiles.findById(userId)).thenReturn(Optional.of(UserProfile.builder().build()));
        when(profiles.findById(target)).thenReturn(Optional.of(UserProfile.builder().isPublic(false).build()));
        assertThatThrownBy(() -> service.getCompatibility(target)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test void preferencesRejectReversedBudgetRange() {
        when(profiles.findById(userId)).thenReturn(Optional.of(UserProfile.builder().budgetMax(BigDecimal.valueOf(3_000_000)).build()));
        assertThatThrownBy(() -> service.updatePreferences(MatchingProfileRequest.builder()
                .budgetMin(BigDecimal.valueOf(5_000_000)).build())).isInstanceOf(BadRequestException.class);
        verify(profiles, never()).save(any());
    }

    @Test void activeProfileBoostRanksBeforeHigherCompatibility() {
        User compatible = User.builder().build();
        compatible.setId(UUID.randomUUID());
        User boosted = User.builder().build();
        boosted.setId(UUID.randomUUID());
        when(users.findByRoleAndStatus(UserRole.TENANT, UserStatus.ACTIVE)).thenReturn(List.of(compatible, boosted));
        when(profiles.findById(userId)).thenReturn(Optional.of(UserProfile.builder().build()));
        when(profiles.findById(compatible.getId())).thenReturn(Optional.of(UserProfile.builder().build()));
        when(profiles.findById(boosted.getId())).thenReturn(Optional.of(UserProfile.builder()
                .budgetMin(BigDecimal.valueOf(2_000_000)).budgetMax(BigDecimal.valueOf(5_000_000)).build()));
        when(consumables.findByProfileBoostExpiresAtAfter(any(Instant.class))).thenReturn(List.of(UserConsumable.builder()
                .userId(boosted.getId()).profileBoostExpiresAt(Instant.now().plusSeconds(60)).build()));
        assertThat(service.getDiscoveryFeed()).extracting(card -> card.getUserId()).containsExactly(boosted.getId(), compatible.getId());
    }

    @Test void mutualMatchNotifiesBothUsersWithTheirConversation() {
        UUID target = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        when(users.findById(target)).thenReturn(Optional.of(User.builder().build()));
        when(profiles.findById(target)).thenReturn(Optional.of(UserProfile.builder().build()));
        when(consumables.decrementSwipeAtomic(userId)).thenReturn(1);
        when(swipes.findReverseSwipe(userId, target, List.of(SwipeAction.LIKE)))
                .thenReturn(Optional.of(Swipe.builder().action(SwipeAction.LIKE).build()));
        when(matches.save(any())).thenAnswer(i -> {
            Match match = i.getArgument(0);
            match.setId(matchId);
            return match;
        });
        when(conversations.findBetweenUsers(userId, target, null))
                .thenReturn(Optional.of(Conversation.builder().id(conversationId).build()));
        assertThat(service.swipe(SwipeRequest.builder().targetUserId(target).action(SwipeAction.LIKE).build()).isMatch()).isTrue();
        verify(notifications).create(eq(userId), anyString(), anyString(), eq(NotificationType.MATCH),
                eq(Map.of("matchId", matchId.toString(), "conversationId", conversationId.toString(), "partnerId", target.toString())));
        verify(notifications).create(eq(target), anyString(), anyString(), eq(NotificationType.MATCH),
                eq(Map.of("matchId", matchId.toString(), "conversationId", conversationId.toString(), "partnerId", userId.toString())));
    }
}
