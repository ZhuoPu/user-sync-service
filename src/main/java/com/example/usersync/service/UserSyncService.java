package com.example.usersync.service;

import com.example.usersync.dto.UserEvent;
import com.example.usersync.entity.User;
import com.example.usersync.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserSyncService {

    private final UserRepository userRepository;
    private final ExternalApiService externalApiService;

    private static final int MAX_RETRY = 3;

    @Transactional
    public void processUserEvent(UserEvent event) {
        log.info("Processing user event: userId={}, eventType={}", event.getUserId(), event.getEventType());

        if (event.getEventType() == UserEvent.EventType.DELETE) {
            handleDelete(event);
            return;
        }

        String newDataHash = calculateDataHash(event);

        Optional<User> existingUserOpt = userRepository.findById(event.getUserId());

        if (existingUserOpt.isPresent()) {
            User existingUser = existingUserOpt.get();
            if (!newDataHash.equals(existingUser.getDataHash())) {
                log.info("User data changed for userId={}, marking for re-sync", event.getUserId());
                updateUserFromEvent(existingUser, event, newDataHash);
                existingUser.setSyncStatus(User.SyncStatus.CHANGED);
                userRepository.save(existingUser);
            } else {
                log.debug("User data unchanged for userId={}", event.getUserId());
            }
        } else {
            log.info("New user detected: userId={}", event.getUserId());
            User newUser = createUserFromEvent(event, newDataHash);
            newUser.setSyncStatus(User.SyncStatus.PENDING);
            userRepository.save(newUser);
        }
    }

    private void handleDelete(UserEvent event) {
        userRepository.findById(event.getUserId()).ifPresent(user -> {
            log.info("Deleting user: userId={}", event.getUserId());
            userRepository.delete(user);
        });
    }

    @Transactional
    public int syncPendingUsers() {
        var pendingUsers = userRepository.findBySyncStatusAndRetryCountLessThan(
                User.SyncStatus.PENDING, MAX_RETRY
        );

        var changedUsers = userRepository.findBySyncStatusAndRetryCountLessThan(
                User.SyncStatus.CHANGED, MAX_RETRY
        );

        var failedUsers = userRepository.findBySyncStatusAndRetryCountLessThan(
                User.SyncStatus.FAILED, MAX_RETRY
        );

        int syncedCount = 0;

        syncedCount += syncUserList(pendingUsers);
        syncedCount += syncUserList(changedUsers);
        syncedCount += syncUserList(failedUsers);

        return syncedCount;
    }

    private int syncUserList(java.util.List<User> users) {
        int syncedCount = 0;
        for (User user : users) {
            if (syncUserToExternalApi(user)) {
                syncedCount++;
            }
        }
        return syncedCount;
    }

    private boolean syncUserToExternalApi(User user) {
        try {
            log.info("Syncing user to external API: userId={}", user.getUserId());
            externalApiService.pushUser(user);
            user.setSyncStatus(User.SyncStatus.SYNCED);
            user.setLastSyncedAt(LocalDateTime.now());
            user.setRetryCount(0);
            userRepository.save(user);
            return true;
        } catch (Exception e) {
            log.error("Failed to sync user: userId={}, error={}", user.getUserId(), e.getMessage());
            user.setSyncStatus(User.SyncStatus.FAILED);
            user.setRetryCount(user.getRetryCount() == null ? 1 : user.getRetryCount() + 1);
            userRepository.save(user);
            return false;
        }
    }

    private User createUserFromEvent(UserEvent event, String dataHash) {
        return User.builder()
                .userId(event.getUserId())
                .username(event.getUsername())
                .email(event.getEmail())
                .firstName(event.getFirstName())
                .lastName(event.getLastName())
                .phoneNumber(event.getPhoneNumber())
                .dataHash(dataHash)
                .retryCount(0)
                .build();
    }

    private void updateUserFromEvent(User user, UserEvent event, String dataHash) {
        user.setUsername(event.getUsername());
        user.setEmail(event.getEmail());
        user.setFirstName(event.getFirstName());
        user.setLastName(event.getLastName());
        user.setPhoneNumber(event.getPhoneNumber());
        user.setDataHash(dataHash);
    }

    private String calculateDataHash(UserEvent event) {
        try {
            String data = String.format("%s|%s|%s|%s|%s|%s",
                    event.getUserId(),
                    event.getUsername(),
                    event.getEmail(),
                    event.getFirstName(),
                    event.getLastName(),
                    event.getPhoneNumber()
            );

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to calculate data hash", e);
        }
    }
}
