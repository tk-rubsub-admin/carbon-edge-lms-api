package com.carbonedge.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carbonedge.backend.dto.MoodleContentProgressResponse;
import com.carbonedge.backend.model.CourseContentItem;
import com.carbonedge.backend.model.CourseContentProgress;
import com.carbonedge.backend.model.CourseContentType;
import com.carbonedge.backend.model.CourseSection;
import com.carbonedge.backend.model.MoodleUserMapping;
import com.carbonedge.backend.model.UserAccount;
import com.carbonedge.backend.repository.CourseContentItemRepository;
import com.carbonedge.backend.repository.CourseContentProgressRepository;
import com.carbonedge.backend.repository.CourseStudentEnrollmentRepository;
import com.carbonedge.backend.repository.MoodleUserMappingRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MoodleContentProgressServiceTests {

    private CourseContentProgressRepository progressRepository;
    private CourseEnrollmentProgressService enrollmentProgressService;
    private CourseContentItem item;
    private CourseContentProgress progress;
    private MoodleContentProgressService service;

    @BeforeEach
    void setUp() {
        MoodleLaunchService launchService = mock(MoodleLaunchService.class);
        MoodleUserMappingRepository mappingRepository = mock(MoodleUserMappingRepository.class);
        CourseContentItemRepository itemRepository = mock(CourseContentItemRepository.class);
        progressRepository = mock(CourseContentProgressRepository.class);
        CourseStudentEnrollmentRepository studentEnrollmentRepository = mock(CourseStudentEnrollmentRepository.class);
        CourseContentAccessService accessService = mock(CourseContentAccessService.class);
        enrollmentProgressService = mock(CourseEnrollmentProgressService.class);
        service = new MoodleContentProgressService(
                launchService,
                mappingRepository,
                itemRepository,
                progressRepository,
                studentEnrollmentRepository,
                accessService,
                enrollmentProgressService);

        UserAccount user = mock(UserAccount.class);
        when(user.getId()).thenReturn(7L);
        MoodleUserMapping mapping = mock(MoodleUserMapping.class);
        when(mapping.getUser()).thenReturn(user);
        when(mappingRepository.findByMoodleUserId(99L)).thenReturn(Optional.of(mapping));
        when(studentEnrollmentRepository.existsByUserIdAndCourseId(7L, 11L)).thenReturn(true);

        CourseSection section = mock(CourseSection.class);
        when(section.getCourseId()).thenReturn(11L);
        item = mock(CourseContentItem.class);
        when(item.getId()).thenReturn(23L);
        when(item.getSection()).thenReturn(section);
        when(item.getType()).thenReturn(CourseContentType.VIDEO);
        when(item.isVisible()).thenReturn(true);
        when(itemRepository.findById(23L)).thenReturn(Optional.of(item));

        progress = new CourseContentProgress();
        progress.setUser(user);
        progress.setCourseId(11L);
        progress.setContentItem(item);
        when(progressRepository.findByUserIdAndContentItemId(7L, 23L)).thenReturn(Optional.of(progress));
        when(progressRepository.save(progress)).thenReturn(progress);
    }

    @Test
    void updateVideoStoresTheLatestPositionEvenWhenLearnerRewinds() {
        progress.setVideoPositionMs(120_000L);
        progress.setVideoDurationMs(600_000L);

        MoodleContentProgressResponse response = service.updateVideo(
                "secret", 11L, 23L, 99L, 60_500L, 600_000L);

        assertThat(progress.getVideoPositionMs()).isEqualTo(60_500L);
        assertThat(progress.getVideoDurationMs()).isEqualTo(600_000L);
        assertThat(progress.getStartedAt()).isNotNull();
        assertThat(progress.getLastViewedAt()).isNotNull();
        assertThat(response.videoPositionMs()).isEqualTo(60_500L);
        verify(progressRepository).save(progress);
        verify(enrollmentProgressService).markStarted(progress.getUser(), 11L);
    }

    @Test
    void startReturnsPreviouslySavedVideoPositionForResume() {
        progress.setVideoPositionMs(135_250L);
        progress.setVideoDurationMs(600_000L);

        MoodleContentProgressResponse response = service.start("secret", 11L, 23L, 99L);

        assertThat(response.videoPositionMs()).isEqualTo(135_250L);
        assertThat(response.videoDurationMs()).isEqualTo(600_000L);
    }

    @Test
    void updateVideoRejectsPositionAfterDuration() {
        assertThatThrownBy(() -> service.updateVideo(
                "secret", 11L, 23L, 99L, 600_001L, 600_000L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid video progress");
    }

    @Test
    void updateVideoRejectsNonVideoContent() {
        when(item.getType()).thenReturn(CourseContentType.PDF);

        assertThatThrownBy(() -> service.updateVideo(
                "secret", 11L, 23L, 99L, 10_000L, 600_000L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("VIDEO content");
    }
}
