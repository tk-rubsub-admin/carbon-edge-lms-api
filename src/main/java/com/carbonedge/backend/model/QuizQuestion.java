package com.carbonedge.backend.model;

import jakarta.persistence.*;

@Entity @Table(name = "quiz_questions")
public class QuizQuestion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "quiz_id", nullable = false) private Quiz quiz;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private QuizQuestionType type;
    @Column(nullable = false, length = 4000) private String prompt;
    @Column(length = 4000) private String feedback;
    @Column(nullable = false) private Integer points;
    @Column(name = "order_index", nullable = false) private Integer orderIndex;
    public Long getId() { return id; } public Quiz getQuiz() { return quiz; } public void setQuiz(Quiz value) { quiz = value; }
    public QuizQuestionType getType() { return type; } public void setType(QuizQuestionType value) { type = value; }
    public String getPrompt() { return prompt; } public void setPrompt(String value) { prompt = value; }
    public String getFeedback() { return feedback; } public void setFeedback(String value) { feedback = value; }
    public Integer getPoints() { return points; } public void setPoints(Integer value) { points = value; }
    public Integer getOrderIndex() { return orderIndex; } public void setOrderIndex(Integer value) { orderIndex = value; }
}
