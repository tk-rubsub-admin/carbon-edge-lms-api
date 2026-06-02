package com.carbonedge.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

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

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getLoginBaseUrl() {
        return loginBaseUrl;
    }

    public void setLoginBaseUrl(String loginBaseUrl) {
        this.loginBaseUrl = loginBaseUrl;
    }

    public String getServiceToken() {
        return serviceToken;
    }

    public void setServiceToken(String serviceToken) {
        this.serviceToken = serviceToken;
    }

    public String getLoginService() {
        return loginService;
    }

    public void setLoginService(String loginService) {
        this.loginService = loginService;
    }

    public String getLaunchSharedSecret() {
        return launchSharedSecret;
    }

    public void setLaunchSharedSecret(String launchSharedSecret) {
        this.launchSharedSecret = launchSharedSecret;
    }

    public Long getLaunchTokenTtlSeconds() {
        return launchTokenTtlSeconds;
    }

    public void setLaunchTokenTtlSeconds(Long launchTokenTtlSeconds) {
        this.launchTokenTtlSeconds = launchTokenTtlSeconds;
    }

    public String getLaunchPluginPath() {
        return launchPluginPath;
    }

    public void setLaunchPluginPath(String launchPluginPath) {
        this.launchPluginPath = launchPluginPath;
    }
}
