package com.carbonedge.backend.repository;

import com.carbonedge.backend.model.SystemSettingAudit;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemSettingAuditRepository extends JpaRepository<SystemSettingAudit, Long> {
    List<SystemSettingAudit> findTop50ByOrderByChangedAtDesc();
}
