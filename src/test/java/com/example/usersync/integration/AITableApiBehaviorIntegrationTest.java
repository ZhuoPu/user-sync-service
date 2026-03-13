package com.example.usersync.integration;

import com.example.usersync.dto.HcmUserDto;
import com.example.usersync.dto.HcmUserSearchRequest;
import com.example.usersync.dto.HcmUserSearchResponse;
import com.example.usersync.repository.HcmUserRepository;
import com.example.usersync.repository.impl.HcmUserRepositoryImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test to verify AITable API behavior.
 */
@SpringBootTest
@DisplayName("AITable API Behavior Integration Test")
class AITableApiBehaviorIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(AITableApiBehaviorIntegrationTest.class);

    @Autowired
    private HcmUserRepository hcmUserRepository;

    @Value("${app.hcm.test.employee-id:TEST999}")
    private String testEmployeeId;

    private String createdRecordId;

    @AfterEach
    void tearDown() {
        if (createdRecordId != null) {
            try {
                log.info("Cleaning up: deleting recordId={}", createdRecordId);
                hcmUserRepository.delete(createdRecordId);
            } catch (Exception e) {
                log.warn("Failed to cleanup: {}", e.getMessage());
            }
        }
    }

    @Test
    @DisplayName("Should test basic AITable API behavior")
    void shouldTestBasicAitTableApiBehavior() {
        // ==================== Step 1: Search without filter ====================
        log.info("=== Step 1: Search WITHOUT filter (searchAll) ===");
        searchAllRecords();

        // ==================== Step 2: Create a record ====================
        log.info("=== Step 2: Create record ===");
        HcmUserDto user = HcmUserDto.builder()
                .title("测试用户")
                .employeeId(testEmployeeId)
                .phone("13900000000")
                .system("HCM")
                .email("test@example.com")
                .build();

        HcmUserDto created = hcmUserRepository.create(user);
        createdRecordId = created.getRecordId();
        log.info("Created: recordId={}, employeeId={}, system={}",
                createdRecordId, created.getEmployeeId(), created.getSystem());
        assertThat(createdRecordId).isNotNull();

        // Wait for indexing
        try { Thread.sleep(3000); } catch (InterruptedException e) {}

        // ==================== Step 3: Search all records (no filter) ====================
        log.info("=== Step 3: Search all records after create ===");
        searchAllRecords();

        // ==================== Step 4: Search with HCM filter ====================
        log.info("=== Step 4: Search WITH HCM filter ===");
        HcmUserSearchResponse response = hcmUserRepository.search(
                HcmUserSearchRequest.builder().pageNum(1).build());
        log.info("HCM filter result: {} records", response.getRecords().size());
        response.getRecords().forEach(r -> log.info("  - recordId={}, employeeId={}, system={}",
                r.getRecordId(), r.getEmployeeId(), r.getSystem()));
    }

    @Test
    @DisplayName("Should test create and delete operations")
    void shouldTestCreateAndDelete() {
        // Create
        HcmUserDto user = HcmUserDto.builder()
                .title("测试用户")
                .employeeId(testEmployeeId)
                .phone("13900000000")
                .system("HCM")
                .build();

        HcmUserDto created = hcmUserRepository.create(user);
        createdRecordId = created.getRecordId();
        log.info("Created: recordId={}", createdRecordId);
        assertThat(createdRecordId).isNotNull();

        // Wait and search
        try { Thread.sleep(3000); } catch (InterruptedException e) {}

        // Delete
        boolean deleted = hcmUserRepository.delete(createdRecordId);
        log.info("Deleted: {}", deleted);
        assertThat(deleted).isTrue();
    }

    private void searchAllRecords() {
        HcmUserSearchResponse response = hcmUserRepository.searchAll(
                HcmUserSearchRequest.builder().pageNum(1).build());
        log.info("searchAll result: {} records (total={})", response.getRecords().size(), response.getTotal());
        response.getRecords().forEach(r -> log.info("  - recordId={}, employeeId={}, system={}, title={}",
                r.getRecordId(), r.getEmployeeId(), r.getSystem(), r.getTitle()));
    }
}
