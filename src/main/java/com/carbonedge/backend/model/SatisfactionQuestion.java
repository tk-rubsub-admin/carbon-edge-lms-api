package com.carbonedge.backend.model;

import jakarta.persistence.*;

@Entity
@Table(name = "satisfaction_questions")
public class SatisfactionQuestion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "survey_id", nullable = false) private SatisfactionSurvey survey;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private SatisfactionQuestionType type;
    @Column(nullable = false, length = 2000) private String prompt;
    @Column(name = "options_json", length = 4000) private String optionsJson;
    @Column(name = "is_required", nullable = false) private boolean required = true;
    @Column(name = "order_index", nullable = false) private Integer orderIndex;
    @Column(nullable = false) private boolean active = true;
    public Long getId() { return id; }
    public SatisfactionSurvey getSurvey() { return survey; }
    public void setSurvey(SatisfactionSurvey value) { survey = value; }
    public SatisfactionQuestionType getType() { return type; }
    public void setType(SatisfactionQuestionType value) { type = value; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String value) { prompt = value; }
    public String getOptionsJson() { return optionsJson; }
    public void setOptionsJson(String value) { optionsJson = value; }
    public boolean isRequired() { return required; }
    public void setRequired(boolean value) { required = value; }
    public Integer getOrderIndex() { return orderIndex; }
    public void setOrderIndex(Integer value) { orderIndex = value; }
    public boolean isActive() { return active; }
    public void setActive(boolean value) { active = value; }
}
