package vn.phongtroxanh.backend.modules.swap.infrastructure.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.phongtroxanh.backend.modules.swap.domain.SwapRequest;
import vn.phongtroxanh.backend.modules.swap.domain.SwapStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface SwapRequestRepository extends JpaRepository<SwapRequest, UUID> {

    List<SwapRequest> findByRequesterIdOrderByCreatedAtDesc(UUID requesterId);

    List<SwapRequest> findByLandlordIdOrderByCreatedAtDesc(UUID landlordId);

    Page<SwapRequest> findByStatusOrderByCreatedAtDesc(SwapStatus status, Pageable pageable);

    Page<SwapRequest> findByStatusAndTargetBudgetMaxLessThanEqualOrderByCreatedAtDesc(SwapStatus status, BigDecimal maxBudget, Pageable pageable);

    Page<SwapRequest> findByTargetBudgetMaxLessThanEqualOrderByCreatedAtDesc(BigDecimal maxBudget, Pageable pageable);

    Page<SwapRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
