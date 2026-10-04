package vn.phongtroxanh.backend.modules.monetization.infrastructure.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import vn.phongtroxanh.backend.modules.monetization.domain.PaymentTransaction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    Optional<PaymentTransaction> findByGatewayOrderId(String gatewayOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentTransaction p where p.gatewayOrderId = :orderId")
    Optional<PaymentTransaction> findLockedByOrderId(@Param("orderId") String orderId);

    Optional<PaymentTransaction> findByIdempotencyKey(String idempotencyKey);

    default Optional<PaymentTransaction> findByTransactionCode(String transactionCode) {
        return findByGatewayOrderId(transactionCode);
    }

    List<PaymentTransaction> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Page<PaymentTransaction> findAllByOrderByCreatedAtDesc(Pageable pageable);

    boolean existsByIdempotencyKey(String idempotencyKey);
}
