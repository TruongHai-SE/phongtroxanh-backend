package vn.phongtroxanh.backend.modules.room.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.phongtroxanh.backend.modules.room.domain.RoomType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomTypeRepository extends JpaRepository<RoomType, UUID> {
    List<RoomType> findAllByIsActiveTrueOrderByDisplayOrderAsc();
    Optional<RoomType> findByCode(String code);
}
