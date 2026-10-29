package com.carbonedge.backend.dto;
import com.carbonedge.backend.model.QuizQuestionType; import java.util.List;
public record QuizQuestionRequest(QuizQuestionType type, String prompt, String feedback, Integer points, Integer orderIndex, List<QuizOptionRequest> options) { }
