package com.carbonedge.backend.controller;

import java.time.Instant;
import java.util.Map;

import com.carbonedge.backend.client.moodle.MoodleClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class HealthController {

    private final MoodleClient moodleClient;

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "service", "lms-api",
                "timestamp", Instant.now().toString()
        );
    }

    @GetMapping("/test-moodle")
    public ResponseEntity<String> testMoodle() {
        return ResponseEntity.ok(moodleClient.getSiteInfo());
    }
}
