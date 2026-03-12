package com.example.usersync.scheduler;

import com.example.usersync.service.UserSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SyncScheduler {

    private final UserSyncService userSyncService;

    @Scheduled(
            fixedDelayString = "${app.sync.interval-seconds:60}000",
            initialDelayString = "${app.sync.interval-seconds:60}000"
    )
    public void syncPendingUsers() {
        log.info("Starting scheduled sync of pending/changed users");
        try {
            int syncedCount = userSyncService.syncPendingUsers();
            log.info("Scheduled sync completed: {} users synced", syncedCount);
        } catch (Exception e) {
            log.error("Error during scheduled sync: {}", e.getMessage(), e);
        }
    }
}
