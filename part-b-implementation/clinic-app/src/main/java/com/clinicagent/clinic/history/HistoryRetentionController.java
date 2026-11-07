package com.clinicagent.clinic.history;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/history/retention")
@Tag(name = "History", description = "History retention")
public class HistoryRetentionController {

    private final HistoryRetentionJob job;

    public HistoryRetentionController(HistoryRetentionJob job) {
        this.job = job;
    }

    @PostMapping("/run")
    @PreAuthorize("hasRole('CLINIC_MANAGER')")
    @Operation(summary = "Run history retention now (clinic manager only); the job also runs daily on its own")
    public HistoryRetentionJob.RetentionResult runNow() {
        return job.run();
    }
}
