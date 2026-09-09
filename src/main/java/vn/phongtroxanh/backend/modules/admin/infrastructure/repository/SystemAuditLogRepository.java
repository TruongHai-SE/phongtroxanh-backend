package vn.phongtroxanh.backend.modules.admin.infrastructure.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.phongtroxanh.backend.modules.admin.domain.SystemAuditLog;

import java.util.UUID;

@Repository
public interface SystemAuditLogRepository extends JpaRepository<SystemAuditLog, UUID> {

    Page<SystemAuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
