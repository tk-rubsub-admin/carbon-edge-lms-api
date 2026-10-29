package com.carbonedge.backend.model;

import jakarta.persistence.*;

@Entity
@Table(name = "satisfaction_answers")
public class SatisfactionAnswer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "response_id", nullable = false) private SatisfactionResponse response;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "question_id") private SatisfactionQuestion question;
    @Column(name = "question_prompt_snapshot", nullable = false, length = 2000) private String questionPromptSnapshot;
    @Enumerated(EnumType.STRING) @Column(name = "question_type_snapshot", nullable = false, length = 30) private SatisfactionQuestionType questionTypeSnapshot;
    @Column(name = "answer_text", columnDefinition = "TEXT") private String answerText;
    public Long getId() { return id; }
    public SatisfactionResponse getResponse() { return response; }
    public void setResponse(SatisfactionResponse value) { response = value; }
    public SatisfactionQuestion getQuestion() { return question; }
    public void setQuestion(SatisfactionQuestion value) { question = value; }
    public String getQuestionPromptSnapshot() { return questionPromptSnapshot; }
    public void setQuestionPromptSnapshot(String value) { questionPromptSnapshot = value; }
    public SatisfactionQuestionType getQuestionTypeSnapshot() { return questionTypeSnapshot; }
    public void setQuestionTypeSnapshot(SatisfactionQuestionType value) { questionTypeSnapshot = value; }
    public String getAnswerText() { return answerText; }
    public void setAnswerText(String value) { answerText = value; }
}
