package com.carbonedge.backend.controller;

import com.carbonedge.backend.dto.MoodleCourseContentResponse;
import com.carbonedge.backend.dto.MoodleContentProgressRequest;
import com.carbonedge.backend.dto.MoodleContentProgressResponse;
import com.carbonedge.backend.service.CourseService;
import com.carbonedge.backend.service.MoodleContentProgressService;
import com.carbonedge.backend.service.MoodleLaunchService;
import com.carbonedge.backend.service.QuizService;
import com.carbonedge.backend.dto.MoodleQuizAttemptRequest;
import java.util.Map;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/moodle/courses")
@RequiredArgsConstructor
public class MoodleCourseContentController {

    private final CourseService courseService;
    private final MoodleContentProgressService moodleContentProgressService;
    private final QuizService quizService;

    @GetMapping("/{courseId}/content")
    public MoodleCourseContentResponse getCourseContent(
            @RequestHeader(MoodleLaunchService.PLUGIN_SECRET_HEADER) String pluginSecretHeader,
            @PathVariable("courseId") Long courseId,
            @RequestParam(required = false) Long moodleUserId
    ) {
        return courseService.getMoodleCourseContent(courseId, pluginSecretHeader, moodleUserId);
    }

    @PostMapping("/{courseId}/items/{itemId}/progress/start")
    public MoodleContentProgressResponse startContentItem(
            @RequestHeader(MoodleLaunchService.PLUGIN_SECRET_HEADER) String pluginSecretHeader,
            @PathVariable Long courseId,
            @PathVariable Long itemId,
            @Valid @RequestBody MoodleContentProgressRequest request
    ) {
        return moodleContentProgressService.start(pluginSecretHeader, courseId, itemId, request.moodleUserId());
    }

    @PostMapping("/{courseId}/items/{itemId}/progress/complete")
    @ResponseStatus(HttpStatus.OK)
    public MoodleContentProgressResponse completeContentItem(
            @RequestHeader(MoodleLaunchService.PLUGIN_SECRET_HEADER) String pluginSecretHeader,
            @PathVariable Long courseId,
            @PathVariable Long itemId,
            @Valid @RequestBody MoodleContentProgressRequest request
    ) {
        return moodleContentProgressService.complete(pluginSecretHeader, courseId, itemId, request.moodleUserId());
    }

    @PostMapping("/{courseId}/items/{itemId}/progress/page")
    public MoodleContentProgressResponse updatePdfPage(
            @RequestHeader(MoodleLaunchService.PLUGIN_SECRET_HEADER) String pluginSecretHeader,
            @PathVariable Long courseId,
            @PathVariable Long itemId,
            @Valid @RequestBody MoodleContentProgressRequest request
    ) {
        if (request.currentPage() == null || request.totalPages() == null) {
            throw new IllegalArgumentException("currentPage and totalPages are required");
        }
        return moodleContentProgressService.updatePage(
                pluginSecretHeader, courseId, itemId, request.moodleUserId(), request.currentPage(), request.totalPages()
        );
    }

    @PostMapping("/{courseId}/items/{itemId}/progress/video")
    public MoodleContentProgressResponse updateVideoProgress(
            @RequestHeader(MoodleLaunchService.PLUGIN_SECRET_HEADER) String pluginSecretHeader,
            @PathVariable Long courseId,
            @PathVariable Long itemId,
            @Valid @RequestBody MoodleContentProgressRequest request
    ) {
        if (request.videoPositionMs() == null || request.videoDurationMs() == null) {
            throw new IllegalArgumentException("videoPositionMs and videoDurationMs are required");
        }
        return moodleContentProgressService.updateVideo(
                pluginSecretHeader, courseId, itemId, request.moodleUserId(),
                request.videoPositionMs(), request.videoDurationMs()
        );
    }

    @GetMapping("/{courseId}/items/{itemId}/quiz")
    public Map<String, Object> getQuiz(@RequestHeader(MoodleLaunchService.PLUGIN_SECRET_HEADER) String secret, @PathVariable Long courseId, @PathVariable Long itemId, @RequestParam Long moodleUserId, @RequestParam(required = false) Long attemptId) { return quizService.learnerDefinition(secret, courseId, itemId, moodleUserId, attemptId); }
    @PostMapping("/{courseId}/items/{itemId}/quiz/attempts")
    public Map<String, Object> startQuizAttempt(@RequestHeader(MoodleLaunchService.PLUGIN_SECRET_HEADER) String secret, @PathVariable Long courseId, @PathVariable Long itemId, @Valid @RequestBody MoodleQuizAttemptRequest request) { return quizService.startAttempt(secret, courseId, itemId, request.moodleUserId()); }
    @PostMapping("/{courseId}/items/{itemId}/quiz/attempts/{attemptId}/submit")
    public Map<String, Object> submitQuizAttempt(@RequestHeader(MoodleLaunchService.PLUGIN_SECRET_HEADER) String secret, @PathVariable Long courseId, @PathVariable Long itemId, @PathVariable Long attemptId, @Valid @RequestBody MoodleQuizAttemptRequest request) { return quizService.submitAttempt(secret, courseId, itemId, attemptId, request); }
}
