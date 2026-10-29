package com.carbonedge.backend.controller;

import com.carbonedge.backend.dto.QuizDefinitionRequest;
import com.carbonedge.backend.dto.QuizDefinitionResponse;
import com.carbonedge.backend.service.QuizService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/courses/{courseId}/sections/{sectionId}/items/{itemId}/quiz") @RequiredArgsConstructor
public class QuizController {
    private final QuizService quizService;
    @GetMapping public QuizDefinitionResponse get(@RequestHeader("Authorization") String authorization, @PathVariable Long courseId, @PathVariable Long sectionId, @PathVariable Long itemId) { return quizService.getDefinition(authorization, courseId, sectionId, itemId); }
    @PutMapping public QuizDefinitionResponse save(@RequestHeader("Authorization") String authorization, @PathVariable Long courseId, @PathVariable Long sectionId, @PathVariable Long itemId, @RequestBody QuizDefinitionRequest request) { return quizService.saveDefinition(authorization, courseId, sectionId, itemId, request); }
}
