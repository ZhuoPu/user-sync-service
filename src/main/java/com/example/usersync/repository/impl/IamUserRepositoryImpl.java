package com.example.usersync.repository.impl;

import com.example.usersync.dto.IamUserDto;
import com.example.usersync.dto.IamUserSearchRequest;
import com.example.usersync.dto.IamUserSearchResponse;
import com.example.usersync.repository.IamUserRepository;
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

import java.util.List;
import java.util.stream.Collectors;

/**
 * Repository implementation for IAM user data from AITable API.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class IamUserRepositoryImpl implements IamUserRepository {

    private final RestTemplate restTemplate;

    @Value("${app.iam.base-url:https://aitable.ai}")
    private String baseUrl;

    @Value("${app.iam.datasheet-id:dsthiermXySxq5drDV}")
    private String datasheetId;

    @Value("${app.iam.view-id:viwE79FRt4XA8}")
    private String viewId;

    @Value("${app.iam.api-token:uskuUFS0kLofBBVTuVUWKLT}")
    private String apiToken;

    private static final String TARGET_SYSTEM = "IAM";

    @Override
    public IamUserSearchResponse search(IamUserSearchRequest request) {
        String url = buildUrl(request);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Void> httpEntity = new HttpEntity<>(headers);

        log.info("Fetching IAM records from AITable: {}", url);
        AITableResponse response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                httpEntity,
                AITableResponse.class
        ).getBody();

        return mapToResponse(response);
    }

    @Override
    public IamUserDto create(IamUserDto user) {
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

        log.info("Creating IAM record: {}", user);
        AITableCreateResponse response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                httpEntity,
                AITableCreateResponse.class
        ).getBody();

        if (response != null && response.getData() != null && !response.getData().getRecords().isEmpty()) {
            return mapToIamUserDto(response.getData().getRecords().get(0));
        }
        throw new RuntimeException("Failed to create IAM user record");
    }

    @Override
    public IamUserDto update(IamUserDto user) {
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

        log.info("Updating IAM record: recordId={}, employeeId={}", user.getRecordId(), user.getEmployeeId());
        AITableUpdateResponse response = restTemplate.exchange(
                url,
                HttpMethod.PATCH,
                httpEntity,
                AITableUpdateResponse.class
        ).getBody();

        if (response != null && response.getData() != null && !response.getData().getRecords().isEmpty()) {
            return mapToIamUserDto(response.getData().getRecords().get(0));
        }
        throw new RuntimeException("Failed to update IAM user record");
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

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
        for (String recordId : recordIds) {
            builder.queryParam("recordIds", recordId);
        }
        url = builder.build().toUriString();

        HttpEntity<Void> httpEntity = new HttpEntity<>(headers);

        log.info("Deleting IAM records: {}", recordIds);
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

    @Override
    public IamUserDto resign(String recordId) {
        // First, get the current user to preserve data
        String url = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/fusion/v1/datasheets/{datasheetId}/records")
                .queryParam("viewId", viewId)
                .queryParam("fieldKey", "name")
                .queryParam("recordIds", recordId)
                .buildAndExpand(datasheetId)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Void> httpEntity = new HttpEntity<>(headers);

        log.info("Fetching IAM user before resign: recordId={}", recordId);
        AITableResponse getResponse = restTemplate.exchange(
                url,
                HttpMethod.GET,
                httpEntity,
                AITableResponse.class
        ).getBody();

        if (getResponse == null || getResponse.getData() == null || getResponse.getData().getRecords().isEmpty()) {
            throw new RuntimeException("User not found with recordId: " + recordId);
        }

        // Get the current user data
        AITableRecord existingRecord = getResponse.getData().getRecords().get(0);
        IamUserDto currentUser = mapToIamUserDto(existingRecord);

        // Prepare resign data: active=false and prefix phone with _
        IamUserDto resignUser = IamUserDto.builder()
                .recordId(recordId)
                .title(currentUser.getTitle())
                .employeeId(currentUser.getEmployeeId())
                .phone("_" + (currentUser.getPhone() != null ? currentUser.getPhone() : ""))
                .system(currentUser.getSystem())
                .email(currentUser.getEmail())
                .active(false)
                .build();

        // Update the record (the API response may not include all fields, so return our DTO)
        update(resignUser);
        return resignUser;
    }

    private String buildUrl(IamUserSearchRequest request) {
        Integer pageNum = request.getPageNum() != null ? request.getPageNum() : 1;
        return UriComponentsBuilder.fromUriString(baseUrl)
                .path("/fusion/v1/datasheets/{datasheetId}/records")
                .queryParam("viewId", viewId)
                .queryParam("fieldKey", "name")
                .queryParam("pageNum", pageNum)
                .buildAndExpand(datasheetId)
                .toUriString();
    }

    private IamUserSearchResponse mapToResponse(AITableResponse response) {
        if (response == null || response.getData() == null) {
            return IamUserSearchResponse.builder()
                    .total(0)
                    .pageNum(1)
                    .pageSize(0)
                    .records(List.of())
                    .build();
        }

        AITableData data = response.getData();

        // Filter records to only include IMA system in application layer
        List<IamUserDto> iamUsers = data.getRecords().stream()
                .filter(record -> TARGET_SYSTEM.equals(record.getFields().getSystem()))
                .map(this::mapToIamUserDto)
                .collect(Collectors.toList());

        return IamUserSearchResponse.builder()
                .total(iamUsers.size())
                .pageNum(data.getPageNum())
                .pageSize(data.getPageSize())
                .records(iamUsers)
                .build();
    }

    private IamUserDto mapToIamUserDto(AITableRecord record) {
        return IamUserDto.builder()
                .recordId(record.getRecordId())
                .title(record.getFields().getTitle())
                .employeeId(record.getFields().getEmployeeId())
                .phone(record.getFields().getPhone())
                .system(record.getFields().getSystem())
                .email(record.getFields().getEmail())
                .active(record.getFields().getActive())
                .build();
    }

    private AITableRecord toAITableRecord(IamUserDto user) {
        AITableRecord record = new AITableRecord();
        record.setRecordId(user.getRecordId());

        AITableFields fields = new AITableFields();
        fields.setTitle(user.getTitle());
        fields.setEmployeeId(user.getEmployeeId());
        fields.setPhone(user.getPhone());
        fields.setSystem(user.getSystem());
        fields.setEmail(user.getEmail());
        fields.setActive(user.getActive());

        record.setFields(fields);
        return record;
    }

    // ==================== AITable API Response/Request Classes ====================

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

    @Data
    public static class AITableRecord {
        @JsonProperty("recordId")
        private String recordId;

        @JsonProperty("fields")
        private AITableFields fields;
    }

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

        @JsonProperty("在职")
        private Boolean active;
    }

    @Data
    public static class AITableCreateRequest {
        @JsonProperty("records")
        private List<AITableRecord> records;
    }

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

    @Data
    public static class AITableCreateData {
        @JsonProperty("records")
        private List<AITableRecord> records;
    }

    @Data
    public static class AITableUpdateRequest {
        @JsonProperty("records")
        private List<AITableRecord> records;

        @JsonProperty("fieldKey")
        private String fieldKey;
    }

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

    @Data
    public static class AITableUpdateData {
        @JsonProperty("records")
        private List<AITableRecord> records;
    }

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

    @Data
    public static class AITableDeleteData {
        @JsonProperty("recordCount")
        private Integer recordCount;
    }
}
