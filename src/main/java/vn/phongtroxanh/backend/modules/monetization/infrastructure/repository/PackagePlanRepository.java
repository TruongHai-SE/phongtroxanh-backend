package vn.phongtroxanh.backend.modules.monetization.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.phongtroxanh.backend.modules.monetization.domain.PackagePlan;

@Repository
public interface PackagePlanRepository extends JpaRepository<PackagePlan, String> {
}
