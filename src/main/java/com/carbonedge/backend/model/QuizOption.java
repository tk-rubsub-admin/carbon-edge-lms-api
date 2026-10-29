package com.carbonedge.backend.model;

import jakarta.persistence.*;

@Entity @Table(name = "quiz_options")
public class QuizOption {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "question_id", nullable = false) private QuizQuestion question;
    @Column(nullable = false, length = 2000) private String text;
    @Column(nullable = false) private boolean correct;
    @Column(name = "order_index", nullable = false) private Integer orderIndex;
    public Long getId() { return id; } public QuizQuestion getQuestion() { return question; } public void setQuestion(QuizQuestion value) { question = value; }
    public String getText() { return text; } public void setText(String value) { text = value; }
    public boolean isCorrect() { return correct; } public void setCorrect(boolean value) { correct = value; }
    public Integer getOrderIndex() { return orderIndex; } public void setOrderIndex(Integer value) { orderIndex = value; }
}
