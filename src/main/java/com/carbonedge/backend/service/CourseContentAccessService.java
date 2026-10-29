package com.carbonedge.backend.service;

import com.carbonedge.backend.exception.ForbiddenException;
import com.carbonedge.backend.model.CourseContentItem;
import com.carbonedge.backend.model.CourseContentProgress;
import com.carbonedge.backend.model.UserAccount;
import com.carbonedge.backend.model.UserRole;
import com.carbonedge.backend.repository.CourseContentItemRepository;
import com.carbonedge.backend.repository.CourseContentProgressRepository;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseContentAccessService {
    private final CourseContentItemRepository itemRepository;
    private final CourseContentProgressRepository progressRepository;

    @Transactional(readOnly = true)
    public boolean isLocked(UserAccount user, Long courseId, Long targetItemId) {
        if (canBypassSequence(user)) {
            return false;
        }
        Set<Long> completedItemIds = progressRepository.findAllByUserIdAndCourseId(user.getId(), courseId).stream()
                .filter(progress -> progress.getCompletedAt() != null)
                .map(CourseContentProgress::getContentItem)
                .map(CourseContentItem::getId)
                .collect(Collectors.toSet());

        for (CourseContentItem item : itemRepository.findAllBySection_CourseIdOrderBySection_OrderIndexAscOrderIndexAscIdAsc(courseId)) {
            if (!item.isVisible()) {
                continue;
            }
            if (item.getId().equals(targetItemId)) {
                return false;
            }
            if (!completedItemIds.contains(item.getId())) {
                return true;
            }
        }
        return false;
    }

    public void requireUnlocked(UserAccount user, Long courseId, Long itemId) {
        if (isLocked(user, courseId, itemId)) {
            throw new ForbiddenException("Complete the previous content item before opening this item");
        }
    }

    private boolean canBypassSequence(UserAccount user) {
        return user.getRole() == UserRole.ADMIN;
    }
}
