package com.carbonedge.backend.controller;

import com.carbonedge.backend.service.LearningInsightsService;
import com.carbonedge.backend.dto.CourseResponse;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
public class LearningInsightsController {
    private final LearningInsightsService service;
    @GetMapping("/learning-history") public List<Map<String,Object>> history(@RequestHeader("Authorization") String auth, @RequestParam(required=false) Long courseId) { return service.myTimeline(auth, courseId); }
    @GetMapping("/admin/students/{studentId}/learning-history") public List<Map<String,Object>> studentHistory(@RequestHeader("Authorization") String auth, @PathVariable Long studentId) { return service.adminStudentTimeline(auth, studentId); }
    @GetMapping("/admin/students/{studentId}/courses") public List<CourseResponse> studentCourses(@RequestHeader("Authorization") String auth, @PathVariable Long studentId) { return service.adminStudentCourses(auth, studentId); }
    @GetMapping("/admin/engagement") public Map<String,Object> engagement(@RequestHeader("Authorization") String auth) { return service.adminEngagement(auth); }
}
