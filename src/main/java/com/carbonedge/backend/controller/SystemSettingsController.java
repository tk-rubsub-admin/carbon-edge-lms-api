package com.carbonedge.backend.controller;

import com.carbonedge.backend.dto.SystemSettingAuditResponse;
import com.carbonedge.backend.dto.SystemSettingResponse;
import com.carbonedge.backend.dto.UpdateSystemSettingRequest;
import com.carbonedge.backend.service.SystemSettingsService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/system-settings")
@RequiredArgsConstructor
public class SystemSettingsController {
    private final SystemSettingsService systemSettingsService;

    @GetMapping
    public List<SystemSettingResponse> getSettings(@RequestHeader("Authorization") String authorizationHeader) {
        return systemSettingsService.getSettings(authorizationHeader);
    }

    @PatchMapping("/{key}")
    public SystemSettingResponse updateSetting(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable String key,
            @Valid @RequestBody UpdateSystemSettingRequest request
    ) {
        return systemSettingsService.update(authorizationHeader, key, request.value());
    }

    @PostMapping("/moodle/test")
    public String testMoodle(@RequestHeader("Authorization") String authorizationHeader) {
        return systemSettingsService.testMoodle(authorizationHeader);
    }

    @PostMapping("/mail/test")
    public void testMail(@RequestHeader("Authorization") String authorizationHeader) {
        systemSettingsService.testMail(authorizationHeader);
    }

    @GetMapping("/audit")
    public List<SystemSettingAuditResponse> getAudit(@RequestHeader("Authorization") String authorizationHeader) {
        return systemSettingsService.getAudit(authorizationHeader);
    }
}
