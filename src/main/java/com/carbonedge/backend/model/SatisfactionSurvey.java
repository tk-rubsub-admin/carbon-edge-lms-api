package com.carbonedge.backend.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "satisfaction_surveys", uniqueConstraints =
        @UniqueConstraint(name = "uk_satisfaction_survey_default_course", columnNames = "default_for_course_id"))
public class SatisfactionSurvey {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "course_id", nullable = false) private Long courseId;
    @Column(name = "default_for_course_id") private Long defaultForCourseId;
    @Column(nullable = false, length = 200) private String title;
    @Column(length = 4000) private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private SatisfactionSurveyStatus status = SatisfactionSurveyStatus.DRAFT;
    private Instant opensAt;
    private Instant closesAt;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @PrePersist void onCreate() { Instant now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
    public Long getId() { return id; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long value) { courseId = value; }
    public Long getDefaultForCourseId() { return defaultForCourseId; }
    public void setDefaultForCourseId(Long value) { defaultForCourseId = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { title = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
    public SatisfactionSurveyStatus getStatus() { return status; }
    public void setStatus(SatisfactionSurveyStatus value) { status = value; }
    public Instant getOpensAt() { return opensAt; }
    public void setOpensAt(Instant value) { opensAt = value; }
    public Instant getClosesAt() { return closesAt; }
    public void setClosesAt(Instant value) { closesAt = value; }
}
