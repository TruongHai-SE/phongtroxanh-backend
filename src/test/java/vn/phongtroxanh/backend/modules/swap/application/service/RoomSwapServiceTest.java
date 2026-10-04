package vn.phongtroxanh.backend.modules.swap.application.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import jakarta.persistence.EntityManager;
import vn.phongtroxanh.backend.modules.rental.domain.*;
import vn.phongtroxanh.backend.common.exception.*;
import vn.phongtroxanh.backend.common.security.SecurityUtils;
import vn.phongtroxanh.backend.modules.rental.infrastructure.repository.RentalRepository;
import vn.phongtroxanh.backend.modules.room.domain.Room;
import vn.phongtroxanh.backend.modules.room.domain.RoomStatus;
import vn.phongtroxanh.backend.modules.user.domain.User;
import vn.phongtroxanh.backend.modules.room.infrastructure.repository.RoomRepository;
import vn.phongtroxanh.backend.modules.swap.domain.*;
import vn.phongtroxanh.backend.modules.swap.infrastructure.repository.SwapRequestRepository;
import vn.phongtroxanh.backend.modules.swap.presentation.dto.*;
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.UserRepository;
import vn.phongtroxanh.backend.modules.notification.application.service.NotificationService;
import java.util.*;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomSwapServiceTest {
    @Mock SwapRequestRepository swaps;
    @Mock RoomRepository rooms;
    @Mock UserRepository users;
    @Mock RentalRepository rentals;
    @Mock EntityManager entityManager;
    @Mock NotificationService notifications;
    @InjectMocks RoomSwapService service;
    UUID owner = UUID.randomUUID(), applicant = UUID.randomUUID(), landlord = UUID.randomUUID(), roomId = UUID.randomUUID();

    @Test
    void postRequiresRealCheckedInLeaseholder() {
        Room room = Room.builder().landlordId(landlord).build();
        room.setId(roomId);
        when(rooms.findById(roomId)).thenReturn(Optional.of(room));
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(owner);
            assertThrows(ForbiddenException.class, () -> service.createSwapPost(CreateSwapRequest.builder()
                    .currentRoomId(roomId).isLeaseholder(true).build()));
        }
        verify(swaps, never()).save(any());
    }

    @Test
    void ownerCannotApproveWithoutLandlordDecision() {
        SwapRequest swap = post(SwapStatus.MATCHING);
        swap.setMatchedTenantId(applicant);
        when(swaps.findById(swap.getId())).thenReturn(Optional.of(swap));
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(owner);
            assertThrows(ForbiddenException.class, () -> service.updateSwapProposalStatus(swap.getId(),
                    UpdateSwapRequestStatusRequest.builder().status(SwapStatus.APPROVED).build()));
        }
    }

    @Test
    void landlordCannotApproveUnmatchedPost() {
        SwapRequest swap = post(SwapStatus.OPEN);
        when(swaps.findById(swap.getId())).thenReturn(Optional.of(swap));
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(landlord);
            assertThrows(BadRequestException.class, () -> service.landlordDecision(swap.getId(), true));
        }
    }

    @Test
    void proposalMessageSurvivesOwnerAcceptance() {
        SwapRequest swap = post(SwapStatus.OPEN);
        when(users.findById(applicant)).thenReturn(Optional.of(User.builder().build()));
        when(rentals.findByRoomIdAndStatus(roomId, RentalStatus.CHECKED_IN)).thenReturn(List.of(
                Rental.builder().tenantId(owner).landlordId(landlord).roomId(roomId).status(RentalStatus.CHECKED_IN).build()));
        when(swaps.findById(swap.getId())).thenReturn(Optional.of(swap));
        when(swaps.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(applicant);
            service.sendSwapProposal(swap.getId(), SendSwapProposalRequest.builder().message("Can move in next week").build());
            security.when(SecurityUtils::getCurrentUserId).thenReturn(owner);
            SwapProposalResponse response = service.updateSwapProposalStatus(swap.getId(),
                    UpdateSwapRequestStatusRequest.builder().status(SwapStatus.PENDING_LANDLORD).build());
            assertEquals("Can move in next week", response.getMessage());
            assertEquals(applicant, response.getRequesterId());
            assertNull(response.getOfferedRoomId());
        }
    }

    @Test
    void completionCreatesIncomingLeaseAndEndsOutgoingLease() {
        SwapRequest swap = post(SwapStatus.APPROVED);
        swap.setLandlordDecision(SwapStatus.APPROVED);
        swap.setMatchedTenantId(applicant);
        Rental outgoing = Rental.builder().id(UUID.randomUUID()).roomId(roomId).tenantId(owner).landlordId(landlord)
                .status(RentalStatus.CHECKED_IN).startDate(LocalDate.now()).endDate(LocalDate.now().plusMonths(6)).build();
        Room room = Room.builder().landlordId(landlord).status(RoomStatus.RENTED).build();
        room.setId(roomId);
        when(swaps.findById(swap.getId())).thenReturn(Optional.of(swap));
        lenient().when(rooms.findById(roomId)).thenReturn(Optional.of(room));
        lenient().when(rentals.findByRoomIdAndStatus(roomId, RentalStatus.CHECKED_IN)).thenReturn(List.of(outgoing));
        when(users.findById(applicant)).thenReturn(Optional.of(User.builder().build()));
        when(swaps.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(landlord);
            service.updateSwapProposalStatus(swap.getId(), UpdateSwapRequestStatusRequest.builder().status(SwapStatus.COMPLETED).build());
        }
        assertEquals(RentalStatus.TERMINATED, outgoing.getStatus());
        verify(rentals).save(argThat(r -> applicant.equals(r.getTenantId()) && roomId.equals(r.getRoomId())
                && landlord.equals(r.getLandlordId()) && r.getStatus() == RentalStatus.PENDING_CHECKIN
                && outgoing.getEndDate().equals(r.getEndDate())));
    }

    private SwapRequest post(SwapStatus status) {
        SwapRequest swap = SwapRequest.builder().requesterId(owner).currentRoomId(roomId).landlordId(landlord)
                .reason("Owner description").status(status).build();
        swap.setId(UUID.randomUUID());
        return swap;
    }
}
