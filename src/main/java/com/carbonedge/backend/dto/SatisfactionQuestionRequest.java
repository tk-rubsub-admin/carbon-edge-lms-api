package com.carbonedge.backend.dto;

import com.carbonedge.backend.model.SatisfactionQuestionType;
import java.util.List;

public record SatisfactionQuestionRequest(Long id, SatisfactionQuestionType type, String prompt,
        List<String> options, Boolean required, Integer orderIndex) { }
