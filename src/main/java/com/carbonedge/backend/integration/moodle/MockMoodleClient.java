package com.carbonedge.backend.integration.moodle;

import com.carbonedge.backend.client.moodle.MoodleClient;
import com.carbonedge.backend.config.MoodleIntegrationProperties;
import com.carbonedge.backend.dto.moodle.MoodleCreateUserRequest;
import com.carbonedge.backend.dto.moodle.MoodleCourseRecord;
import com.carbonedge.backend.dto.moodle.MoodleUserRecord;
import com.carbonedge.backend.exception.IntegrationException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "moodle.integration", name = "mode", havingValue = "mock")
public class MockMoodleClient implements MoodleClient {

    private final AtomicLong moodleUserSequence = new AtomicLong(1000);
    private final Map<String, MoodleUserRecord> usersByUsername = new ConcurrentHashMap<>();
    private final MoodleIntegrationProperties properties;

    public MockMoodleClient(MoodleIntegrationProperties properties) {
        this.properties = properties;
    }

    @Override
    public MoodleUserRecord createUser(MoodleCreateUserRequest request) {
        if (!"mock".equalsIgnoreCase(properties.getMode())) {
            throw new IntegrationException("Mock Moodle client is disabled");
        }
        MoodleUserRecord existing = usersByUsername.get(request.username().toLowerCase());
        if (existing != null) {
            throw new IntegrationException("Moodle username already exists");
        }
        MoodleUserRecord created = new MoodleUserRecord(
                moodleUserSequence.incrementAndGet(),
                request.username(),
                request.email()
        );
        usersByUsername.put(request.username().toLowerCase(), created);
        return created;
    }

    @Override
    public void validateCredentials(String username, String password) {
        if (!"mock".equalsIgnoreCase(properties.getMode())) {
            throw new IntegrationException("Mock Moodle client is disabled");
        }
        if (!usersByUsername.containsKey(username.toLowerCase())) {
            throw new IntegrationException("Moodle user not found");
        }
    }

    @Override
    public String createLoginUrl(String username, Long moodleUserId) {
        if (!"mock".equalsIgnoreCase(properties.getMode())) {
            throw new IntegrationException("Mock Moodle client is disabled");
        }
        String signature = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString((username + ":" + moodleUserId).getBytes(StandardCharsets.UTF_8));
        return properties.getLoginBaseUrl() + "/mock-login?username=" + username + "&token=" + signature;
    }

    @Override
    public List<MoodleCourseRecord> getUserCourses(Long moodleUserId) {
        if (!"mock".equalsIgnoreCase(properties.getMode())) {
            throw new IntegrationException("Mock Moodle client is disabled");
        }
        return List.of(new MoodleCourseRecord(
                501L,
                "COURSE-501",
                "Sample Course 501",
                "Sample Course 501",
                1,
                "Mock course for launch flow tests"
        ));
    }

    @Override
    public String createCourseLaunchUrl(Long courseId) {
        if (!"mock".equalsIgnoreCase(properties.getMode())) {
            throw new IntegrationException("Mock Moodle client is disabled");
        }
        return properties.getLoginBaseUrl() + "/course/view.php?id=" + courseId;
    }
}
