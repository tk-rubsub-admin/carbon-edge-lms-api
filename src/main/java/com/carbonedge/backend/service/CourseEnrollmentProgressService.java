package com.carbonedge.backend.service;

import com.carbonedge.backend.model.CourseContentItem;
import com.carbonedge.backend.model.CourseContentProgress;
import com.carbonedge.backend.model.CourseStudentEnrollment;
import com.carbonedge.backend.model.UserAccount;
import com.carbonedge.backend.repository.CourseContentItemRepository;
import com.carbonedge.backend.repository.CourseContentProgressRepository;
import com.carbonedge.backend.repository.CourseStudentEnrollmentRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseEnrollmentProgressService {

    private final CourseStudentEnrollmentRepository enrollmentRepository;
    private final CourseContentItemRepository contentItemRepository;
    private final CourseContentProgressRepository contentProgressRepository;

    @Transactional
    public Optional<CourseStudentEnrollment> markStarted(UserAccount user, Long courseId) {
        Optional<CourseStudentEnrollment> enrollment = enrollmentRepository.findByUserIdAndCourseId(user.getId(), courseId);
        enrollment.ifPresent(value -> {
            if (value.getStartedAt() == null) {
                value.setStartedAt(Instant.now());
                enrollmentRepository.save(value);
            }
        });
        return enrollment;
    }

    @Transactional
    public Optional<CourseStudentEnrollment> refreshCompletion(UserAccount user, Long courseId) {
        Optional<CourseStudentEnrollment> optionalEnrollment = enrollmentRepository.findByUserIdAndCourseId(user.getId(), courseId);
        if (optionalEnrollment.isEmpty()) {
            return Optional.empty();
        }

        CourseStudentEnrollment enrollment = optionalEnrollment.get();
        List<CourseContentProgress> progressItems = contentProgressRepository.findAllByUserIdAndCourseId(user.getId(), courseId);
        progressItems.stream()
                .map(CourseContentProgress::getStartedAt)
                .filter(java.util.Objects::nonNull)
                .min(Comparator.naturalOrder())
                .filter(ignored -> enrollment.getStartedAt() == null)
                .ifPresent(enrollment::setStartedAt);

        if (enrollment.getStartedAt() != null && enrollment.getCompletedAt() == null) {
            List<CourseContentItem> requiredItems = contentItemRepository
                    .findAllBySection_CourseIdOrderBySection_OrderIndexAscOrderIndexAscIdAsc(courseId)
                    .stream()
                    .filter(item -> item.isVisible() && item.isRequired())
                    .toList();
            Set<Long> completedItemIds = progressItems.stream()
                    .filter(progress -> progress.getCompletedAt() != null)
                    .map(progress -> progress.getContentItem().getId())
                    .collect(Collectors.toSet());

            if (requiredItems.stream().allMatch(item -> completedItemIds.contains(item.getId()))) {
                Instant completedAt = progressItems.stream()
                        .filter(progress -> progress.getCompletedAt() != null)
                        .map(CourseContentProgress::getCompletedAt)
                        .max(Comparator.naturalOrder())
                        .orElseGet(Instant::now);
                enrollment.setCompletedAt(completedAt);
            }
        }

        return Optional.of(enrollmentRepository.save(enrollment));
    }
}
