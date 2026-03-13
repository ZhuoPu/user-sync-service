package com.example.usersync.integration;

import com.example.usersync.dto.HcmUserSearchRequest;
import com.example.usersync.dto.HcmUserSearchResponse;
import com.example.usersync.service.HcmUserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simple integration test to verify HCM user search works with real AITable API.
 */
@SpringBootTest
@DisplayName("HCM User Search Integration Test")
class HcmUserSearchIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(HcmUserSearchIntegrationTest.class);

    @Autowired
    private HcmUserService hcmUserService;

    @Test
    @DisplayName("Should search HCM users from AITable")
    void shouldSearchHcmUsers_fromAitTable() {
        // Given
        HcmUserSearchRequest request = HcmUserSearchRequest.builder()
                .pageNum(1)
                .build();

        // When
        log.info("Searching HCM users...");
        HcmUserSearchResponse response = hcmUserService.search(request);

        // Then
        log.info("Search result: total={}, pageSize={}, records count={}",
                response.getTotal(), response.getPageSize(), response.getRecords().size());

        assertThat(response).isNotNull();
        assertThat(response.getPageNum()).isEqualTo(1);
        assertThat(response.getRecords()).isNotNull();

        // Log all records
        response.getRecords().forEach(user ->
            log.info("Found user: recordId={}, title={}, employeeId={}, system={}",
                    user.getRecordId(), user.getTitle(), user.getEmployeeId(), user.getSystem())
        );

        // All returned users should be HCM system
        response.getRecords().forEach(user ->
            assertThat(user.getSystem()).isEqualTo("HCM")
        );
    }
}
