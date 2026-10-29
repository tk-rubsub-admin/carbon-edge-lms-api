package com.carbonedge.backend.controller;

import com.carbonedge.backend.dto.BackupJobResponse;
import com.carbonedge.backend.dto.BackupOverviewResponse;
import com.carbonedge.backend.service.DatabaseBackupService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/backups")
@RequiredArgsConstructor
public class DatabaseBackupController {
    private final DatabaseBackupService service;

    @GetMapping
    public BackupOverviewResponse overview(@RequestHeader("Authorization") String authorization) {
        return service.overview(authorization);
    }

    @PostMapping
    public ResponseEntity<BackupJobResponse> start(@RequestHeader("Authorization") String authorization) {
        return ResponseEntity.accepted().body(service.request(authorization));
    }

    @GetMapping("/{id}")
    public BackupJobResponse status(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String id
    ) {
        return service.status(authorization, id);
    }

    @GetMapping("/{id}/{database}")
    public ResponseEntity<FileSystemResource> download(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String id,
            @PathVariable String database
    ) {
        FileSystemResource resource = service.download(authorization, id, database);
        String filename = "backup-" + id + "-" + database + ".sql.gz.enc";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(resource);
    }
}
