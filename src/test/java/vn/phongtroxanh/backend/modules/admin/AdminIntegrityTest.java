package vn.phongtroxanh.backend.modules.admin;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import vn.phongtroxanh.backend.modules.admin.application.service.AdminService;
import vn.phongtroxanh.backend.modules.monetization.domain.PaymentMethod;
import vn.phongtroxanh.backend.modules.monetization.domain.PaymentStatus;
import vn.phongtroxanh.backend.modules.monetization.domain.PaymentTransaction;
import vn.phongtroxanh.backend.modules.monetization.infrastructure.repository.PaymentTransactionRepository;
import vn.phongtroxanh.backend.modules.user.domain.*;
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminIntegrityTest {
    @Mock UserVerificationRepository verifications;
    @Mock UserRepository users;
    @Mock TrustScoreLogRepository logs;
    @Mock PaymentTransactionRepository paymentTransactions;
    @Mock EntityManager entityManager;
    @InjectMocks AdminService service;

    @Test void approvedKycCannotBeApprovedTwice() {
        var id = UUID.randomUUID();
        var verification = UserVerification.builder().userId(UUID.randomUUID()).status(VerificationStatus.APPROVED).build();
        when(verifications.findById(id)).thenReturn(Optional.of(verification));
        assertThrows(RuntimeException.class, () -> service.approveKyc(id));
        verifyNoInteractions(users,logs);
        verify(verifications,never()).save(any());
    }

    @Test void transactionsNormalizeRawPlanCodeToCanonicalVietnameseName() {
        var tx = PaymentTransaction.builder()
                .userId(UUID.randomUUID())
                .itemName("PRO_TENANT")
                .amount(BigDecimal.valueOf(29000))
                .paymentMethod(PaymentMethod.PAYOS)
                .status(PaymentStatus.SUCCESS)
                .build();
        when(paymentTransactions.findAllByOrderByCreatedAtDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(tx)));

        var page = service.getTransactions(0, 10);
        assertEquals("Gói Pro Tìm Bạn & Thuê Phòng", page.getContent().get(0).getItemName());
    }
}
