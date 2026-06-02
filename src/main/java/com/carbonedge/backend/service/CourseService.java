package com.carbonedge.backend.service;

import com.carbonedge.backend.client.moodle.MoodleClient;
import com.carbonedge.backend.dto.CourseLaunchResponse;
import com.carbonedge.backend.dto.CourseResponse;
import com.carbonedge.backend.exception.NotFoundException;
import com.carbonedge.backend.exception.UnauthorizedException;
import com.carbonedge.backend.model.AuthSession;
import com.carbonedge.backend.model.MoodleUserMapping;
import com.carbonedge.backend.repository.AuthSessionRepository;
import com.carbonedge.backend.repository.MoodleUserMappingRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseService {

    private final AuthSessionRepository authSessionRepository;
    private final MoodleUserMappingRepository moodleUserMappingRepository;
    private final MoodleClient moodleClient;
    private final MoodleLaunchService moodleLaunchService;

    public CourseService(
            AuthSessionRepository authSessionRepository,
            MoodleUserMappingRepository moodleUserMappingRepository,
            MoodleClient moodleClient,
            MoodleLaunchService moodleLaunchService
    ) {
        this.authSessionRepository = authSessionRepository;
        this.moodleUserMappingRepository = moodleUserMappingRepository;
        this.moodleClient = moodleClient;
        this.moodleLaunchService = moodleLaunchService;
    }

    @Transactional(readOnly = true)
    public List<CourseResponse> getMyCourses(String authorizationHeader) {
        AuthSession session = resolveSession(authorizationHeader);
        return getMyCourses(session);
    }

    @Transactional(readOnly = true)
    public CourseResponse getMyCourseById(String authorizationHeader, Long courseId) {
        AuthSession session = resolveSession(authorizationHeader);
        return getMyCourses(session)
                .stream()
                .filter(course -> course.id().equals(courseId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Course not found for current user"));
    }

    @Transactional(readOnly = true)
    public CourseLaunchResponse getMyCourseLaunch(String authorizationHeader, Long courseId) {
        AuthSession session = resolveSession(authorizationHeader);
        CourseResponse course = getMyCourseById(authorizationHeader, courseId);
        MoodleUserMapping mapping = moodleUserMappingRepository.findByUser(session.getUser())
                .orElseThrow(() -> new UnauthorizedException("User has no Moodle mapping"));
        return moodleLaunchService.issueLaunchUrl(
                session.getUser(),
                mapping.getMoodleUserId(),
                course.id(),
                "/course/view.php?id=" + course.id()
        );
    }

    private AuthSession resolveSession(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        AuthSession session = authSessionRepository.findByAccessToken(token)
                .orElseThrow(() -> new UnauthorizedException("Invalid access token"));
        if (session.getRevokedAt() != null || !session.getExpiresAt().isAfter(Instant.now())) {
            throw new UnauthorizedException("Session has expired");
        }
        return session;
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Missing bearer token");
        }
        return authorizationHeader.substring(7).trim();
    }

    private List<CourseResponse> getMyCourses(AuthSession session) {
        MoodleUserMapping mapping = moodleUserMappingRepository.findByUser(session.getUser())
                .orElseThrow(() -> new UnauthorizedException("User has no Moodle mapping"));

        return moodleClient.getUserCourses(mapping.getMoodleUserId())
                .stream()
                .map(CourseResponse::from)
                .toList();
    }
}
