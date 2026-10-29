package com.carbonedge.backend.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/content-files")
public class CourseContentFileController {
    private final Path storagePath;

    public CourseContentFileController(@Value("${app.content-storage-path}") String storagePath) {
        this.storagePath = Path.of(storagePath).toAbsolutePath().normalize();
    }

    @GetMapping("/{fileName:.+}")
    public ResponseEntity<Resource> getContentFile(@PathVariable String fileName) {
        if (!fileName.matches("[a-f0-9-]{36}\\.(pdf|mp4|mov|webm|ogv)")) {
            return ResponseEntity.notFound().build();
        }
        Path file = storagePath.resolve(fileName).normalize();
        if (!file.startsWith(storagePath) || !Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .contentType(MediaTypeFactory.getMediaType(fileName).orElse(MediaType.APPLICATION_OCTET_STREAM))
                .body(new FileSystemResource(file));
    }
}
