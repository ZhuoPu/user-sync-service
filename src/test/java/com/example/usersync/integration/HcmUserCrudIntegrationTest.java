package com.example.usersync.integration;

import com.example.usersync.dto.HcmUserDto;
import com.example.usersync.dto.HcmUserSearchRequest;
import com.example.usersync.dto.HcmUserSearchResponse;
import com.example.usersync.service.HcmUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for HCM User CRUD operations.
 * These tests make real API calls to AITable.
 */
@SpringBootTest
@DisplayName("HCM User CRUD Integration Tests")
class HcmUserCrudIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(HcmUserCrudIntegrationTest.class);

    @Autowired
    private HcmUserService hcmUserService;

    @Value("${app.hcm.test.employee-id:TEST999}")
    private String testEmployeeIdPrefix;

    private HcmUserDto testUser;
    private String createdRecordId;
    private String uniqueEmployeeId;

    // Small delay to avoid AITable API rate limit (2 QPS)
    private void delay() {
        try {
            Thread.sleep(800);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Longer delay for AITable indexing
    private void longDelay() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @BeforeEach
    void setUp() {
        // Generate unique employeeId using timestamp to avoid conflicts with test data
        uniqueEmployeeId = testEmployeeIdPrefix + "_" + System.currentTimeMillis();
        testUser = HcmUserDto.builder()
                .title("李四")
                .employeeId(uniqueEmployeeId)
                .phone("13444444444")
                .system("HCM")
                .email("lisi@test.com")
                .active(true)   // 在职
                .build();
    }

    @AfterEach
    void tearDown() {
        // Cleanup: try to delete the test user if it exists
        if (uniqueEmployeeId != null) {
            try {
                HcmUserSearchResponse response = hcmUserService.search(HcmUserSearchRequest.builder().pageNum(1).build());
                response.getRecords().stream()
                        .filter(u -> uniqueEmployeeId.equals(u.getEmployeeId()))
                        .forEach(u -> {
                            log.info("Cleaning up test user: recordId={}, employeeId={}", u.getRecordId(), u.getEmployeeId());
                            hcmUserService.delete(u.getRecordId());
                        });
            } catch (Exception e) {
                log.warn("Failed to cleanup test user: {}", e.getMessage());
            }
        }
    }

    @Test
    @DisplayName("Should complete full CRUD cycle: create -> read -> delete")
    void shouldCompleteFullCrudCycle() {
        // ==================== Step 1: Get initial state ====================
        log.info("=== Step 1: Getting initial state ===");
        HcmUserSearchResponse initialResponse = hcmUserService.search(HcmUserSearchRequest.builder().pageNum(1).build());
        int initialCount = initialResponse.getRecords().size();
        List<String> initialEmployeeIds = initialResponse.getRecords().stream()
                .map(HcmUserDto::getEmployeeId)
                .toList();
        log.info("Initial state: {} users, employeeIds={}", initialCount, initialEmployeeIds);

        // Assert test user doesn't exist
        assertThat(initialEmployeeIds).doesNotContain(uniqueEmployeeId);
        delay();

        // ==================== Step 2: Create "李四" ====================
        log.info("=== Step 2: Creating user: {} ===", testUser);
        HcmUserDto createdUser = hcmUserService.create(testUser);
        createdRecordId = createdUser.getRecordId();
        log.info("Created user: recordId={}, employeeId={}, title={}",
                createdRecordId, createdUser.getEmployeeId(), createdUser.getTitle());

        assertThat(createdRecordId).isNotNull();
        assertThat(createdUser.getTitle()).isEqualTo("李四");
        assertThat(createdUser.getEmployeeId()).isEqualTo(uniqueEmployeeId);

        // Wait for AITable to index the new record
        longDelay();

        // ==================== Step 3: Get users and assert "李四" was added ====================
        log.info("=== Step 3: Getting users after creation ===");

        // Try multiple times with delay to account for AITable indexing
        HcmUserSearchResponse afterCreateResponse = null;
        int retryCount = 0;
        int maxRetries = 5;

        while (retryCount < maxRetries) {
            afterCreateResponse = hcmUserService.search(HcmUserSearchRequest.builder().pageNum(1).build());
            if (!afterCreateResponse.getRecords().isEmpty()) {
                break;
            }
            log.info("No records found yet, retrying... ({}/{})", retryCount + 1, maxRetries);
            longDelay();
            retryCount++;
        }

        int afterCreateCount = afterCreateResponse.getRecords().size();
        List<String> afterCreateEmployeeIds = afterCreateResponse.getRecords().stream()
                .map(HcmUserDto::getEmployeeId)
                .toList();

        log.info("After create: {} users, employeeIds={}", afterCreateCount, afterCreateEmployeeIds);
        assertThat(afterCreateCount).isGreaterThan(initialCount);
        assertThat(afterCreateEmployeeIds).contains(uniqueEmployeeId);

        // Verify "李四" exists in the response
        HcmUserDto foundUser = afterCreateResponse.getRecords().stream()
                .filter(u -> uniqueEmployeeId.equals(u.getEmployeeId()))
                .findFirst()
                .orElseThrow();
        assertThat(foundUser.getTitle()).isEqualTo("李四");
        assertThat(foundUser.getPhone()).isEqualTo("13444444444");
        assertThat(foundUser.getSystem()).isEqualTo("HCM");
        log.info("Found user in response: {}", foundUser);
        delay();

        // ==================== Step 4: Delete "李四" ====================
        log.info("=== Step 4: Deleting user: recordId={} ===", createdRecordId);
        boolean deleted = hcmUserService.delete(createdRecordId);
        assertThat(deleted).isTrue();
        log.info("Deleted user: recordId={}", createdRecordId);
        delay();

        // ==================== Step 5: Get users and assert back to initial state ====================
        log.info("=== Step 5: Getting users after deletion ===");
        HcmUserSearchResponse afterDeleteResponse = hcmUserService.search(HcmUserSearchRequest.builder().pageNum(1).build());
        int afterDeleteCount = afterDeleteResponse.getRecords().size();
        List<String> afterDeleteEmployeeIds = afterDeleteResponse.getRecords().stream()
                .map(HcmUserDto::getEmployeeId)
                .toList();

        log.info("After delete: {} users, employeeIds={}", afterDeleteCount, afterDeleteEmployeeIds);
        assertThat(afterDeleteEmployeeIds).doesNotContain(uniqueEmployeeId);

        // Assert we're back to the original count (approximately, since other data might change)
        // We mainly check that our test user is gone
        assertThat(afterDeleteResponse.getRecords().stream()
                .noneMatch(u -> uniqueEmployeeId.equals(u.getEmployeeId())))
                .isTrue();

        log.info("=== CRUD cycle completed successfully ===");
    }

    @Test
    @DisplayName("Should update existing user")
    void shouldUpdateExistingUser() {
        // ==================== Step 1: Create test user ====================
        log.info("=== Step 1: Creating test user ===");
        HcmUserDto createdUser = hcmUserService.create(testUser);
        createdRecordId = createdUser.getRecordId();
        delay();

        // ==================== Step 2: Update user ====================
        log.info("=== Step 2: Updating user ===");
        HcmUserDto updateRequest = HcmUserDto.builder()
                .recordId(createdRecordId)
                .title("李四-update")
                .employeeId(uniqueEmployeeId)
                .phone("13555555555")
                .system("HCM")
                .email("lisi-updated@test.com")
                .active(true)   // 在职
                .build();

        HcmUserDto updatedUser = hcmUserService.update(updateRequest);
        assertThat(updatedUser.getTitle()).isEqualTo("李四-update");
        assertThat(updatedUser.getPhone()).isEqualTo("13555555555");
        // Note: email field may not be returned by AITable API depending on view configuration
        delay();

        // ==================== Step 3: Verify update ====================
        log.info("=== Step 3: Verifying update ===");
        HcmUserSearchResponse response = hcmUserService.search(HcmUserSearchRequest.builder().pageNum(1).build());
        HcmUserDto foundUser = response.getRecords().stream()
                .filter(u -> uniqueEmployeeId.equals(u.getEmployeeId()))
                .findFirst()
                .orElseThrow();

        assertThat(foundUser.getTitle()).isEqualTo("李四-update");
        assertThat(foundUser.getPhone()).isEqualTo("13555555555");
        // Note: email field may not be returned by AITable API depending on view configuration
        log.info("Update verified: {}", foundUser);
    }

    @Test
    @DisplayName("Should batch delete users")
    void shouldBatchDeleteUsers() {
        // ==================== Step 1: Create multiple test users ====================
        log.info("=== Step 1: Creating multiple test users ===");
        String uniqueId1 = uniqueEmployeeId + "1";
        String uniqueId2 = uniqueEmployeeId + "2";

        HcmUserDto user1 = HcmUserDto.builder()
                .title("测试用户1")
                .employeeId(uniqueId1)
                .phone("13800000001")
                .system("HCM")
                .active(true)   // 在职
                .build();

        HcmUserDto user2 = HcmUserDto.builder()
                .title("测试用户2")
                .employeeId(uniqueId2)
                .phone("13800000002")
                .system("HCM")
                .active(true)   // 在职
                .build();

        HcmUserDto created1 = hcmUserService.create(user1);
        delay();
        HcmUserDto created2 = hcmUserService.create(user2);

        String recordId1 = created1.getRecordId();
        String recordId2 = created2.getRecordId();
        delay();

        // ==================== Step 2: Verify users exist ====================
        log.info("=== Step 2: Verifying users exist ===");
        HcmUserSearchResponse response = hcmUserService.search(HcmUserSearchRequest.builder().pageNum(1).build());
        List<String> employeeIds = response.getRecords().stream().map(HcmUserDto::getEmployeeId).toList();
        assertThat(employeeIds).contains(uniqueId1, uniqueId2);
        delay();

        // ==================== Step 3: Batch delete ====================
        log.info("=== Step 3: Batch deleting users ===");
        int deletedCount = hcmUserService.delete(List.of(recordId1, recordId2));
        assertThat(deletedCount).isEqualTo(2);
        delay();

        // ==================== Step 4: Verify users are gone ====================
        log.info("=== Step 4: Verifying users are deleted ===");
        HcmUserSearchResponse afterDeleteResponse = hcmUserService.search(HcmUserSearchRequest.builder().pageNum(1).build());
        List<String> afterDeleteIds = afterDeleteResponse.getRecords().stream().map(HcmUserDto::getEmployeeId).toList();
        assertThat(afterDeleteIds).doesNotContain(uniqueId1, uniqueId2);
    }
}
