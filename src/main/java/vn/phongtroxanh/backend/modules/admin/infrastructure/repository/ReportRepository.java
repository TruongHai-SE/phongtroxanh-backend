package vn.phongtroxanh.backend.modules.admin.infrastructure.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.phongtroxanh.backend.modules.admin.domain.Report;
import vn.phongtroxanh.backend.modules.admin.domain.ReportSeverity;
import vn.phongtroxanh.backend.modules.admin.domain.ReportStatus;

import java.util.UUID;

@Repository
public interface ReportRepository extends JpaRepository<Report, UUID> {

    Page<Report> findByStatusAndSeverityOrderByCreatedAtDesc(ReportStatus status, ReportSeverity severity, Pageable pageable);

    Page<Report> findByStatusOrderByCreatedAtDesc(ReportStatus status, Pageable pageable);

    Page<Report> findBySeverityOrderByCreatedAtDesc(ReportSeverity severity, Pageable pageable);

    Page<Report> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
