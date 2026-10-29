package com.carbonedge.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "system_setting_audits")
public class SystemSettingAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "setting_key", nullable = false, length = 100)
    private String key;

    @Lob
    @Column(name = "old_value", columnDefinition = "text")
    private String oldValue;

    @Lob
    @Column(name = "new_value", columnDefinition = "text")
    private String newValue;

    @Column(name = "is_secret", nullable = false)
    private boolean secret;

    @ManyToOne
    @JoinColumn(name = "changed_by_user_id")
    private UserAccount changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;
}
