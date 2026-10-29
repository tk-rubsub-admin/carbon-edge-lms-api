package com.carbonedge.backend.service;

import com.carbonedge.backend.dto.MoodleContentProgressResponse;
import com.carbonedge.backend.exception.ForbiddenException;
import com.carbonedge.backend.exception.NotFoundException;
import com.carbonedge.backend.model.CourseContentItem;
import com.carbonedge.backend.model.CourseContentType;
import com.carbonedge.backend.model.CourseContentProgress;
import com.carbonedge.backend.model.MoodleUserMapping;
import com.carbonedge.backend.model.UserAccount;
import com.carbonedge.backend.model.UserRole;
import com.carbonedge.backend.repository.CourseContentItemRepository;
import com.carbonedge.backend.repository.CourseContentProgressRepository;
import com.carbonedge.backend.repository.CourseStudentEnrollmentRepository;
import com.carbonedge.backend.repository.MoodleUserMappingRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MoodleContentProgressService {
    private final MoodleLaunchService moodleLaunchService;
    private final MoodleUserMappingRepository moodleUserMappingRepository;
    private final CourseContentItemRepository courseContentItemRepository;
    private final CourseContentProgressRepository courseContentProgressRepository;
    private final CourseStudentEnrollmentRepository courseStudentEnrollmentRepository;
    private final CourseContentAccessService courseContentAccessService;
    private final CourseEnrollmentProgressService courseEnrollmentProgressService;

    @Transactional
    public MoodleContentProgressResponse start(String pluginSecret, Long courseId, Long itemId, Long moodleUserId) {
        return updateProgress(pluginSecret, courseId, itemId, moodleUserId, false);
    }

    @Transactional
    public MoodleContentProgressResponse complete(String pluginSecret, Long courseId, Long itemId, Long moodleUserId) {
        return updateProgress(pluginSecret, courseId, itemId, moodleUserId, true);
    }

    @Transactional
    public MoodleContentProgressResponse updatePage(
            String pluginSecret, Long courseId, Long itemId, Long moodleUserId, int currentPage, int totalPages
    ) {
        if (totalPages < 1 || currentPage < 1 || currentPage > totalPages) {
            throw new IllegalArgumentException("Invalid PDF page progress");
        }
        CourseContentProgress progress = loadProgress(pluginSecret, courseId, itemId, moodleUserId);
        int previousPage = progress.getCurrentPage() == null ? 0 : progress.getCurrentPage();
        if (currentPage > previousPage + 1) {
            throw new ForbiddenException("PDF pages must be viewed in order");
        }
        Instant now = Instant.now();
        if (progress.getStartedAt() == null) {
            progress.setStartedAt(now);
        }
        progress.setCurrentPage(Math.max(previousPage, currentPage));
        progress.setTotalPages(totalPages);
        progress.setLastViewedAt(now);
        CourseContentProgress saved = courseContentProgressRepository.save(progress);
        courseEnrollmentProgressService.markStarted(saved.getUser(), courseId);
        return toResponse(saved);
    }

    @Transactional
    public MoodleContentProgressResponse updateVideo(
            String pluginSecret, Long courseId, Long itemId, Long moodleUserId,
            long videoPositionMs, long videoDurationMs
    ) {
        if (videoPositionMs < 0 || videoDurationMs <= 0 || videoPositionMs > videoDurationMs) {
            throw new IllegalArgumentException("Invalid video progress");
        }
        CourseContentProgress progress = loadProgress(pluginSecret, courseId, itemId, moodleUserId);
        if (progress.getContentItem().getType() != CourseContentType.VIDEO) {
            throw new IllegalArgumentException("Video progress is only supported for VIDEO content");
        }
        Instant now = Instant.now();
        if (progress.getStartedAt() == null) {
            progress.setStartedAt(now);
        }
        progress.setVideoPositionMs(videoPositionMs);
        progress.setVideoDurationMs(videoDurationMs);
        progress.setLastViewedAt(now);
        CourseContentProgress saved = courseContentProgressRepository.save(progress);
        courseEnrollmentProgressService.markStarted(saved.getUser(), courseId);
        return toResponse(saved);
    }

    private MoodleContentProgressResponse updateProgress(
            String pluginSecret, Long courseId, Long itemId, Long moodleUserId, boolean complete
    ) {
        CourseContentProgress progress = loadProgress(pluginSecret, courseId, itemId, moodleUserId);
        Instant now = Instant.now();
        if (progress.getStartedAt() == null) {
            progress.setStartedAt(now);
        }
        if (complete && progress.getContentItem().getType() == CourseContentType.PDF
                && (progress.getTotalPages() == null || progress.getCurrentPage() == null
                || !progress.getCurrentPage().equals(progress.getTotalPages()))) {
            throw new ForbiddenException("PDF must be viewed through the final page before completion");
        }
        if (complete && progress.getCompletedAt() == null) {
            progress.setCompletedAt(now);
        }
        CourseContentProgress saved = courseContentProgressRepository.save(progress);
        if (complete) {
            courseEnrollmentProgressService.refreshCompletion(saved.getUser(), courseId);
        } else {
            courseEnrollmentProgressService.markStarted(saved.getUser(), courseId);
        }
        return toResponse(saved);
    }

    private CourseContentProgress loadProgress(String pluginSecret, Long courseId, Long itemId, Long moodleUserId) {
        moodleLaunchService.requirePluginSecret(pluginSecret);
        MoodleUserMapping mapping = moodleUserMappingRepository.findByMoodleUserId(moodleUserId)
                .orElseThrow(() -> new NotFoundException("Moodle user mapping was not found"));
        UserAccount user = mapping.getUser();
        requireCourseAccess(user, courseId);
        CourseContentItem item = courseContentItemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Course content item was not found"));
        if (!item.getSection().getCourseId().equals(courseId)) {
            throw new NotFoundException("Course content item was not found in this course");
        }
        if (!item.isVisible()) {
            throw new ForbiddenException("Course content item is not available");
        }
        courseContentAccessService.requireUnlocked(user, courseId, itemId);
        return courseContentProgressRepository.findByUserIdAndContentItemId(user.getId(), itemId)
                .orElseGet(() -> {
                    CourseContentProgress created = new CourseContentProgress();
                    created.setUser(user);
                    created.setCourseId(courseId);
                    created.setContentItem(item);
                    return created;
                });
    }

    private MoodleContentProgressResponse toResponse(CourseContentProgress progress) {
        return new MoodleContentProgressResponse(
                progress.getCourseId(), progress.getContentItem().getId(), progress.getStartedAt(), progress.getCompletedAt(),
                progress.getCurrentPage(), progress.getTotalPages(),
                progress.getVideoPositionMs(), progress.getVideoDurationMs()
        );
    }

    private void requireCourseAccess(UserAccount user, Long courseId) {
        if (user.getRole() == UserRole.ADMIN
                || courseStudentEnrollmentRepository.existsByUserIdAndCourseId(user.getId(), courseId)) {
            return;
        }
        throw new ForbiddenException("User is not enrolled in this course");
    }
}
