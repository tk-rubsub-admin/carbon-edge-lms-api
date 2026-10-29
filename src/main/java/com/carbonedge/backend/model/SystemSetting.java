package com.carbonedge.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "system_settings")
public class SystemSetting {
    @Id
    @Column(name = "setting_key", length = 100)
    private String key;

    @Lob
    @Column(name = "setting_value", nullable = false, columnDefinition = "text")
    private String value;

    @Column(name = "is_secret", nullable = false)
    private boolean secret;

    @ManyToOne
    @JoinColumn(name = "updated_by_user_id")
    private UserAccount updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
