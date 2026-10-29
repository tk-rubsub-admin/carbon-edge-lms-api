package com.carbonedge.backend.client.moodle;

import com.carbonedge.backend.dto.moodle.MoodleCreateUserRequest;
import com.carbonedge.backend.dto.moodle.MoodleCreateCourseRequest;
import com.carbonedge.backend.dto.moodle.MoodleCreateCategoryRequest;
import com.carbonedge.backend.dto.moodle.MoodleCategoryRecord;
import com.carbonedge.backend.dto.moodle.MoodleCourseRecord;
import com.carbonedge.backend.dto.moodle.MoodleUpdateCategoryRequest;
import com.carbonedge.backend.dto.moodle.MoodleUpdateCourseRequest;
import com.carbonedge.backend.dto.moodle.MoodleUserRecord;
import com.carbonedge.backend.dto.moodle.MoodleUpdateUserRequest;
import java.util.List;

public interface MoodleClient {

    MoodleUserRecord createUser(MoodleCreateUserRequest request);

    void updateUser(MoodleUpdateUserRequest request);

    MoodleCourseRecord createCourse(MoodleCreateCourseRequest request);

    MoodleCategoryRecord createCategory(MoodleCreateCategoryRequest request);

    void enrollStudent(Long courseId, Long moodleUserId);

    void unenrollStudent(Long courseId, Long moodleUserId);

    MoodleCourseRecord updateCourse(MoodleUpdateCourseRequest request);

    MoodleCategoryRecord updateCategory(MoodleUpdateCategoryRequest request);

    void deleteCourse(Long courseId);

    void deleteCategory(Long categoryId);

    MoodleCourseRecord getCourseById(Long courseId);

    MoodleCategoryRecord getCategoryById(Long categoryId);

    List<MoodleCategoryRecord> getCategories();

    List<MoodleCourseRecord> getCourses();

    void validateCredentials(String username, String password);

    void updatePassword(Long moodleUserId, String newPassword);

    String createLoginUrl(String username, Long moodleUserId);

    List<MoodleCourseRecord> getUserCourses(Long moodleUserId);

    String createCourseLaunchUrl(Long courseId);

    String getSiteInfo();
}
