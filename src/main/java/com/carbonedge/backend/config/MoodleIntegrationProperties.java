package com.carbonedge.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "moodle.integration")
public class MoodleIntegrationProperties {

    private String mode = "real";
    private String baseUrl = "https://tk-rubsub.online/lms";
    private String loginBaseUrl = "https://tk-rubsub.online/lms";
    private String serviceToken = "";
    private String loginService = "moodle_mobile_app";
    private String launchSharedSecret = "";
    private Long launchTokenTtlSeconds = 60L;
    private String launchPluginPath = "/local/lmslaunch/launch.php";
}
