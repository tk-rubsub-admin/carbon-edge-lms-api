package com.carbonedge.backend.controller;

import com.carbonedge.backend.dto.CertificateTemplateSettings;
import com.carbonedge.backend.service.CertificateTemplateService;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class CertificateTemplateController {
    private final CertificateTemplateService service;

    @GetMapping("/admin/certificate-template")
    public CertificateTemplateSettings get(@RequestHeader("Authorization") String authorization) {
        return service.get(authorization);
    }

    @PutMapping("/admin/certificate-template")
    public CertificateTemplateSettings save(
            @RequestHeader("Authorization") String authorization,
            @RequestBody CertificateTemplateSettings settings
    ) {
        return service.save(authorization, settings);
    }

    @PostMapping(value = "/admin/certificate-template/assets", consumes = "multipart/form-data")
    public String upload(
            @RequestHeader("Authorization") String authorization,
            @RequestParam("kind") String kind,
            @RequestParam("file") MultipartFile file
    ) {
        return service.upload(authorization, file, kind);
    }

    @GetMapping("/certificate-template-assets/{filename:.+}")
    public ResponseEntity<FileSystemResource> asset(@PathVariable String filename) throws IOException {
        FileSystemResource asset = service.asset(filename);
        return ResponseEntity.ok()
                .contentType(service.mediaType(filename))
                .cacheControl(CacheControl.noCache())
                .body(asset);
    }
}
