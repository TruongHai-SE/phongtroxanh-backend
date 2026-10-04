package vn.phongtroxanh.backend.modules.review;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.phongtroxanh.backend.common.exception.BadRequestException;
import vn.phongtroxanh.backend.common.exception.ForbiddenException;
import vn.phongtroxanh.backend.common.security.UserPrincipal;
import vn.phongtroxanh.backend.common.storage.FileStoragePort;
import vn.phongtroxanh.backend.modules.rental.domain.*;
import vn.phongtroxanh.backend.modules.rental.infrastructure.repository.RentalRepository;
import vn.phongtroxanh.backend.modules.review.application.service.ReviewService;
import vn.phongtroxanh.backend.modules.review.infrastructure.repository.*;
import vn.phongtroxanh.backend.modules.review.presentation.dto.CreateReviewRequest;
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class ReviewIntegrityTest {
    @Test void terminatedUnoccupiedLeaseCannotBeReviewed() {
        var rentals = mock(RentalRepository.class);
        var reviews = mock(ReviewRepository.class);
        var service = new ReviewService(reviews, mock(ReviewDisputeRepository.class), rentals, mock(UserRepository.class), mock(TrustScoreLogRepository.class), mock(FileStoragePort.class));
        var tenant = UUID.randomUUID();
        var rental = Rental.builder().tenantId(tenant).landlordId(UUID.randomUUID()).status(RentalStatus.TERMINATED).build();
        rental.setId(UUID.randomUUID());
        when(rentals.findById(rental.getId())).thenReturn(Optional.of(rental));
        when(reviews.save(any())).thenAnswer(i -> i.getArgument(0));
        var principal = UserPrincipal.builder().id(tenant).role("TENANT").active(true).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        try {
            assertThrows(BadRequestException.class, () -> service.createReview(CreateReviewRequest.builder().rentalId(rental.getId()).rating(3).comment("Chưa vào ở").build()));
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test void userWithoutRentalCannotReviewRoom() {
        var rentals = mock(RentalRepository.class);
        var reviews = mock(ReviewRepository.class);
        var service = new ReviewService(reviews, mock(ReviewDisputeRepository.class), rentals, mock(UserRepository.class), mock(TrustScoreLogRepository.class), mock(FileStoragePort.class));
        var tenant = UUID.randomUUID();
        var roomId = UUID.randomUUID();

        when(rentals.findFirstByTenantIdAndRoomIdOrderByCreatedAtDesc(tenant, roomId)).thenReturn(Optional.empty());

        var principal = UserPrincipal.builder().id(tenant).role("TENANT").active(true).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        try {
            assertThrows(ForbiddenException.class, () -> service.createReview(CreateReviewRequest.builder().roomId(roomId).rating(4).comment("Chưa từng thuê").build()));
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test void tenantWithUncheckedInRentalCannotReviewRoom() {
        var rentals = mock(RentalRepository.class);
        var reviews = mock(ReviewRepository.class);
        var service = new ReviewService(reviews, mock(ReviewDisputeRepository.class), rentals, mock(UserRepository.class), mock(TrustScoreLogRepository.class), mock(FileStoragePort.class));
        var tenant = UUID.randomUUID();
        var roomId = UUID.randomUUID();
        var rental = Rental.builder()
                .tenantId(tenant)
                .landlordId(UUID.randomUUID())
                .roomId(roomId)
                .status(RentalStatus.PENDING_CHECKIN)
                .checkedInAt(null)
                .build();
        rental.setId(UUID.randomUUID());

        when(rentals.findFirstByTenantIdAndRoomIdOrderByCreatedAtDesc(tenant, roomId)).thenReturn(Optional.of(rental));

        var principal = UserPrincipal.builder().id(tenant).role("TENANT").active(true).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        try {
            assertThrows(BadRequestException.class, () -> service.createReview(CreateReviewRequest.builder().roomId(roomId).rating(5).comment("Chưa checkin").build()));
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test void tenantWithCheckedInRentalCanReviewRoom() {
        var rentals = mock(RentalRepository.class);
        var reviews = mock(ReviewRepository.class);
        var users = mock(UserRepository.class);
        var service = new ReviewService(reviews, mock(ReviewDisputeRepository.class), rentals, users, mock(TrustScoreLogRepository.class), mock(FileStoragePort.class));
        var tenant = UUID.randomUUID();
        var landlord = UUID.randomUUID();
        var roomId = UUID.randomUUID();
        var rental = Rental.builder()
                .tenantId(tenant)
                .landlordId(landlord)
                .roomId(roomId)
                .status(RentalStatus.CHECKED_IN)
                .checkedInAt(Instant.now())
                .build();
        rental.setId(UUID.randomUUID());

        when(rentals.findFirstByTenantIdAndRoomIdOrderByCreatedAtDesc(tenant, roomId)).thenReturn(Optional.of(rental));
        when(reviews.existsByRentalContractIdAndReviewerId(rental.getId(), tenant)).thenReturn(false);
        when(reviews.save(any())).thenAnswer(i -> {
            var r = (vn.phongtroxanh.backend.modules.review.domain.Review) i.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        var principal = UserPrincipal.builder().id(tenant).role("TENANT").active(true).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        try {
            var response = service.createReview(CreateReviewRequest.builder().roomId(roomId).rating(5).comment("Phòng rất tốt").build());
            assertNotNull(response);
            assertTrue(response.getIsVerifiedStay());
        } finally { SecurityContextHolder.clearContext(); }
    }
}
