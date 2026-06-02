package com.carbonedge.backend.client.moodle;

import com.carbonedge.backend.dto.moodle.MoodleCreateUserRequest;
import com.carbonedge.backend.dto.moodle.MoodleCourseRecord;
import com.carbonedge.backend.dto.moodle.MoodleUserRecord;
import java.util.List;

public interface MoodleClient {

    MoodleUserRecord createUser(MoodleCreateUserRequest request);

    void validateCredentials(String username, String password);

    String createLoginUrl(String username, Long moodleUserId);

    List<MoodleCourseRecord> getUserCourses(Long moodleUserId);

    String createCourseLaunchUrl(Long courseId);
}
