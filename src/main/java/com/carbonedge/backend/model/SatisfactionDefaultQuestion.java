package com.carbonedge.backend.model;

import jakarta.persistence.*;

@Entity
@Table(name = "satisfaction_default_questions")
public class SatisfactionDefaultQuestion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "order_index", nullable = false, unique = true) private Integer orderIndex;
    @Column(nullable = false, length = 2000) private String prompt;

    public Long getId() { return id; }
    public Integer getOrderIndex() { return orderIndex; }
    public void setOrderIndex(Integer value) { orderIndex = value; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String value) { prompt = value; }
}
