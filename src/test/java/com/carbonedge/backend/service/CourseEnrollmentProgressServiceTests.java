package com.carbonedge.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carbonedge.backend.model.CourseContentItem;
import com.carbonedge.backend.model.CourseContentProgress;
import com.carbonedge.backend.model.CourseEnrollmentStatus;
import com.carbonedge.backend.model.CourseStudentEnrollment;
import com.carbonedge.backend.model.UserAccount;
import com.carbonedge.backend.repository.CourseContentItemRepository;
import com.carbonedge.backend.repository.CourseContentProgressRepository;
import com.carbonedge.backend.repository.CourseStudentEnrollmentRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CourseEnrollmentProgressServiceTests {

    private CourseStudentEnrollmentRepository enrollmentRepository;
    private CourseContentItemRepository contentItemRepository;
    private CourseContentProgressRepository contentProgressRepository;
    private CourseEnrollmentProgressService service;

    @BeforeEach
    void setUp() {
        enrollmentRepository = mock(CourseStudentEnrollmentRepository.class);
        contentItemRepository = mock(CourseContentItemRepository.class);
        contentProgressRepository = mock(CourseContentProgressRepository.class);
        service = new CourseEnrollmentProgressService(
                enrollmentRepository,
                contentItemRepository,
                contentProgressRepository);
    }

    @Test
    void markStartedChangesEnrollmentToInProgress() {
        UserAccount user = mock(UserAccount.class);
        when(user.getId()).thenReturn(7L);
        CourseStudentEnrollment enrollment = enrollment(user, 11L);
        when(enrollmentRepository.findByUserIdAndCourseId(7L, 11L)).thenReturn(Optional.of(enrollment));

        Optional<CourseStudentEnrollment> result = service.markStarted(user, 11L);

        assertThat(result).contains(enrollment);
        assertThat(enrollment.getStartedAt()).isNotNull();
        assertThat(enrollment.getStatus()).isEqualTo(CourseEnrollmentStatus.IN_PROGRESS);
        verify(enrollmentRepository).save(enrollment);
    }

    @Test
    void refreshCompletionCompletesCourseAfterAllRequiredItemsAreComplete() {
        UserAccount user = mock(UserAccount.class);
        when(user.getId()).thenReturn(7L);
        CourseStudentEnrollment enrollment = enrollment(user, 11L);
        enrollment.setStartedAt(Instant.parse("2026-09-29T01:00:00Z"));

        CourseContentItem requiredItem = mock(CourseContentItem.class);
        when(requiredItem.getId()).thenReturn(23L);
        when(requiredItem.isVisible()).thenReturn(true);
        when(requiredItem.isRequired()).thenReturn(true);

        CourseContentProgress progress = new CourseContentProgress();
        progress.setUser(user);
        progress.setCourseId(11L);
        progress.setContentItem(requiredItem);
        progress.setStartedAt(Instant.parse("2026-09-29T01:05:00Z"));
        progress.setCompletedAt(Instant.parse("2026-09-29T01:30:00Z"));

        when(enrollmentRepository.findByUserIdAndCourseId(7L, 11L)).thenReturn(Optional.of(enrollment));
        when(contentProgressRepository.findAllByUserIdAndCourseId(7L, 11L)).thenReturn(List.of(progress));
        when(contentItemRepository.findAllBySection_CourseIdOrderBySection_OrderIndexAscOrderIndexAscIdAsc(11L))
                .thenReturn(List.of(requiredItem));
        when(enrollmentRepository.save(enrollment)).thenReturn(enrollment);

        Optional<CourseStudentEnrollment> result = service.refreshCompletion(user, 11L);

        assertThat(result).contains(enrollment);
        assertThat(enrollment.getCompletedAt()).isEqualTo(Instant.parse("2026-09-29T01:30:00Z"));
        assertThat(enrollment.getStatus()).isEqualTo(CourseEnrollmentStatus.COMPLETED);
    }

    @Test
    void refreshCompletionKeepsCourseInProgressWhenARequiredItemIsIncomplete() {
        UserAccount user = mock(UserAccount.class);
        when(user.getId()).thenReturn(7L);
        CourseStudentEnrollment enrollment = enrollment(user, 11L);
        enrollment.setStartedAt(Instant.parse("2026-09-29T01:00:00Z"));

        CourseContentItem requiredItem = mock(CourseContentItem.class);
        when(requiredItem.getId()).thenReturn(23L);
        when(requiredItem.isVisible()).thenReturn(true);
        when(requiredItem.isRequired()).thenReturn(true);

        when(enrollmentRepository.findByUserIdAndCourseId(7L, 11L)).thenReturn(Optional.of(enrollment));
        when(contentProgressRepository.findAllByUserIdAndCourseId(7L, 11L)).thenReturn(List.of());
        when(contentItemRepository.findAllBySection_CourseIdOrderBySection_OrderIndexAscOrderIndexAscIdAsc(11L))
                .thenReturn(List.of(requiredItem));
        when(enrollmentRepository.save(enrollment)).thenReturn(enrollment);

        service.refreshCompletion(user, 11L);

        assertThat(enrollment.getCompletedAt()).isNull();
        assertThat(enrollment.getStatus()).isEqualTo(CourseEnrollmentStatus.IN_PROGRESS);
    }

    private CourseStudentEnrollment enrollment(UserAccount user, Long courseId) {
        CourseStudentEnrollment enrollment = new CourseStudentEnrollment();
        enrollment.setUser(user);
        enrollment.setCourseId(courseId);
        return enrollment;
    }
}
