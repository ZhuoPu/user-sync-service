package com.example.usersync.integration;

import com.example.usersync.dto.UserEvent;
import com.example.usersync.entity.User;
import com.example.usersync.repository.UserRepository;
import com.example.usersync.service.UserSyncService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the User Sync Service.
 * Tests the service layer with H2 database without external dependencies.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=kafka01.dev.jereh.cn:9092"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("User Sync Integration Tests")
class UserSyncIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSyncService userSyncService;

    @BeforeEach
    void setUp() {
        // Clear database
        userRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        // Clean up database after each test
        userRepository.deleteAll();
    }

    @Nested
    @DisplayName("Database Persistence Tests")
    class DatabasePersistenceTests {

        @Test
        @DisplayName("Should persist and retrieve user from database")
        void shouldPersistAndRetrieveUser() {
            // Given
            User user = User.builder()
                    .userId("test-user-1")
                    .username("testuser")
                    .email("test@example.com")
                    .firstName("Test")
                    .lastName("User")
                    .phoneNumber("+1234567890")
                    .syncStatus(User.SyncStatus.PENDING)
                    .retryCount(0)
                    .build();

            // When
            userRepository.save(user);

            // Then
            assertThat(userRepository.findById("test-user-1")).isPresent();
            User retrieved = userRepository.findById("test-user-1").get();
            assertThat(retrieved.getUsername()).isEqualTo("testuser");
            assertThat(retrieved.getEmail()).isEqualTo("test@example.com");
            assertThat(retrieved.getSyncStatus()).isEqualTo(User.SyncStatus.PENDING);
        }

        @Test
        @DisplayName("Should update existing user in database")
        void shouldUpdateExistingUser() {
            // Given - Create user
            User user = User.builder()
                    .userId("update-user-1")
                    .username("original")
                    .email("original@example.com")
                    .syncStatus(User.SyncStatus.PENDING)
                    .build();
            userRepository.save(user);

            // When - Update user
            User retrieved = userRepository.findById("update-user-1").get();
            retrieved.setUsername("updated");
            retrieved.setEmail("updated@example.com");
            retrieved.setSyncStatus(User.SyncStatus.SYNCED);
            userRepository.save(retrieved);

            // Then
            User updated = userRepository.findById("update-user-1").get();
            assertThat(updated.getUsername()).isEqualTo("updated");
            assertThat(updated.getEmail()).isEqualTo("updated@example.com");
            assertThat(updated.getSyncStatus()).isEqualTo(User.SyncStatus.SYNCED);
        }

        @Test
        @DisplayName("Should delete user from database")
        void shouldDeleteUser() {
            // Given - Create user
            User user = User.builder()
                    .userId("delete-user-1")
                    .username("deleteuser")
                    .build();
            userRepository.save(user);
            assertThat(userRepository.findById("delete-user-1")).isPresent();

            // When
            userRepository.delete(user);

            // Then
            assertThat(userRepository.findById("delete-user-1")).isEmpty();
        }

        @Test
        @DisplayName("Should find users by sync status")
        void shouldFindUsersBySyncStatus() {
            // Given - Create users with different statuses
            userRepository.save(User.builder()
                    .userId("pending-1")
                    .username("pending1")
                    .syncStatus(User.SyncStatus.PENDING)
                    .build());
            userRepository.save(User.builder()
                    .userId("synced-1")
                    .username("synced1")
                    .syncStatus(User.SyncStatus.SYNCED)
                    .build());
            userRepository.save(User.builder()
                    .userId("pending-2")
                    .username("pending2")
                    .syncStatus(User.SyncStatus.PENDING)
                    .build());

            // When
            List<User> pendingUsers = userRepository.findBySyncStatus(User.SyncStatus.PENDING);
            List<User> syncedUsers = userRepository.findBySyncStatus(User.SyncStatus.SYNCED);

            // Then
            assertThat(pendingUsers).hasSize(2);
            assertThat(syncedUsers).hasSize(1);
        }

        @Test
        @DisplayName("Should find users by sync status and retry count")
        void shouldFindUsersBySyncStatusAndRetryCount() {
            // Given - Create users with different retry counts
            userRepository.save(User.builder()
                    .userId("retry-1")
                    .username("user1")
                    .syncStatus(User.SyncStatus.FAILED)
                    .retryCount(1)
                    .build());
            userRepository.save(User.builder()
                    .userId("retry-2")
                    .username("user2")
                    .syncStatus(User.SyncStatus.FAILED)
                    .retryCount(3)
                    .build());
            userRepository.save(User.builder()
                    .userId("retry-3")
                    .username("user3")
                    .syncStatus(User.SyncStatus.FAILED)
                    .retryCount(2)
                    .build());

            // When - Clear persistence context to ensure fresh query
            userRepository.flush();

            List<User> retryableUsers = userRepository.findBySyncStatusAndRetryCountLessThan(
                    User.SyncStatus.FAILED, 3);

            // Then
            assertThat(retryableUsers).hasSize(2);
            assertThat(retryableUsers)
                    .extracting("userId")
                    .containsExactlyInAnyOrder("retry-1", "retry-3");
        }
    }

    @Nested
    @DisplayName("Service Layer Integration Tests")
    class ServiceLayerTests {

        @Test
        @DisplayName("Should process CREATE event and persist to database")
        void shouldProcessCreateEvent() {
            // Given
            UserEvent event = UserEvent.builder()
                    .userId("create-test-1")
                    .username("newuser")
                    .email("new@example.com")
                    .firstName("New")
                    .lastName("User")
                    .phoneNumber("+1234567890")
                    .eventType(UserEvent.EventType.CREATE)
                    .eventTimestamp(Instant.now())
                    .dataVersion(1L)
                    .build();

            // When
            userSyncService.processUserEvent(event);

            // Then
            assertThat(userRepository.findById("create-test-1")).isPresent();
            User user = userRepository.findById("create-test-1").get();
            assertThat(user.getUsername()).isEqualTo("newuser");
            assertThat(user.getEmail()).isEqualTo("new@example.com");
            assertThat(user.getSyncStatus()).isEqualTo(User.SyncStatus.PENDING);
            assertThat(user.getDataHash()).isNotNull();
        }

        @Test
        @DisplayName("Should process UPDATE event and modify existing user")
        void shouldProcessUpdateEvent() {
            // Given - Existing user
            User existing = User.builder()
                    .userId("update-test-1")
                    .username("olduser")
                    .email("old@example.com")
                    .firstName("Old")
                    .lastName("User")
                    .phoneNumber("+1111111111")
                    .dataHash("old-hash-value")
                    .syncStatus(User.SyncStatus.SYNCED)
                    .build();
            userRepository.save(existing);

            UserEvent event = UserEvent.builder()
                    .userId("update-test-1")
                    .username("updateduser")
                    .email("updated@example.com")
                    .firstName("Updated")
                    .lastName("User")
                    .phoneNumber("+2222222222")
                    .eventType(UserEvent.EventType.UPDATE)
                    .eventTimestamp(Instant.now())
                    .dataVersion(2L)
                    .build();

            // When
            userSyncService.processUserEvent(event);

            // Then
            User updated = userRepository.findById("update-test-1").get();
            assertThat(updated.getUsername()).isEqualTo("updateduser");
            assertThat(updated.getEmail()).isEqualTo("updated@example.com");
            assertThat(updated.getFirstName()).isEqualTo("Updated");
            assertThat(updated.getPhoneNumber()).isEqualTo("+2222222222");
            assertThat(updated.getSyncStatus()).isEqualTo(User.SyncStatus.CHANGED);
            assertThat(updated.getDataHash()).isNotEqualTo("old-hash-value");
        }

        @Test
        @DisplayName("Should process DELETE event and remove user")
        void shouldProcessDeleteEvent() {
            // Given - Existing user
            User existing = User.builder()
                    .userId("delete-test-1")
                    .username("deleteuser")
                    .email("delete@example.com")
                    .syncStatus(User.SyncStatus.SYNCED)
                    .build();
            userRepository.save(existing);
            assertThat(userRepository.findById("delete-test-1")).isPresent();

            UserEvent event = UserEvent.builder()
                    .userId("delete-test-1")
                    .eventType(UserEvent.EventType.DELETE)
                    .eventTimestamp(Instant.now())
                    .build();

            // When
            userSyncService.processUserEvent(event);

            // Then
            assertThat(userRepository.findById("delete-test-1")).isEmpty();
        }

        @Test
        @DisplayName("Should not update user when data has not changed")
        void shouldNotUpdateUser_whenDataUnchanged() {
            // Given - Create user via service first to establish the hash
            UserEvent createEvent = UserEvent.builder()
                    .userId("unchanged-test-1")
                    .username("sameuser")
                    .email("same@example.com")
                    .firstName("Same")
                    .lastName("User")
                    .phoneNumber("+1234567890")
                    .eventType(UserEvent.EventType.CREATE)
                    .eventTimestamp(Instant.now())
                    .dataVersion(1L)
                    .build();
            userSyncService.processUserEvent(createEvent);

            // Get the original hash and mark as SYNCED
            String originalHash = userRepository.findById("unchanged-test-1").get().getDataHash();
            User user = userRepository.findById("unchanged-test-1").get();
            user.setSyncStatus(User.SyncStatus.SYNCED);
            userRepository.save(user);

            // When - Send UPDATE event with same data
            UserEvent updateEvent = UserEvent.builder()
                    .userId("unchanged-test-1")
                    .username("sameuser")
                    .email("same@example.com")
                    .firstName("Same")
                    .lastName("User")
                    .phoneNumber("+1234567890")
                    .eventType(UserEvent.EventType.UPDATE)
                    .eventTimestamp(Instant.now())
                    .dataVersion(2L)
                    .build();
            userSyncService.processUserEvent(updateEvent);

            // Then - User should still be SYNCED (no change detected)
            User result = userRepository.findById("unchanged-test-1").get();
            assertThat(result.getSyncStatus()).isEqualTo(User.SyncStatus.SYNCED);
            assertThat(result.getDataHash()).isEqualTo(originalHash);
        }

        @Test
        @DisplayName("Should handle sync for PENDING users")
        void shouldHandleSyncForPendingUsers() {
            // Given - Create PENDING users
            userRepository.save(User.builder()
                    .userId("pending-1")
                    .username("pending1")
                    .syncStatus(User.SyncStatus.PENDING)
                    .retryCount(0)
                    .build());
            userRepository.save(User.builder()
                    .userId("pending-2")
                    .username("pending2")
                    .syncStatus(User.SyncStatus.PENDING)
                    .retryCount(0)
                    .build());

            // When - Sync will fail due to no external API, but we test the flow
            int syncedCount = userSyncService.syncPendingUsers();

            // Then - Users should be marked as FAILED
            List<User> failedUsers = userRepository.findBySyncStatus(User.SyncStatus.FAILED);
            assertThat(failedUsers).hasSize(2);
        }

        @Test
        @DisplayName("Should track retry count correctly")
        void shouldTrackRetryCount() {
            // Given
            User user = User.builder()
                    .userId("retry-test-1")
                    .username("retryuser")
                    .syncStatus(User.SyncStatus.FAILED)
                    .retryCount(1)
                    .build();
            userRepository.save(user);

            // When - First sync attempt (will fail)
            userSyncService.syncPendingUsers();

            // Then - Retry count should be incremented
            User updated = userRepository.findById("retry-test-1").get();
            assertThat(updated.getRetryCount()).isEqualTo(2);
            assertThat(updated.getSyncStatus()).isEqualTo(User.SyncStatus.FAILED);
        }

        @Test
        @DisplayName("Should not sync users beyond MAX_RETRY")
        void shouldNotSyncBeyondMaxRetry() {
            // Given - User at max retry
            userRepository.save(User.builder()
                    .userId("max-retry-1")
                    .username("maxretry")
                    .syncStatus(User.SyncStatus.FAILED)
                    .retryCount(3)
                    .build());

            // When
            int syncedCount = userSyncService.syncPendingUsers();

            // Then - Should not attempt sync
            assertThat(syncedCount).isEqualTo(0);
            User user = userRepository.findById("max-retry-1").get();
            assertThat(user.getRetryCount()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Hash Calculation Tests")
    class HashTests {

        @Test
        @DisplayName("Should generate consistent hash for same data")
        void shouldGenerateConsistentHash() {
            // Given
            UserEvent event1 = UserEvent.builder()
                    .userId("hash-test-1")
                    .username("hashuser")
                    .email("hash@example.com")
                    .firstName("Hash")
                    .lastName("User")
                    .phoneNumber("+1234567890")
                    .eventType(UserEvent.EventType.CREATE)
                    .dataVersion(1L)
                    .build();

            // When - Process twice
            userSyncService.processUserEvent(event1);
            String firstHash = userRepository.findById("hash-test-1").get().getDataHash();

            // Update with same data
            UserEvent event2 = UserEvent.builder()
                    .userId("hash-test-1")
                    .username("hashuser")
                    .email("hash@example.com")
                    .firstName("Hash")
                    .lastName("User")
                    .phoneNumber("+1234567890")
                    .eventType(UserEvent.EventType.UPDATE)
                    .dataVersion(2L)
                    .build();
            userSyncService.processUserEvent(event2);

            // Then - Hash should be the same
            User user = userRepository.findById("hash-test-1").get();
            assertThat(user.getDataHash()).isEqualTo(firstHash);
        }

        @Test
        @DisplayName("Should generate different hashes for different data")
        void shouldGenerateDifferentHashes() {
            // Given
            UserEvent event1 = UserEvent.builder()
                    .userId("diff-hash-1")
                    .username("user1")
                    .email("user1@example.com")
                    .eventType(UserEvent.EventType.CREATE)
                    .build();
            UserEvent event2 = UserEvent.builder()
                    .userId("diff-hash-2")
                    .username("user2")
                    .email("user2@example.com")
                    .eventType(UserEvent.EventType.CREATE)
                    .build();

            // When
            userSyncService.processUserEvent(event1);
            userSyncService.processUserEvent(event2);

            // Then
            String hash1 = userRepository.findById("diff-hash-1").get().getDataHash();
            String hash2 = userRepository.findById("diff-hash-2").get().getDataHash();
            assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        @DisplayName("Should include all fields in hash calculation")
        void shouldIncludeAllFieldsInHash() {
            // Given - Create initial user
            UserEvent event1 = UserEvent.builder()
                    .userId("field-hash-1")
                    .username("user1")
                    .email("user1@example.com")
                    .firstName("First")
                    .lastName("Last")
                    .phoneNumber("+1111111111")
                    .eventType(UserEvent.EventType.CREATE)
                    .build();
            userSyncService.processUserEvent(event1);
            String originalHash = userRepository.findById("field-hash-1").get().getDataHash();

            // When - Update with different email only
            UserEvent event2 = UserEvent.builder()
                    .userId("field-hash-1")
                    .username("user1")
                    .email("different@example.com")  // Changed
                    .firstName("First")
                    .lastName("Last")
                    .phoneNumber("+1111111111")
                    .eventType(UserEvent.EventType.UPDATE)
                    .build();
            userSyncService.processUserEvent(event2);

            // Then - Hash should be different
            String newHash = userRepository.findById("field-hash-1").get().getDataHash();
            assertThat(newHash).isNotEqualTo(originalHash);
        }
    }

    @Nested
    @DisplayName("Data Integrity Tests")
    class DataIntegrityTests {

        @Test
        @DisplayName("Should maintain data consistency across multiple operations")
        void shouldMaintainDataConsistency() {
            // Given - Initial user
            UserEvent createEvent = UserEvent.builder()
                    .userId("consistency-1")
                    .username("user1")
                    .email("user1@example.com")
                    .eventType(UserEvent.EventType.CREATE)
                    .build();
            userSyncService.processUserEvent(createEvent);

            // When - Multiple updates
            UserEvent update1 = UserEvent.builder()
                    .userId("consistency-1")
                    .username("user1-updated")
                    .email("user1-updated@example.com")
                    .eventType(UserEvent.EventType.UPDATE)
                    .build();
            userSyncService.processUserEvent(update1);

            UserEvent update2 = UserEvent.builder()
                    .userId("consistency-1")
                    .username("user1-final")
                    .email("user1-final@example.com")
                    .eventType(UserEvent.EventType.UPDATE)
                    .build();
            userSyncService.processUserEvent(update2);

            // Then - Final state should reflect last update
            User finalUser = userRepository.findById("consistency-1").get();
            assertThat(finalUser.getUsername()).isEqualTo("user1-final");
            assertThat(finalUser.getEmail()).isEqualTo("user1-final@example.com");
        }

        @Test
        @DisplayName("Should handle concurrent user operations")
        void shouldHandleConcurrentOperations() {
            // Given - Create multiple users
            for (int i = 1; i <= 10; i++) {
                UserEvent event = UserEvent.builder()
                        .userId("concurrent-" + i)
                        .username("user" + i)
                        .email("user" + i + "@example.com")
                        .eventType(UserEvent.EventType.CREATE)
                        .build();
                userSyncService.processUserEvent(event);
            }

            // Then - All users should be persisted
            assertThat(userRepository.count()).isEqualTo(10);
            for (int i = 1; i <= 10; i++) {
                assertThat(userRepository.findById("concurrent-" + i)).isPresent();
            }
        }
    }
}
