package com.carbonedge.backend.dto;

import java.util.List;

public record BackupOverviewResponse(boolean configured, BackupJobResponse activeJob, List<BackupFileResponse> files) {
}
