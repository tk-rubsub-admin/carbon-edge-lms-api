package com.carbonedge.backend.model;

import jakarta.persistence.*;

@Entity @Table(name = "quiz_attempt_answers", uniqueConstraints = @UniqueConstraint(columnNames = {"attempt_id", "question_id"}))
public class QuizAttemptAnswer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "attempt_id", nullable = false) private QuizAttempt attempt;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "question_id", nullable = false) private QuizQuestion question;
    @Column(name = "selected_option_ids", length = 1000) private String selectedOptionIds;
    public Long getId() { return id; } public QuizAttempt getAttempt() { return attempt; } public void setAttempt(QuizAttempt value) { attempt = value; } public QuizQuestion getQuestion() { return question; } public void setQuestion(QuizQuestion value) { question = value; } public String getSelectedOptionIds() { return selectedOptionIds; } public void setSelectedOptionIds(String value) { selectedOptionIds = value; }
}
