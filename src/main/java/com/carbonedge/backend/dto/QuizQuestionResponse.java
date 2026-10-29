package com.carbonedge.backend.dto;
import java.util.List;
public record QuizQuestionResponse(Long id, String type, String prompt, String feedback, Integer points, Integer orderIndex, List<QuizOptionResponse> options) { }
