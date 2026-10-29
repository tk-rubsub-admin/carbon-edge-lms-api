package com.carbonedge.backend.service;

import com.carbonedge.backend.exception.ForbiddenException;
import com.carbonedge.backend.exception.NotFoundException;
import com.carbonedge.backend.model.*;
import com.carbonedge.backend.repository.*;
import com.carbonedge.backend.client.moodle.MoodleClient;
import com.carbonedge.backend.dto.CourseResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-models for learner history and the administrator engagement dashboard. */
@Service @RequiredArgsConstructor
public class LearningInsightsService {
    private final AuthService authService;
    private final UserAccountRepository userRepository;
    private final CourseStudentEnrollmentRepository enrollmentRepository;
    private final CourseContentProgressRepository progressRepository;
    private final QuizAttemptRepository attemptRepository;
    private final MoodleUserMappingRepository moodleUserMappingRepository;
    private final MoodleClient moodleClient;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> myTimeline(String authorization, Long courseId) {
        UserAccount user = current(authorization);
        return timelineForUser(user.getId(), courseId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> adminStudentTimeline(String authorization, Long studentId) {
        UserAccount admin = current(authorization);
        requireAdmin(admin);
        UserAccount student = userRepository.findById(studentId).orElseThrow(() -> new NotFoundException("Student not found"));
        if (student.getRole() != UserRole.STUDENT) throw new NotFoundException("Student not found");
        return timelineForUser(student.getId(), null);
    }

    @Transactional(readOnly = true)
    public List<CourseResponse> adminStudentCourses(String authorization, Long studentId) {
        UserAccount admin = current(authorization);
        requireAdmin(admin);
        UserAccount student = userRepository.findById(studentId).orElseThrow(() -> new NotFoundException("Student not found"));
        if (student.getRole() != UserRole.STUDENT) throw new NotFoundException("Student not found");
        return moodleUserMappingRepository.findByUser(student)
                .map(mapping -> moodleClient.getUserCourses(mapping.getMoodleUserId()))
                .orElseGet(List::of)
                .stream()
                .map(CourseResponse::from)
                .map(course -> enrollmentRepository.findByUserIdAndCourseId(student.getId(), course.id())
                        .map(course::withEnrollment)
                        .orElse(course))
                .toList();
    }

    private List<Map<String, Object>> timelineForUser(Long userId, Long courseId) {
        List<Map<String, Object>> events = new ArrayList<>();
        progressRepository.findAllByUserId(userId).stream()
                .filter(p -> courseId == null || courseId.equals(p.getCourseId()))
                .forEach(p -> {
                    add(events, "CONTENT_STARTED", p.getStartedAt(), p.getCourseId(), p.getContentItem().getId(), p.getContentItem().getTitle());
                    add(events, "CONTENT_VIEWED", p.getLastViewedAt(), p.getCourseId(), p.getContentItem().getId(), p.getContentItem().getTitle());
                    add(events, "CONTENT_COMPLETED", p.getCompletedAt(), p.getCourseId(), p.getContentItem().getId(), p.getContentItem().getTitle());
                });
        attemptRepository.findAllByUserId(userId).stream()
                .filter(a -> courseId == null || courseId.equals(a.getQuiz().getContentItem().getSection().getCourseId()))
                .forEach(a -> {
                    if (a.getSubmittedAt() == null) return;
                    Map<String, Object> event = new LinkedHashMap<>();
                    event.put("type", "QUIZ_SUBMITTED");
                    event.put("occurredAt", a.getSubmittedAt());
                    event.put("courseId", a.getQuiz().getContentItem().getSection().getCourseId());
                    event.put("contentItemId", a.getQuiz().getContentItem().getId());
                    event.put("title", a.getQuiz().getContentItem().getTitle());
                    event.put("score", a.getScore());
                    event.put("maxScore", a.getMaxScore());
                    event.put("passed", a.getPassed());
                    event.put("attemptNumber", a.getAttemptNumber());
                    events.add(event);
                });
        return events.stream().sorted(Comparator.comparing(e -> (Instant)e.get("occurredAt"), Comparator.reverseOrder())).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> adminEngagement(String authorization) {
        UserAccount admin = current(authorization);
        if (admin.getRole() != UserRole.ADMIN) throw new ForbiddenException("Admin access is required");
        List<CourseStudentEnrollment> enrollments = enrollmentRepository.findAll();
        List<Map<String, Object>> risks = new ArrayList<>();
        long completed = 0;
//        for (CourseStudentEnrollment enrollment : enrollments) {
//            List<CourseContentProgress> progress = progressRepository.findAllByUserIdAndCourseId(enrollment.getUser().getId(), enrollment.getCourseId());
//            long done = progress.stream().filter(p -> p.getCompletedAt() != null).count();
//            Instant last = progress.stream().flatMap(p -> Stream.of(p.getLastViewedAt(), p.getCompletedAt(), p.getStartedAt())).filter(Objects::nonNull).max(Instant::compareTo).orElse(null);
//            if (done > 0) completed++;
//            if (last == null || Duration.between(last, Instant.now()).toDays() >= 14) {
//                risks.add(Map.of("userId", enrollment.getUser().getId(), "username", enrollment.getUser().getUsername(), "courseId", enrollment.getCourseId(), "reason", last == null ? "NO_LEARNING_ACTIVITY" : "INACTIVE_14_DAYS", "lastActivityAt", last));
//            }
//        }
//        Map<String, Object> result = new LinkedHashMap<>();
//        result.put("totalUsers", userRepository.count()); result.put("totalEnrollments", enrollments.size());
//        result.put("engagedEnrollments", completed); result.put("atRiskEnrollments", risks.size()); result.put("atRiskUsers", risks);
        return null;
    }

    private UserAccount current(String authorization) { return userRepository.findById(authService.currentUser(authorization).id()).orElseThrow(); }
    private void requireAdmin(UserAccount user) { if (user.getRole() != UserRole.ADMIN) throw new ForbiddenException("Admin access is required"); }
    private void add(List<Map<String, Object>> events, String type, Instant at, Long courseId, Long itemId, String title) { if (at != null) events.add(Map.of("type", type, "occurredAt", at, "courseId", courseId, "contentItemId", itemId, "title", title)); }
}
