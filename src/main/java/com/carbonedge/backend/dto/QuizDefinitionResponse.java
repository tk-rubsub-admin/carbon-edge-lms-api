package com.carbonedge.backend.dto;
import java.time.Instant; import java.util.List;
public record QuizDefinitionResponse(
        Long id,
        Long contentItemId,
        Integer passingScore,
        Integer maxAttempts,
        Integer timeLimitSeconds,
        Instant opensAt,
        Instant closesAt,
        Boolean published,
        Boolean randomizeQuestions,
        Boolean randomizeOptions,
        List<QuizQuestionResponse> questions
) { }
