package com.example.usersync.repository.impl;

import com.example.usersync.dto.HcmUserDto;
import com.example.usersync.dto.HcmUserSearchRequest;
import com.example.usersync.dto.HcmUserSearchResponse;
import com.example.usersync.repository.HcmUserRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Repository implementation for HCM user data from AITable API.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class HcmUserRepositoryImpl implements HcmUserRepository {

    private final RestTemplate restTemplate;

    @Value("${app.hcm.base-url:https://aitable.ai}")
    private String baseUrl;

    @Value("${app.hcm.datasheet-id:dsthiermXySxq5drDV}")
    private String datasheetId;

    @Value("${app.hcm.view-id:viwE79FRt4XA8}")
    private String viewId;

    @Value("${app.hcm.api-token:uskuUFS0kLofBBVTuVUWKLT}")
    private String apiToken;

    @Override
    public HcmUserSearchResponse search(HcmUserSearchRequest request) {
        String url = buildUrl(request);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Void> httpEntity = new HttpEntity<>(headers);

        log.info("Fetching HCM records from AITable: {}", url);
        AITableResponse response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                httpEntity,
                AITableResponse.class
        ).getBody();

        return mapToResponse(response);
    }

    @Override
    public HcmUserSearchResponse searchAll(HcmUserSearchRequest request) {
        String url = buildUrlWithoutFilter(request);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Void> httpEntity = new HttpEntity<>(headers);

        log.info("Fetching ALL records from AITable: {}", url);
        AITableResponse response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                httpEntity,
                AITableResponse.class
        ).getBody();

        return mapToResponse(response);
    }

    private String buildUrlWithoutFilter(HcmUserSearchRequest request) {
        Integer pageNum = request.getPageNum() != null ? request.getPageNum() : 1;
        return UriComponentsBuilder.fromUriString(baseUrl)
                .path("/fusion/v1/datasheets/{datasheetId}/records")
                .queryParam("viewId", viewId)
                .queryParam("fieldKey", "name")
                .queryParam("pageNum", pageNum)
                .buildAndExpand(datasheetId)
                .toUriString();
    }

    @Override
    public HcmUserDto create(HcmUserDto user) {
        String url = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/fusion/v1/datasheets/{datasheetId}/records")
                .queryParam("fieldKey", "name")
                .buildAndExpand(datasheetId)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        AITableCreateRequest request = new AITableCreateRequest();
        request.setRecords(List.of(toAITableRecord(user)));

        HttpEntity<AITableCreateRequest> httpEntity = new HttpEntity<>(request, headers);

        log.info("Creating HCM record: {}", user);
        AITableCreateResponse response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                httpEntity,
                AITableCreateResponse.class
        ).getBody();

        if (response != null && response.getData() != null && !response.getData().getRecords().isEmpty()) {
            return mapToHcmUserDto(response.getData().getRecords().get(0));
        }
        throw new RuntimeException("Failed to create HCM user record");
    }

    @Override
    public HcmUserDto update(HcmUserDto user) {
        String url = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/fusion/v1/datasheets/{datasheetId}/records")
                .queryParam("viewId", viewId)
                .queryParam("fieldKey", "name")
                .buildAndExpand(datasheetId)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        AITableUpdateRequest request = new AITableUpdateRequest();
        request.setRecords(List.of(toAITableRecord(user)));
        request.setFieldKey("name");

        HttpEntity<AITableUpdateRequest> httpEntity = new HttpEntity<>(request, headers);

        log.info("Updating HCM record: recordId={}, employeeId={}", user.getRecordId(), user.getEmployeeId());
        AITableUpdateResponse response = restTemplate.exchange(
                url,
                HttpMethod.PATCH,
                httpEntity,
                AITableUpdateResponse.class
        ).getBody();

        if (response != null && response.getData() != null && !response.getData().getRecords().isEmpty()) {
            return mapToHcmUserDto(response.getData().getRecords().get(0));
        }
        throw new RuntimeException("Failed to update HCM user record");
    }

    @Override
    public int delete(List<String> recordIds) {
        if (recordIds == null || recordIds.isEmpty()) {
            return 0;
        }

        String url = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/fusion/v1/datasheets/{datasheetId}/records")
                .buildAndExpand(datasheetId)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiToken);

        // Add recordIds as query parameters
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
        for (String recordId : recordIds) {
            builder.queryParam("recordIds", recordId);
        }
        url = builder.build().toUriString();

        HttpEntity<Void> httpEntity = new HttpEntity<>(headers);

        log.info("Deleting HCM records: {}", recordIds);
        ResponseEntity<AITableDeleteResponse> response = restTemplate.exchange(
                url,
                HttpMethod.DELETE,
                httpEntity,
                AITableDeleteResponse.class
        );

        if (response.getBody() != null && response.getBody().getData() != null) {
            return response.getBody().getData().getRecordCount();
        }
        return recordIds.size();
    }

    @Override
    public boolean delete(String recordId) {
        return delete(List.of(recordId)) > 0;
    }

    private String buildUrl(HcmUserSearchRequest request) {
        Integer pageNum = request.getPageNum() != null ? request.getPageNum() : 1;
        // Temporarily disable filterByFormula to debug
        // TODO: Re-enable after verifying filterByFormula syntax with AITable API

        return UriComponentsBuilder.fromUriString(baseUrl)
                .path("/fusion/v1/datasheets/{datasheetId}/records")
                .queryParam("viewId", viewId)
                .queryParam("fieldKey", "name")
                .queryParam("pageNum", pageNum)
                .buildAndExpand(datasheetId)
                .toUriString();
    }

    private HcmUserSearchResponse mapToResponse(AITableResponse response) {
        if (response == null || response.getData() == null) {
            return HcmUserSearchResponse.builder()
                    .total(0)
                    .pageNum(1)
                    .pageSize(0)
                    .records(List.of())
                    .build();
        }

        AITableData data = response.getData();

        // Filter records to only include HCM system in application layer
        // (filterByFormula parameter seems to have issues, so we filter in code)
        final String TARGET_SYSTEM = "HCM";
        List<HcmUserDto> hcmUsers = data.getRecords().stream()
                .filter(record -> TARGET_SYSTEM.equals(record.getFields().getSystem()))
                .map(this::mapToHcmUserDto)
                .collect(Collectors.toList());

        return HcmUserSearchResponse.builder()
                .total(hcmUsers.size())
                .pageNum(data.getPageNum())
                .pageSize(data.getPageSize())
                .records(hcmUsers)
                .build();
    }

    private HcmUserDto mapToHcmUserDto(AITableRecord record) {
        return HcmUserDto.builder()
                .recordId(record.getRecordId())
                .title(record.getFields().getTitle())
                .employeeId(record.getFields().getEmployeeId())
                .phone(record.getFields().getPhone())
                .system(record.getFields().getSystem())
                .email(record.getFields().getEmail())
                .build();
    }

    private AITableRecord toAITableRecord(HcmUserDto user) {
        AITableRecord record = new AITableRecord();
        record.setRecordId(user.getRecordId());

        AITableFields fields = new AITableFields();
        fields.setTitle(user.getTitle());
        fields.setEmployeeId(user.getEmployeeId());
        fields.setPhone(user.getPhone());
        fields.setSystem(user.getSystem());
        fields.setEmail(user.getEmail());

        record.setFields(fields);
        return record;
    }

    /**
     * AITable API response wrapper.
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

    // ==================== CRUD Request/Response Classes ====================

    /**
     * Request to create records.
     */
    @Data
    public static class AITableCreateRequest {
        @JsonProperty("records")
        private List<AITableRecord> records;
    }

    /**
     * Response from create operation.
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
     * Request to update records.
     */
    @Data
    public static class AITableUpdateRequest {
        @JsonProperty("records")
        private List<AITableRecord> records;

        @JsonProperty("fieldKey")
        private String fieldKey;
    }

    /**
     * Response from update operation.
     */
    @Data
    public static class AITableUpdateResponse {
        @JsonProperty("code")
        private Integer code;

        @JsonProperty("success")
        private Boolean success;

        @JsonProperty("message")
        private String message;

        @JsonProperty("data")
        private AITableUpdateData data;
    }

    /**
     * Data container for update response.
     */
    @Data
    public static class AITableUpdateData {
        @JsonProperty("records")
        private List<AITableRecord> records;
    }

    /**
     * Response from delete operation.
     */
    @Data
    public static class AITableDeleteResponse {
        @JsonProperty("code")
        private Integer code;

        @JsonProperty("success")
        private Boolean success;

        @JsonProperty("message")
        private String message;

        @JsonProperty("data")
        private AITableDeleteData data;
    }

    /**
     * Data container for delete response.
     */
    @Data
    public static class AITableDeleteData {
        @JsonProperty("recordCount")
        private Integer recordCount;
    }
}
