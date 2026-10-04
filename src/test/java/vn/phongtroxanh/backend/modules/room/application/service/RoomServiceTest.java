package vn.phongtroxanh.backend.modules.room.application.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.redis.core.RedisTemplate;
import vn.phongtroxanh.backend.common.exception.BadRequestException;
import vn.phongtroxanh.backend.common.exception.ResourceNotFoundException;
import vn.phongtroxanh.backend.common.location.GeocodingPort;
import vn.phongtroxanh.backend.common.security.SecurityUtils;
import vn.phongtroxanh.backend.common.storage.FileStoragePort;
import vn.phongtroxanh.backend.modules.room.domain.Room;
import vn.phongtroxanh.backend.modules.room.domain.RoomStatus;
import vn.phongtroxanh.backend.modules.room.infrastructure.repository.RoomRepository;
import vn.phongtroxanh.backend.modules.room.infrastructure.repository.SavedRoomRepository;
import vn.phongtroxanh.backend.modules.room.presentation.dto.CreateRoomRequest;
import vn.phongtroxanh.backend.modules.user.domain.UserConsumable;
import vn.phongtroxanh.backend.modules.room.infrastructure.repository.RoomSwipeRepository;
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.UserConsumableRepository;
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.UserRepository;

import java.time.Instant;
import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RoomServiceTest {
    private final UUID userId = UUID.randomUUID();
    private final RoomRepository rooms = mock(RoomRepository.class);
    private final UserConsumableRepository consumables = mock(UserConsumableRepository.class);
    private final GeocodingPort geocoder = mock(GeocodingPort.class);
    private final RoomService service = new RoomService(rooms, mock(RoomSwipeRepository.class), mock(SavedRoomRepository.class),
            mock(UserRepository.class), consumables, mock(FileStoragePort.class), geocoder, mock(RedisTemplate.class));
    private MockedStatic<SecurityUtils> security;

    @BeforeEach void authenticate() {
        security = mockStatic(SecurityUtils.class);
        security.when(SecurityUtils::getCurrentUserId).thenReturn(userId);
    }
    @AfterEach void clearAuthentication() { security.close(); }

    @Test void unavailableGeocoderRequiresCoordinatesInsteadOfInventingLocation() {
        when(geocoder.geocodeAddress(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createRoom(CreateRoomRequest.builder()
                .addressStreet("12 Main Street").district("District 1").build()))
                .isInstanceOf(BadRequestException.class);
        verify(rooms, never()).save(any());
    }

    @Test void expiredBoostIsNotAdvertisedInSearch() {
        Room room = Room.builder().isBoosted(true).boostExpiresAt(Instant.now().minusSeconds(1)).build();
        when(rooms.searchRooms(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(room)));
        assertThat(service.searchRooms(null, null, null, null, null, "boost_first", 0, 20)
                .getContent().getFirst().getIsBoosted()).isFalse();
    }

    @Test void roomBoostLastsSevenDaysAndSpendsCreditAtomically() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.builder().landlordId(userId).build();
        when(rooms.findById(roomId)).thenReturn(Optional.of(room));
        when(consumables.findById(userId)).thenReturn(Optional.of(UserConsumable.builder().boostsLeft(1).build()));
        when(consumables.decrementBoostAtomic(userId)).thenReturn(1);
        Instant before = Instant.now();
        service.boostRoom(roomId);
        assertThat(room.getBoostExpiresAt()).isBetween(before.plus(7, ChronoUnit.DAYS), Instant.now().plus(7, ChronoUnit.DAYS));
        verify(consumables).decrementBoostAtomic(userId);
        verify(consumables, never()).save(any());
    }

    @Test void publicDetailCannotExposeHiddenRoom() {
        UUID roomId = UUID.randomUUID();
        when(rooms.findById(roomId)).thenReturn(Optional.of(Room.builder()
                .landlordId(UUID.randomUUID()).status(RoomStatus.HIDDEN).build()));
        assertThatThrownBy(() -> service.getRoomDetail(roomId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test void cannotBoostUnavailableRoomOrSpendItsCredit() {
        UUID roomId = UUID.randomUUID();
        when(rooms.findById(roomId)).thenReturn(Optional.of(Room.builder()
                .landlordId(userId).status(RoomStatus.HIDDEN).build()));
        when(consumables.findById(userId)).thenReturn(Optional.of(UserConsumable.builder().boostsLeft(1).build()));
        when(consumables.decrementBoostAtomic(userId)).thenReturn(1);
        assertThatThrownBy(() -> service.boostRoom(roomId)).isInstanceOf(BadRequestException.class);
        verify(consumables, never()).decrementBoostAtomic(any());
    }

    @Test void rejectsCoordinatesOutsideMapBounds() {
        assertThatThrownBy(() -> service.getRoomsOnMap(91, 106, 5.0, null, null, null, null))
                .isInstanceOf(BadRequestException.class);
        verify(rooms, never()).findRoomsInRadius(anyDouble(), anyDouble(), anyDouble(), any(), any(), any(), any());
    }

    @Test void rejectsReversedSearchPriceRange() {
        when(rooms.searchRooms(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        assertThatThrownBy(() -> service.searchRooms(null, null, BigDecimal.TEN, BigDecimal.ONE, null, null, 0, 20))
                .isInstanceOf(BadRequestException.class);
    }
}
