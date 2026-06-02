package com.carbonedge.backend.controller;

import com.carbonedge.backend.dto.CourseLaunchConsumeRequest;
import com.carbonedge.backend.dto.CourseLaunchConsumeResponse;
import com.carbonedge.backend.service.MoodleLaunchService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/moodle/launch")
public class MoodleLaunchController {

    private final MoodleLaunchService moodleLaunchService;

    public MoodleLaunchController(MoodleLaunchService moodleLaunchService) {
        this.moodleLaunchService = moodleLaunchService;
    }

    @PostMapping("/consume")
    public CourseLaunchConsumeResponse consume(
            @RequestHeader(MoodleLaunchService.PLUGIN_SECRET_HEADER) String pluginSecretHeader,
            @Valid @RequestBody CourseLaunchConsumeRequest request
    ) {
        return moodleLaunchService.consume(request.token(), pluginSecretHeader);
    }
}
