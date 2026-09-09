package vn.phongtroxanh.backend.modules.rental.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.phongtroxanh.backend.modules.rental.domain.Rental;
import vn.phongtroxanh.backend.modules.rental.domain.RentalStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RentalRepository extends JpaRepository<Rental, UUID> {

    List<Rental> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<Rental> findByLandlordIdOrderByCreatedAtDesc(UUID landlordId);

    List<Rental> findByRoomIdAndStatus(UUID roomId, RentalStatus status);

    Optional<Rental> findByIdAndCheckInCode(UUID id, String checkInCode);
}
