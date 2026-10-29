package com.carbonedge.backend.config;

import com.carbonedge.backend.service.SystemSettingsService;
import jakarta.servlet.MultipartConfigElement;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

@Configuration
@RequiredArgsConstructor
public class MultipartConfiguration {
    private static final int MULTIPART_REQUEST_OVERHEAD_MB = 10;

    private final SystemSettingsService systemSettingsService;

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        int maxUploadSizeMb = systemSettingsService.getMaxUploadSizeMb();
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(DataSize.ofMegabytes(maxUploadSizeMb));
        // A multipart request is slightly larger than the file itself because it
        // also contains boundaries and headers. Keep the file policy exact while
        // allowing enough request overhead for files near the configured limit.
        factory.setMaxRequestSize(DataSize.ofMegabytes(maxUploadSizeMb + MULTIPART_REQUEST_OVERHEAD_MB));
        return factory.createMultipartConfig();
    }
}
