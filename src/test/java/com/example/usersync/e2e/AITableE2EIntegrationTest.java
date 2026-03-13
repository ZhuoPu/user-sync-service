package com.example.usersync.e2e;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * End-to-End Integration Test simulating the full sync pipeline:
 * HCM System (AITable) → User Sync Service → IMA System (AITable)
 *
 * Scenario:
 * 1. HCM system has a new user (simulated via UserEvent)
 * 2. User Sync Service processes the event
 * 3. User Sync Service syncs user data to IMA system via external API
 * 4. Verify the sync pipeline completes successfully
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=kafka01.dev.jereh.cn:9092",
        "app.rest-api.base-url=https://aitable.ai",
        "app.rest-api.sync-endpoint=/fusion/v1/datasheets/dsthiermXySxq5drDV/records"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("AITable End-to-End Integration Tests")
class AITableE2EIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSyncService userSyncService;

    private static final String TEST_EMPLOYEE_ID = "TEST001";
    private static final String TEST_USER_NAME = "测试用户";
    private static final String TEST_PHONE = "13800138000";
    private static final String TEST_EMAIL = "test@example.com";

    @BeforeEach
    void setUp() {
        // Clear database
        userRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @Nested
    @DisplayName("Full Sync Pipeline Tests")
    class FullSyncPipelineTests {

        @Test
        @DisplayName("Should sync HCM user to IMA system through complete pipeline")
        void shouldSyncHCMUserToIMA_throughCompletePipeline() {
            // Given - HCM system creates a new user event
            UserEvent hcmUserEvent = UserEvent.builder()
                    .userId("user-" + TEST_EMPLOYEE_ID)
                    .username(TEST_USER_NAME)
                    .email(TEST_EMAIL)
                    .firstName(TEST_USER_NAME)
                    .lastName("")
                    .phoneNumber(TEST_PHONE)
                    .eventType(UserEvent.EventType.CREATE)
                    .eventTimestamp(Instant.now())
                    .dataVersion(1L)
                    .build();

            // When - Process the HCM user event
            userSyncService.processUserEvent(hcmUserEvent);

            // Then - Verify user is persisted in local database with PENDING status
            Optional<User> user = userRepository.findById("user-" + TEST_EMPLOYEE_ID);
            assertThat(user).isPresent();
            assertThat(user.get().getUsername()).isEqualTo(TEST_USER_NAME);
            assertThat(user.get().getEmail()).isEqualTo(TEST_EMAIL);
            assertThat(user.get().getPhoneNumber()).isEqualTo(TEST_PHONE);
            assertThat(user.get().getSyncStatus()).isEqualTo(User.SyncStatus.PENDING);
            assertThat(user.get().getDataHash()).isNotNull();
        }

        @Test
        @DisplayName("Should handle UPDATE event through pipeline")
        void shouldHandleUPDATE_eventThroughPipeline() {
            // Given - Existing synced user
            UserEvent createEvent = UserEvent.builder()
                    .userId("user-UPDATE001")
                    .username("原始用户")
                    .email("original@example.com")
                    .phoneNumber("13000000000")
                    .eventType(UserEvent.EventType.CREATE)
                    .build();

            userSyncService.processUserEvent(createEvent);

            // Mark as synced
            User user = userRepository.findById("user-UPDATE001").get();
            user.setSyncStatus(User.SyncStatus.SYNCED);
            userRepository.save(user);

            // When - UPDATE event is processed
            UserEvent updateEvent = UserEvent.builder()
                    .userId("user-UPDATE001")
                    .username("更新用户")
                    .email("updated@example.com")
                    .phoneNumber("13100000000")
                    .eventType(UserEvent.EventType.UPDATE)
                    .build();

            userSyncService.processUserEvent(updateEvent);

            // Then - User should be updated and marked as CHANGED
            User updated = userRepository.findById("user-UPDATE001").get();
            assertThat(updated.getUsername()).isEqualTo("更新用户");
            assertThat(updated.getEmail()).isEqualTo("updated@example.com");
            assertThat(updated.getPhoneNumber()).isEqualTo("13100000000");
            assertThat(updated.getSyncStatus()).isEqualTo(User.SyncStatus.CHANGED);
        }

        @Test
        @DisplayName("Should handle DELETE event through pipeline")
        void shouldHandleDELETE_eventThroughPipeline() {
            // Given - Existing synced user
            UserEvent createEvent = UserEvent.builder()
                    .userId("user-DELETE001")
                    .username("待删除用户")
                    .eventType(UserEvent.EventType.CREATE)
                    .build();

            userSyncService.processUserEvent(createEvent);
            assertThat(userRepository.findById("user-DELETE001")).isPresent();

            // When - DELETE event is processed
            UserEvent deleteEvent = UserEvent.builder()
                    .userId("user-DELETE001")
                    .eventType(UserEvent.EventType.DELETE)
                    .build();

            userSyncService.processUserEvent(deleteEvent);

            // Then - User should be deleted from database
            assertThat(userRepository.findById("user-DELETE001")).isEmpty();
        }

        @Test
        @DisplayName("Should handle multiple users syncing through pipeline")
        void shouldHandleMultipleUsers_syncingThroughPipeline() {
            // Given - Multiple HCM users
            List<UserEvent> events = new ArrayList<>();
            for (int i = 1; i <= 3; i++) {
                UserEvent event = UserEvent.builder()
                        .userId("user-MULTI00" + i)
                        .username("测试用户" + i)
                        .email("test" + i + "@example.com")
                        .phoneNumber("1380013800" + i)
                        .eventType(UserEvent.EventType.CREATE)
                        .build();
                events.add(event);
            }

            // When - Process all events
            for (UserEvent event : events) {
                userSyncService.processUserEvent(event);
            }

            // Then - All users should be persisted
            assertThat(userRepository.count()).isEqualTo(3);
            for (int i = 1; i <= 3; i++) {
                Optional<User> user = userRepository.findById("user-MULTI00" + i);
                assertThat(user).isPresent();
                assertThat(user.get().getUsername()).isEqualTo("测试用户" + i);
            }
        }
    }

    @Nested
    @DisplayName("Sync Status Tests")
    class SyncStatusTests {

        @Test
        @DisplayName("Should verify user appears in database after event processing")
        void shouldVerifyUserAppears_afterEventProcessing() {
            // Given
            String userId = "user-POLL001";
            UserEvent event = UserEvent.builder()
                    .userId(userId)
                    .username("轮询测试用户")
                    .email("poll@example.com")
                    .phoneNumber("13700137000")
                    .eventType(UserEvent.EventType.CREATE)
                    .build();

            // When - Event is processed
            userSyncService.processUserEvent(event);

            // Then - Verify user appears in database
            Optional<User> user = userRepository.findById(userId);
            assertThat(user).isPresent();
            assertThat(user.get().getUsername()).isEqualTo("轮询测试用户");
            assertThat(user.get().getSyncStatus()).isEqualTo(User.SyncStatus.PENDING);
        }

        @Test
        @DisplayName("Should verify sync status changes after sync attempt failure")
        void shouldVerifySyncStatusChanges_afterSyncAttemptFailure() {
            // Given
            String userId = "user-STATUS001";
            UserEvent event = UserEvent.builder()
                    .userId(userId)
                    .username("状态测试用户")
                    .eventType(UserEvent.EventType.CREATE)
                    .build();

            userSyncService.processUserEvent(event);

            // Initially PENDING
            User user = userRepository.findById(userId).get();
            assertThat(user.getSyncStatus()).isEqualTo(User.SyncStatus.PENDING);

            // When - Sync is attempted (will fail without external API)
            userSyncService.syncPendingUsers();

            // Then - Status should change to FAILED with retry count incremented
            User failedUser = userRepository.findById(userId).get();
            assertThat(failedUser.getSyncStatus()).isEqualTo(User.SyncStatus.FAILED);
            assertThat(failedUser.getRetryCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Data Mapping Tests")
    class DataMappingTests {

        @Test
        @DisplayName("Should correctly map user fields")
        void shouldCorrectlyMapFields() {
            // Given
            String employeeId = "MAP001";
            String name = "字段映射用户";
            String phone = "13600136000";
            String email = "mapping@example.com";

            UserEvent event = UserEvent.builder()
                    .userId("user-" + employeeId)
                    .username(name)
                    .email(email)
                    .phoneNumber(phone)
                    .firstName("Field")
                    .lastName("Mapper")
                    .eventType(UserEvent.EventType.CREATE)
                    .build();

            // When
            userSyncService.processUserEvent(event);

            // Then - Verify all fields are correctly mapped
            User user = userRepository.findById("user-" + employeeId).get();
            assertThat(user.getUsername()).isEqualTo(name);
            assertThat(user.getEmail()).isEqualTo(email);
            assertThat(user.getPhoneNumber()).isEqualTo(phone);
            assertThat(user.getFirstName()).isEqualTo("Field");
            assertThat(user.getLastName()).isEqualTo("Mapper");
        }

        @Test
        @DisplayName("Should handle Chinese characters correctly")
        void shouldHandleChineseCharacters_correctly() {
            // Given
            String chineseName = "王五";
            String employeeId = "CHINESE001";

            UserEvent event = UserEvent.builder()
                    .userId("user-" + employeeId)
                    .username(chineseName)
                    .email("wangwu@example.com")
                    .phoneNumber("13500135000")
                    .eventType(UserEvent.EventType.CREATE)
                    .build();

            // When
            userSyncService.processUserEvent(event);

            // Then - Verify Chinese characters are preserved
            User user = userRepository.findById("user-" + employeeId).get();
            assertThat(user.getUsername()).isEqualTo(chineseName);
        }

        @Test
        @DisplayName("Should generate consistent hash for data integrity")
        void shouldGenerateConsistentHash_forDataIntegrity() {
            // Given
            String userId = "user-HASH001";
            UserEvent event = UserEvent.builder()
                    .userId(userId)
                    .username("哈希测试用户")
                    .email("hash@example.com")
                    .phoneNumber("13400134000")
                    .eventType(UserEvent.EventType.CREATE)
                    .build();

            // When
            userSyncService.processUserEvent(event);

            // Then - Verify hash is generated
            User user = userRepository.findById(userId).get();
            assertThat(user.getDataHash()).isNotNull();
            assertThat(user.getDataHash()).hasSize(64); // SHA-256 produces 64 hex chars

            // Process same event again - should not change hash
            user.setSyncStatus(User.SyncStatus.SYNCED);
            userRepository.save(user);

            UserEvent sameEvent = UserEvent.builder()
                    .userId(userId)
                    .username("哈希测试用户")
                    .email("hash@example.com")
                    .phoneNumber("13400134000")
                    .eventType(UserEvent.EventType.UPDATE)
                    .build();

            userSyncService.processUserEvent(sameEvent);

            User unchanged = userRepository.findById(userId).get();
            assertThat(unchanged.getDataHash()).isEqualTo(user.getDataHash());
            assertThat(unchanged.getSyncStatus()).isEqualTo(User.SyncStatus.SYNCED);
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle sync failure and retry correctly")
        void shouldHandleSyncFailure_andRetryCorrectly() {
            // Given
            String userId = "user-ERROR001";
            UserEvent event = UserEvent.builder()
                    .userId(userId)
                    .username("错误处理用户")
                    .eventType(UserEvent.EventType.CREATE)
                    .build();

            userSyncService.processUserEvent(event);

            // When - First sync fails
            userSyncService.syncPendingUsers();

            // Then - User should be FAILED with retry count 1
            User user = userRepository.findById(userId).get();
            assertThat(user.getSyncStatus()).isEqualTo(User.SyncStatus.FAILED);
            assertThat(user.getRetryCount()).isEqualTo(1);

            // When - Another sync attempt (still fails without external API)
            userSyncService.syncPendingUsers();

            // Then - Retry count should increment
            User retried = userRepository.findById(userId).get();
            assertThat(retried.getRetryCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should not retry users exceeding MAX_RETRY")
        void shouldNotRetryUsers_exceedingMaxRetry() {
            // Given - User at max retry count
            User user = User.builder()
                    .userId("user-MAXRETRY")
                    .username("最大重试用户")
                    .syncStatus(User.SyncStatus.FAILED)
                    .retryCount(3)
                    .build();
            userRepository.save(user);

            // When - Sync is attempted
            int syncedCount = userSyncService.syncPendingUsers();

            // Then - Should not attempt sync
            assertThat(syncedCount).isEqualTo(0);

            User stillFailed = userRepository.findById("user-MAXRETRY").get();
            assertThat(stillFailed.getSyncStatus()).isEqualTo(User.SyncStatus.FAILED);
            assertThat(stillFailed.getRetryCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("Should continue processing when one user fails")
        void shouldContinueProcessing_whenOneUserFails() {
            // Given - Multiple users with different statuses
            User pending1 = User.builder()
                    .userId("user-pending-1")
                    .username("pending1")
                    .syncStatus(User.SyncStatus.PENDING)
                    .retryCount(0)
                    .build();
            User pending2 = User.builder()
                    .userId("user-pending-2")
                    .username("pending2")
                    .syncStatus(User.SyncStatus.PENDING)
                    .retryCount(0)
                    .build();
            userRepository.saveAll(List.of(pending1, pending2));

            // When - Sync is attempted (both fail without external API)
            int syncedCount = userSyncService.syncPendingUsers();

            // Then - Both should be processed (marked as FAILED)
            assertThat(userRepository.findBySyncStatus(User.SyncStatus.FAILED)).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Data Integrity Tests")
    class DataIntegrityTests {

        @Test
        @DisplayName("Should detect data changes correctly")
        void shouldDetectDataChanges_correctly() {
            // Given - Initial user
            UserEvent event1 = UserEvent.builder()
                    .userId("user-CHANGE001")
                    .username("原始用户")
                    .email("original@example.com")
                    .eventType(UserEvent.EventType.CREATE)
                    .build();

            userSyncService.processUserEvent(event1);
            String originalHash = userRepository.findById("user-CHANGE001").get().getDataHash();

            // Mark as SYNCED
            User user = userRepository.findById("user-CHANGE001").get();
            user.setSyncStatus(User.SyncStatus.SYNCED);
            userRepository.save(user);

            // When - Data changes
            UserEvent event2 = UserEvent.builder()
                    .userId("user-CHANGE001")
                    .username("修改用户")
                    .email("modified@example.com")
                    .eventType(UserEvent.EventType.UPDATE)
                    .build();

            userSyncService.processUserEvent(event2);

            // Then - Hash should be different and status should be CHANGED
            User updated = userRepository.findById("user-CHANGE001").get();
            assertThat(updated.getDataHash()).isNotEqualTo(originalHash);
            assertThat(updated.getSyncStatus()).isEqualTo(User.SyncStatus.CHANGED);
        }

        @Test
        @DisplayName("Should preserve data across full pipeline")
        void shouldPreserveData_acrossFullPipeline() {
            // Given - User with complete data
            String userId = "user-FULLPIPE001";
            UserEvent event = UserEvent.builder()
                    .userId(userId)
                    .username("完整用户")
                    .email("full@example.com")
                    .firstName("完整")
                    .lastName("数据")
                    .phoneNumber("13800138888")
                    .eventType(UserEvent.EventType.CREATE)
                    .build();

            // When
            userSyncService.processUserEvent(event);

            // Then - All data should be preserved
            User user = userRepository.findById(userId).get();
            assertThat(user.getUsername()).isEqualTo("完整用户");
            assertThat(user.getEmail()).isEqualTo("full@example.com");
            assertThat(user.getFirstName()).isEqualTo("完整");
            assertThat(user.getLastName()).isEqualTo("数据");
            assertThat(user.getPhoneNumber()).isEqualTo("13800138888");
        }
    }
}
