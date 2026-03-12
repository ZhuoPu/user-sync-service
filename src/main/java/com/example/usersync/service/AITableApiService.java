package com.example.usersync.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Service for interacting with AITable API.
 * Used for syncing user data between HCM and IMA systems.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AITableApiService {

    private final RestTemplate restTemplate;

    @Value("${app.aitable.base-url:https://aitable.ai}")
    private String baseUrl;

    @Value("${app.aitable.datasheet-id:dsthiermXySxq5drDV}")
    private String datasheetId;

    @Value("${app.aitable.view-id:viwE79FRt4XA8}")
    private String viewId;

    @Value("${app.aitable.api-token:uskuUFS0kLofBBVTuVUWKLT}")
    private String apiToken;

    @Value("${app.aitable.target-system:IMA}")
    private String targetSystem;

    /**
     * Fetch records from AITable for the configured view.
     *
     * @return AITable response containing records
     */
    public AITableResponse fetchRecords() {
        String url = String.format("%s/fusion/v1/datasheets/%s/records?viewId=%s&fieldKey=name",
                baseUrl, datasheetId, viewId);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        log.info("Fetching records from AITable: {}", url);
        ResponseEntity<AITableResponse> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                AITableResponse.class
        );

        return response.getBody();
    }

    /**
     * Check if a record exists for the given employee ID (工号) and target system.
     *
     * @param employeeId The employee ID to search for
     * @return true if a record exists for the target system, false otherwise
     */
    public boolean existsRecordForSystem(String employeeId) {
        AITableResponse response = fetchRecords();

        if (response == null || response.getData() == null) {
            return false;
        }

        return response.getData().getRecords().stream()
                .anyMatch(record -> {
                    String system = record.getFields().getSystem();
                    String empId = record.getFields().getEmployeeId();
                    return targetSystem.equals(system) && employeeId.equals(empId);
                });
    }

    /**
     * Find a record by employee ID and target system.
     *
     * @param employeeId The employee ID to search for
     * @return The matching record, or null if not found
     */
    public AITableRecord findRecordByEmployeeId(String employeeId) {
        AITableResponse response = fetchRecords();

        if (response == null || response.getData() == null) {
            return null;
        }

        return response.getData().getRecords().stream()
                .filter(record -> targetSystem.equals(record.getFields().getSystem())
                        && employeeId.equals(record.getFields().getEmployeeId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Create a new record in AITable for the target system.
     *
     * @param userDto The user data to create
     * @return The created record
     */
    public AITableRecord createRecord(AITableUserDto userDto) {
        String url = String.format("%s/fusion/v1/datasheets/%s/records", baseUrl, datasheetId);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        AITableCreateRequest request = new AITableCreateRequest();
        request.setRecords(List.of(new AITableRecordRequest(userDto, targetSystem)));

        HttpEntity<AITableCreateRequest> entity = new HttpEntity<>(request, headers);

        log.info("Creating record in AITable for user: {}, system: {}", userDto.getEmployeeId(), targetSystem);
        ResponseEntity<AITableCreateResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                AITableCreateResponse.class
        );

        return response.getBody().getData().getRecords().get(0);
    }

    /**
     * Response wrapper from AITable API.
     */
    @Data
    public static class AITableResponse {
        @JsonProperty("code")
        private Integer code;

        @JsonProperty("success")
        private Boolean success;

        @JsonProperty("message")
        private String message;

        @JsonProperty("data")
        private AITableData data;
    }

    /**
     * Data container in AITable response.
     */
    @Data
    public static class AITableData {
        @JsonProperty("total")
        private Integer total;

        @JsonProperty("pageNum")
        private Integer pageNum;

        @JsonProperty("pageSize")
        private Integer pageSize;

        @JsonProperty("records")
        private List<AITableRecord> records;
    }

    /**
     * AITable record representation.
     */
    @Data
    public static class AITableRecord {
        @JsonProperty("recordId")
        private String recordId;

        @JsonProperty("fields")
        private AITableFields fields;
    }

    /**
     * Fields in an AITable record.
     */
    @Data
    public static class AITableFields {
        @JsonProperty("Title")
        private String title;

        @JsonProperty("工号")
        private String employeeId;

        @JsonProperty("电话")
        private String phone;

        @JsonProperty("系统")
        private String system;

        @JsonProperty("邮箱")
        private String email;
    }

    /**
     * Request to create records in AITable.
     */
    @Data
    public static class AITableCreateRequest {
        @JsonProperty("records")
        private List<AITableRecordRequest> records;
    }

    /**
     * Single record request for creation.
     */
    @Data
    public static class AITableRecordRequest {
        @JsonProperty("fields")
        private AITableFields fields;

        public AITableRecordRequest(AITableUserDto userDto, String system) {
            this.fields = new AITableFields();
            this.fields.setTitle(userDto.getName());
            this.fields.setEmployeeId(userDto.getEmployeeId());
            this.fields.setPhone(userDto.getPhone());
            this.fields.setEmail(userDto.getEmail());
            this.fields.setSystem(system);
        }
    }

    /**
     * Response from AITable after record creation.
     */
    @Data
    public static class AITableCreateResponse {
        @JsonProperty("code")
        private Integer code;

        @JsonProperty("success")
        private Boolean success;

        @JsonProperty("message")
        private String message;

        @JsonProperty("data")
        private AITableCreateData data;
    }

    /**
     * Data container for create response.
     */
    @Data
    public static class AITableCreateData {
        @JsonProperty("records")
        private List<AITableRecord> records;
    }

    /**
     * DTO for user data to sync to AITable.
     */
    @Data
    public static class AITableUserDto {
        private String name;
        private String employeeId;
        private String phone;
        private String email;
    }
}
