package vn.phongtroxanh.backend.modules.user.application.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import vn.phongtroxanh.backend.common.exception.BadRequestException;
import vn.phongtroxanh.backend.common.exception.ConflictException;
import vn.phongtroxanh.backend.common.exception.ResourceNotFoundException;
import vn.phongtroxanh.backend.common.security.SecurityUtils;
import vn.phongtroxanh.backend.common.storage.FileStoragePort;
import vn.phongtroxanh.backend.modules.chat.infrastructure.repository.ConversationRepository;
import vn.phongtroxanh.backend.modules.matching.application.service.MatchingService;
import vn.phongtroxanh.backend.modules.notification.application.service.NotificationService;
import vn.phongtroxanh.backend.modules.matching.domain.MatchingEngine;
import vn.phongtroxanh.backend.modules.matching.infrastructure.repository.MatchRepository;
import vn.phongtroxanh.backend.modules.matching.infrastructure.repository.SwipeRepository;
import vn.phongtroxanh.backend.modules.rental.domain.Rental;
import vn.phongtroxanh.backend.modules.rental.domain.RentalStatus;
import vn.phongtroxanh.backend.modules.rental.infrastructure.repository.RentalRepository;
import vn.phongtroxanh.backend.modules.review.domain.Review;
import vn.phongtroxanh.backend.modules.review.infrastructure.repository.ReviewRepository;
import vn.phongtroxanh.backend.modules.user.domain.*;
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.*;
import vn.phongtroxanh.backend.modules.user.presentation.dto.KycSubmitRequest;
import vn.phongtroxanh.backend.modules.user.presentation.dto.MatchingProfileRequest;
import vn.phongtroxanh.backend.modules.user.presentation.dto.UpdateUserRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserIntegrityTest {
    @Mock UserRepository users;
    @Mock UserProfileRepository profiles;
    @Mock UserConsumableRepository consumables;
    @Mock UserVerificationRepository verifications;
    @Mock TrustScoreLogRepository trustLogs;
    @Mock ReviewRepository reviews;
    @Mock RentalRepository rentals;
    @Mock FileStoragePort storage;
    @Mock MatchingService matchingService;
    @Mock EntityManager entityManager;
    @InjectMocks UserService service;
    private final UUID userId = UUID.randomUUID();
    private MockedStatic<SecurityUtils> security;

    @BeforeEach void authenticate() {
        security = mockStatic(SecurityUtils.class);
        security.when(SecurityUtils::getCurrentUserId).thenReturn(userId);
    }
    @AfterEach void clearAuthentication() { security.close(); }

    @Test void profileUpdateLocksUserBeforeResettingQuotaToMatchAuthLockOrder() {
        User user = User.builder().fullName("Old name").passwordHash("current-password").build();
        user.setId(userId);
        when(users.findById(userId)).thenReturn(Optional.of(user));

        var response = service.updateMe(UpdateUserRequest.builder().fullName("Updated name").build());

        var order = inOrder(users, entityManager, consumables);
        order.verify(users).findById(userId);
        order.verify(entityManager).refresh(user, LockModeType.PESSIMISTIC_WRITE);
        order.verify(consumables).resetDailySwipesAtomic(eq(userId), any(java.time.LocalDate.class));
        order.verify(users).save(user);
        assertThat(response.getFullName()).isEqualTo("Updated name");
        assertThat(user.getPasswordHash()).isEqualTo("current-password");
    }

    @Test void publicApiCannotExposePrivateProfile() {
        UUID target = UUID.randomUUID();
        when(users.findById(target)).thenReturn(Optional.of(User.builder().build()));
        when(profiles.findById(target)).thenReturn(Optional.of(UserProfile.builder().isPublic(false).build()));
        assertThatThrownBy(() -> service.getPublicProfile(target)).isInstanceOf(ResourceNotFoundException.class);
    }

    @ParameterizedTest @EnumSource(value = UserStatus.class, names = {"LOCKED", "DELETED"})
    void publicApiCannotExposeInactiveAccount(UserStatus status) {
        UUID target = UUID.randomUUID();
        when(users.findById(target)).thenReturn(Optional.of(User.builder().status(status).build()));
        when(profiles.findById(target)).thenReturn(Optional.of(UserProfile.builder().build()));
        assertThatThrownBy(() -> service.getPublicProfile(target)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test void ownerMayInspectPrivateProfile() {
        when(users.findById(userId)).thenReturn(Optional.of(User.builder().build()));
        when(profiles.findById(userId)).thenReturn(Optional.of(UserProfile.builder().isPublic(false).build()));
        assertThatCode(() -> service.getPublicProfile(userId)).doesNotThrowAnyException();
    }

    @Test void adminMayInspectPrivateLockedProfile() {
        UUID target = UUID.randomUUID();
        security.when(() -> SecurityUtils.hasRole("ADMIN")).thenReturn(true);
        when(users.findById(target)).thenReturn(Optional.of(User.builder().status(UserStatus.LOCKED).build()));
        when(profiles.findById(target)).thenReturn(Optional.of(UserProfile.builder().isPublic(false).build()));
        assertThatCode(() -> service.getPublicProfile(target)).doesNotThrowAnyException();
    }

    @Test void userMatchingRouteValidatesEffectiveBudgetAfterPartialUpdate() {
        when(profiles.findById(userId)).thenReturn(Optional.of(UserProfile.builder().budgetMax(BigDecimal.valueOf(4_000_000)).build()));
        // Exercise the real shared matching logic if the user route delegates to it.
        MatchingService realMatching = new MatchingService(mock(SwipeRepository.class), mock(MatchRepository.class),
                new MatchingEngine(), users, profiles, consumables, mock(ConversationRepository.class), mock(NotificationService.class), entityManager);
        lenient().when(matchingService.updatePreferences(any())).thenAnswer(i -> realMatching.updatePreferences(i.getArgument(0)));
        assertThatThrownBy(() -> service.updateMatchingProfile(MatchingProfileRequest.builder()
                .budgetMin(BigDecimal.valueOf(5_000_000)).build())).isInstanceOf(BadRequestException.class);
        verify(profiles, never()).save(any());
    }

    @ParameterizedTest @EnumSource(value = RentalStatus.class, names = {"PENDING_CHECKIN", "CHECKED_IN"})
    void accountCannotBeDeletedWhileTenantHasActiveContract(RentalStatus status) {
        when(users.findById(userId)).thenReturn(Optional.of(User.builder().build()));
        when(rentals.findByTenantIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(Rental.builder().status(status).build()));
        assertThatThrownBy(service::deleteAccount).isInstanceOf(ConflictException.class);
        verify(users, never()).save(any());
    }

    @Test void accountCannotBeDeletedWhileLandlordHasActiveContract() {
        when(users.findById(userId)).thenReturn(Optional.of(User.builder().build()));
        when(rentals.findByLandlordIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(Rental.builder().status(RentalStatus.CHECKED_IN).build()));
        assertThatThrownBy(service::deleteAccount).isInstanceOf(ConflictException.class);
        verify(users, never()).save(any());
    }

    @Test void accountMayBeDeletedAfterAllContractsEnded() {
        User user = User.builder().build();
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(rentals.findByTenantIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(Rental.builder().status(RentalStatus.TERMINATED).build()));
        service.deleteAccount();
        assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
        verify(users).save(user);
    }

    @Test void trustBreakdownIgnoresModeratedReviewsAndNeverCheckedInContract() {
        when(users.findById(userId)).thenReturn(Optional.of(User.builder().build()));
        when(reviews.findByRevieweeIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(
                Review.builder().rating(1).status("ACTIVE").build(),
                Review.builder().rating(5).status("REMOVED").build(),
                Review.builder().rating(5).status("UNDER_DISPUTE").build()));
        when(rentals.findByTenantIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(Rental.builder().status(RentalStatus.TERMINATED).build()));
        var response = service.getTrustScore();
        assertThat(response.getReviewScore()).isEqualTo(6);
        assertThat(response.getRentalDurationScore()).isZero();
    }

    @ParameterizedTest @EnumSource(value = VerificationStatus.class, names = {"PENDING", "APPROVED"})
    void cannotResubmitKycWhilePendingOrApproved(VerificationStatus status) {
        lenient().when(entityManager.find(User.class, userId, LockModeType.PESSIMISTIC_WRITE)).thenReturn(User.builder().build());
        when(verifications.existsByUserIdAndStatusIn(userId, List.of(VerificationStatus.PENDING, VerificationStatus.APPROVED)))
                .thenReturn(true);
        lenient().when(verifications.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThatThrownBy(() -> service.submitKyc(KycSubmitRequest.builder().build())).isInstanceOf(ConflictException.class);
        verify(entityManager).find(User.class, userId, LockModeType.PESSIMISTIC_WRITE);
        verify(verifications, never()).save(any());
    }

    @Test void rejectedKycMayBeSubmittedAgain() {
        when(entityManager.find(User.class, userId, LockModeType.PESSIMISTIC_WRITE)).thenReturn(User.builder().build());
        when(verifications.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThat(service.submitKyc(KycSubmitRequest.builder().build()).getStatus()).isEqualTo(VerificationStatus.PENDING);
        verify(verifications).save(any());
    }
}
