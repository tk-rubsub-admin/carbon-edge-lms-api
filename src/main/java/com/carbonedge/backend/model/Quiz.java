package com.carbonedge.backend.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "quizzes")
public class Quiz {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "content_item_id", nullable = false, unique = true) private CourseContentItem contentItem;
    @Column(nullable = false) private Integer passingScore = 0;
    private Integer maxAttempts;
    private Integer timeLimitSeconds;
    private Instant opensAt;
    private Instant closesAt;
    @Column(nullable = false) private boolean published = false;
    @Column(nullable = false) private boolean randomizeQuestions = false;
    @Column(nullable = false) private boolean randomizeOptions = false;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @PrePersist void onCreate() { Instant now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
    public Long getId() { return id; } public CourseContentItem getContentItem() { return contentItem; } public void setContentItem(CourseContentItem value) { contentItem = value; }
    public Integer getPassingScore() { return passingScore; } public void setPassingScore(Integer value) { passingScore = value; }
    public Integer getMaxAttempts() { return maxAttempts; } public void setMaxAttempts(Integer value) { maxAttempts = value; }
    public Integer getTimeLimitSeconds() { return timeLimitSeconds; } public void setTimeLimitSeconds(Integer value) { timeLimitSeconds = value; }
    public Instant getOpensAt() { return opensAt; } public void setOpensAt(Instant value) { opensAt = value; }
    public Instant getClosesAt() { return closesAt; } public void setClosesAt(Instant value) { closesAt = value; }
    public boolean isPublished() { return published; } public void setPublished(boolean value) { published = value; }
    public boolean isRandomizeQuestions() { return randomizeQuestions; } public void setRandomizeQuestions(boolean value) { randomizeQuestions = value; }
    public boolean isRandomizeOptions() { return randomizeOptions; } public void setRandomizeOptions(boolean value) { randomizeOptions = value; }
}
