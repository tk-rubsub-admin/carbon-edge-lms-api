package com.carbonedge.backend.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "quiz_attempts")
public class QuizAttempt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "quiz_id", nullable = false) private Quiz quiz;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private UserAccount user;
    @Column(nullable = false) private Integer attemptNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private QuizAttemptStatus status = QuizAttemptStatus.IN_PROGRESS;
    @Column(nullable = false) private Instant startedAt;
    private Instant submittedAt; private Instant expiresAt; private Integer score; private Integer maxScore; private Boolean passed;
    public Long getId() { return id; } public Quiz getQuiz() { return quiz; } public void setQuiz(Quiz value) { quiz = value; } public UserAccount getUser() { return user; } public void setUser(UserAccount value) { user = value; }
    public Integer getAttemptNumber() { return attemptNumber; } public void setAttemptNumber(Integer value) { attemptNumber = value; } public QuizAttemptStatus getStatus() { return status; } public void setStatus(QuizAttemptStatus value) { status = value; }
    public Instant getStartedAt() { return startedAt; } public void setStartedAt(Instant value) { startedAt = value; } public Instant getSubmittedAt() { return submittedAt; } public void setSubmittedAt(Instant value) { submittedAt = value; } public Instant getExpiresAt() { return expiresAt; } public void setExpiresAt(Instant value) { expiresAt = value; }
    public Integer getScore() { return score; } public void setScore(Integer value) { score = value; } public Integer getMaxScore() { return maxScore; } public void setMaxScore(Integer value) { maxScore = value; } public Boolean getPassed() { return passed; } public void setPassed(Boolean value) { passed = value; }
}
