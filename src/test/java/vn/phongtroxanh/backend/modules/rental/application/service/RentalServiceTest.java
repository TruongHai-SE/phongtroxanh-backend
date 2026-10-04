package vn.phongtroxanh.backend.modules.rental.application.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import vn.phongtroxanh.backend.common.exception.BadRequestException;
import vn.phongtroxanh.backend.common.exception.ConflictException;
import vn.phongtroxanh.backend.common.security.SecurityUtils;
import vn.phongtroxanh.backend.modules.rental.domain.*;
import vn.phongtroxanh.backend.modules.rental.infrastructure.repository.RentalRepository;
import vn.phongtroxanh.backend.modules.rental.presentation.dto.*;
import vn.phongtroxanh.backend.modules.room.domain.*;
import vn.phongtroxanh.backend.modules.room.infrastructure.repository.RoomRepository;
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.*;
import vn.phongtroxanh.backend.modules.user.domain.*;
import vn.phongtroxanh.backend.modules.notification.application.service.NotificationService;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RentalServiceTest {
    @Mock RentalRepository rentals;
    @Mock RoomRepository rooms;
    @Mock UserRepository users;
    @Mock TrustScoreLogRepository scores;
    @Mock RedisTemplate<String, Object> redis;
    @Mock ValueOperations<String, Object> values;
    @Mock EntityManager entityManager;
    @Mock NotificationService notifications;
    @InjectMocks RentalService service;
    UUID tenantId = UUID.randomUUID(), landlordId = UUID.randomUUID(), roomId = UUID.randomUUID();

    @Test
    void expiredQrCannotUsePersistedDatabaseToken() {
        Rental rental = rental();
        when(rentals.findById(rental.getId())).thenReturn(Optional.of(rental));
        when(redis.opsForValue()).thenReturn(values);
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(landlordId);
            assertThrows(BadRequestException.class, () -> service.verifyCheckIn(rental.getId(),
                    VerifyCheckInRequest.builder().checkInCode("EXPIREDTOKEN").build()));
        }
        assertEquals(RentalStatus.PENDING_CHECKIN, rental.getStatus());
        verify(scores, never()).save(any());
    }

    @Test
    void cancellingBeforeCheckInDoesNotCreateTerminatedStay() {
        Rental rental = rental();
        when(rentals.findById(rental.getId())).thenReturn(Optional.of(rental));
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(landlordId);
            service.terminateRental(rental.getId());
        }
        assertEquals(RentalStatus.CANCELLED, rental.getStatus());
    }

    @Test
    void rejectsEndBeforeStart() {
        Room room = Room.builder().landlordId(landlordId).status(RoomStatus.AVAILABLE).build();
        room.setId(roomId);
        lenient().when(rooms.findById(roomId)).thenReturn(Optional.of(room));
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(tenantId);
            assertThrows(BadRequestException.class, () -> service.createRental(CreateRentalRequest.builder()
                    .roomId(roomId).startDate(LocalDate.now().plusDays(10)).endDate(LocalDate.now().plusDays(1)).build()));
        }
        verify(rentals, never()).save(any());
    }

    @Test
    void rejectsStartInPast() {
        Room room = Room.builder().landlordId(landlordId).build();
        room.setId(roomId);
        lenient().when(rooms.findById(roomId)).thenReturn(Optional.of(room));
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(tenantId);
            assertThrows(BadRequestException.class, () -> service.createRental(CreateRentalRequest.builder()
                    .roomId(roomId).startDate(LocalDate.now().minusDays(1)).endDate(LocalDate.now().plusMonths(1)).build()));
        }
        verify(rentals, never()).save(any());
    }

    @Test
    void liveQrChecksInOnceAndAwardsTrustOnce() {
        Rental rental = rental();
        Room room = Room.builder().landlordId(landlordId).build();
        room.setId(roomId);
        when(rentals.findById(rental.getId())).thenReturn(Optional.of(rental));
        when(rooms.findById(roomId)).thenReturn(Optional.of(room));
        when(users.findById(tenantId)).thenReturn(Optional.of(User.builder().build()));
        when(users.findById(landlordId)).thenReturn(Optional.of(User.builder().role(UserRole.LANDLORD).build()));
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("rental:checkin:" + rental.getId())).thenReturn("LIVEQR");
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(landlordId);
            service.verifyCheckIn(rental.getId(), VerifyCheckInRequest.builder().checkInCode("LIVEQR").build());
            assertThrows(ConflictException.class, () -> service.verifyCheckIn(rental.getId(),
                    VerifyCheckInRequest.builder().checkInCode("LIVEQR").build()));
        }
        assertEquals(RentalStatus.CHECKED_IN, rental.getStatus());
        assertEquals(RoomStatus.RENTED, room.getStatus());
        assertNotNull(rental.getCheckedInAt());
        verify(scores, times(2)).save(any());
        verify(entityManager, times(2)).refresh(rental, LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void validReservationDoesNotSeedUsableCheckInTokens() {
        Room room = Room.builder().landlordId(landlordId).build();
        room.setId(roomId);
        when(rooms.findById(roomId)).thenReturn(Optional.of(room));
        when(users.findById(tenantId)).thenReturn(Optional.of(User.builder().build()));
        when(users.findById(landlordId)).thenReturn(Optional.of(User.builder().role(UserRole.LANDLORD).build()));
        when(rentals.save(any())).thenAnswer(invocation -> {
            Rental saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(tenantId);
            service.createRental(CreateRentalRequest.builder().roomId(roomId).startDate(LocalDate.now())
                    .endDate(LocalDate.now().plusMonths(1)).build());
        }
        verify(rentals).save(argThat(r -> r.getCheckInCode() == null && r.getCheckInQrToken() == null
                && r.getStatus() == RentalStatus.PENDING_CHECKIN));
        verify(entityManager).refresh(room, LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void contractAmountsRemainOriginalAfterRoomPriceChanges() {
        BigDecimal originalRent = new BigDecimal("2500000.00");
        BigDecimal originalDeposit = new BigDecimal("1500000.00");
        Room room = Room.builder().landlordId(landlordId).price(originalRent).depositAmount(originalDeposit).build();
        room.setId(roomId);
        when(rooms.findById(roomId)).thenReturn(Optional.of(room));
        when(users.findById(tenantId)).thenReturn(Optional.of(User.builder().build()));
        when(users.findById(landlordId)).thenReturn(Optional.of(User.builder().role(UserRole.LANDLORD).build()));
        when(rentals.save(any())).thenAnswer(invocation -> {
            Rental saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(tenantId);
            RentalResponse created = service.createRental(CreateRentalRequest.builder().roomId(roomId).startDate(LocalDate.now())
                    .endDate(LocalDate.now().plusMonths(1)).build());
            org.mockito.ArgumentCaptor<Rental> saved = org.mockito.ArgumentCaptor.forClass(Rental.class);
            verify(rentals).save(saved.capture());
            when(rentals.findById(created.getId())).thenReturn(Optional.of(saved.getValue()));
            room.setPrice(new BigDecimal("3500000.00"));
            room.setDepositAmount(new BigDecimal("2000000.00"));
            RentalResponse detail = service.getRentalDetail(created.getId());
            assertEquals(originalRent, detail.getMonthlyRent());
            assertEquals(originalDeposit, detail.getDepositAmount());
        }
    }

    private Rental rental() {
        return Rental.builder().id(UUID.randomUUID()).roomId(roomId).tenantId(tenantId)
                .landlordId(landlordId).checkInCode("EXPIRED").checkInQrToken("EXPIREDTOKEN").build();
    }
}
