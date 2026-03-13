package com.example.usersync.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static java.lang.Thread.sleep;

/**
 * End-to-End Test for User Sync Service with AITable API.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=kafka01.dev.jereh.cn:9092"
})
@DisplayName("User Sync E2E Tests")
class UserSyncE2ETest {

    private static final String AITABLE_BASE_URL = "https://aitable.ai";
    private static final String DATASHEET_ID = "dsthiermXySxq5drDV";
    private static final String VIEW_ID = "viwE79FRt4XA8";
    private static final String API_TOKEN = "uskuUFS0kLofBBVTuVUWKLT";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
    }

    @Test
    @DisplayName("Should fetch all records from AITable")
    void shouldFetchAllRecords_fromAITable() throws InterruptedException {
        // Delay to avoid rate limit (2 QPS)
        sleep(1000);

        // Given - Prepare request headers
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(API_TOKEN);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        // When - Call GET API
        String url = String.format("%s/fusion/v1/datasheets/%s/records?viewId=%s&fieldKey=name",
                AITABLE_BASE_URL, DATASHEET_ID, VIEW_ID);

        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                String.class
        );

        // Then - Verify response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode root = parseJson(response.getBody());
        assertThat(root.path("code").asInt()).isEqualTo(200);
        assertThat(root.path("success").asBoolean()).isTrue();

        JsonNode records = root.path("data").path("records");
        int total = root.path("data").path("total").asInt();

        System.out.println("Total records: " + total);
        records.forEach(record -> {
            JsonNode fields = record.path("fields");
            String title = fields.path("Title").asText();
            String empId = fields.path("工号").asText();
            String phone = fields.path("电话").asText();
            String system = fields.path("系统").asText();
            System.out.println(String.format("  - %s | %s | %s | %s", title, empId, phone, system));
        });
    }

    @Test
    @DisplayName("Should add a new record to AITable")
    void shouldAddNewRecord_toAITable() throws InterruptedException {
        // Delay to avoid rate limit (2 QPS)
        sleep(1000);

        // Given - Prepare new record
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(API_TOKEN);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> newRecord = Map.of(
                "fields", Map.of(
                        "Title", "李四",
                        "工号", "004",
                        "电话", "13444444444",
                        "系统", "HCM"
                )
        );

        Map<String, Object> requestBody = Map.of(
                "records", List.of(newRecord)
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        // When - Call POST API
        String url = String.format("%s/fusion/v1/datasheets/%s/records?fieldKey=name",
                AITABLE_BASE_URL, DATASHEET_ID);

        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                String.class
        );

        // Then - Verify response (AITable returns 201 for successful creation)
        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CREATED);

        JsonNode root = parseJson(response.getBody());
        assertThat(root.path("code").asInt()).isEqualTo(200);
        assertThat(root.path("success").asBoolean()).isTrue();

        JsonNode createdRecords = root.path("data").path("records");
        assertThat(createdRecords.isArray()).isTrue();
        assertThat(createdRecords.size()).isGreaterThan(0);

        String recordId = createdRecords.get(0).path("recordId").asText();
        System.out.println("Created record ID: " + recordId);
        System.out.println("Response: " + response.getBody());
    }

    @Test
    @DisplayName("E2E: Fetch records and add new record")
    void e2eTest_fetchRecordsAndAddNew() throws InterruptedException {
        // Delay to avoid rate limit (2 QPS)
        sleep(1000);

        // First - Fetch existing records
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(API_TOKEN);
        headers.setContentType(MediaType.APPLICATION_JSON);

        String getUrl = String.format("%s/fusion/v1/datasheets/%s/records?viewId=%s&fieldKey=name",
                AITABLE_BASE_URL, DATASHEET_ID, VIEW_ID);

        ResponseEntity<String> getResponse = restTemplate.exchange(
                getUrl,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        int totalBefore = parseJson(getResponse.getBody()).path("data").path("total").asInt();
        System.out.println("Records before: " + totalBefore);

        // Then - Add new record
        Map<String, Object> newRecord = Map.of(
                "fields", Map.of(
                        "Title", "李四",
                        "工号", "004",
                        "电话", "13444444444",
                        "系统", "HCM"
                )
        );

        Map<String, Object> postBody = Map.of("records", List.of(newRecord));

        String postUrl = String.format("%s/fusion/v1/datasheets/%s/records?fieldKey=name",
                AITABLE_BASE_URL, DATASHEET_ID);

        ResponseEntity<String> postResponse = restTemplate.exchange(
                postUrl,
                HttpMethod.POST,
                new HttpEntity<>(postBody, headers),
                String.class
        );

        assertThat(postResponse.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CREATED);
        assertThat(parseJson(postResponse.getBody()).path("success").asBoolean()).isTrue();

        // Delay to avoid rate limit (2 QPS)
        try {
            sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Finally - Verify count increased
        ResponseEntity<String> verifyResponse = restTemplate.exchange(
                getUrl,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        int totalAfter = parseJson(verifyResponse.getBody()).path("data").path("total").asInt();
        System.out.println("Records after: " + totalAfter);
        assertThat(totalAfter).isEqualTo(totalBefore + 1);
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON", e);
        }
    }
}
