package com.carbonedge.backend.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "satisfaction_responses", uniqueConstraints = @UniqueConstraint(name = "uk_satisfaction_response_survey_user", columnNames = {"survey_id", "user_id"}))
public class SatisfactionResponse {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "survey_id", nullable = false) private SatisfactionSurvey survey;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private UserAccount user;
    @Column(name = "survey_title_snapshot", nullable = false, length = 200) private String surveyTitleSnapshot;
    @Column(nullable = false) private Instant submittedAt;
    @PrePersist void onCreate() { if (submittedAt == null) submittedAt = Instant.now(); }
    public Long getId() { return id; }
    public SatisfactionSurvey getSurvey() { return survey; }
    public void setSurvey(SatisfactionSurvey value) { survey = value; }
    public UserAccount getUser() { return user; }
    public void setUser(UserAccount value) { user = value; }
    public String getSurveyTitleSnapshot() { return surveyTitleSnapshot; }
    public void setSurveyTitleSnapshot(String value) { surveyTitleSnapshot = value; }
    public Instant getSubmittedAt() { return submittedAt; }
}
