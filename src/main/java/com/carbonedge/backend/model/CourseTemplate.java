package com.carbonedge.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "course_templates")
public class CourseTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "template_type", nullable = false, length = 40)
    private CourseTemplateType templateType;

    @Column(name = "source_course_id")
    private Long sourceCourseId;

    @Column(name = "category_id_default")
    private Integer categoryIdDefault;

    @Column(name = "visible_default", nullable = false)
    private boolean visibleDefault = true;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "definition_json", nullable = false, columnDefinition = "CLOB")
    private String definitionJson;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public CourseTemplateType getTemplateType() {
        return templateType;
    }

    public void setTemplateType(CourseTemplateType templateType) {
        this.templateType = templateType;
    }

    public Long getSourceCourseId() {
        return sourceCourseId;
    }

    public void setSourceCourseId(Long sourceCourseId) {
        this.sourceCourseId = sourceCourseId;
    }

    public Integer getCategoryIdDefault() {
        return categoryIdDefault;
    }

    public void setCategoryIdDefault(Integer categoryIdDefault) {
        this.categoryIdDefault = categoryIdDefault;
    }

    public boolean isVisibleDefault() {
        return visibleDefault;
    }

    public void setVisibleDefault(boolean visibleDefault) {
        this.visibleDefault = visibleDefault;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getDefinitionJson() {
        return definitionJson;
    }

    public void setDefinitionJson(String definitionJson) {
        this.definitionJson = definitionJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
