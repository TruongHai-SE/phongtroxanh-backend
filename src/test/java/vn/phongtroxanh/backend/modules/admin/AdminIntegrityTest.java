package vn.phongtroxanh.backend.modules.admin;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.phongtroxanh.backend.modules.admin.application.service.AdminService;
import vn.phongtroxanh.backend.modules.user.domain.*;
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.*;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminIntegrityTest {
    @Mock UserVerificationRepository verifications;
    @Mock UserRepository users;
    @Mock TrustScoreLogRepository logs;
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
}
