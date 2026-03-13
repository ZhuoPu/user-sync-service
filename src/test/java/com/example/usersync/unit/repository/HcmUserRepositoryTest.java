package com.example.usersync.unit.repository;

import com.example.usersync.dto.HcmUserDto;
import com.example.usersync.dto.HcmUserSearchRequest;
import com.example.usersync.dto.HcmUserSearchResponse;
import com.example.usersync.repository.impl.HcmUserRepositoryImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
@DisplayName("HcmUserRepository Unit Tests")
class HcmUserRepositoryTest {

    private RestTemplate restTemplate;

    private HcmUserRepositoryImpl hcmUserRepository;

    private static final String BASE_URL = "https://aitable.ai";
    private static final String DATASHEET_ID = "dsthiermXySxq5drDV";
    private static final String VIEW_ID = "viwE79FRt4XA8";
    private static final String API_TOKEN = "test-token";

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        hcmUserRepository = new HcmUserRepositoryImpl(restTemplate);
        ReflectionTestUtils.setField(hcmUserRepository, "baseUrl", BASE_URL);
        ReflectionTestUtils.setField(hcmUserRepository, "datasheetId", DATASHEET_ID);
        ReflectionTestUtils.setField(hcmUserRepository, "viewId", VIEW_ID);
        ReflectionTestUtils.setField(hcmUserRepository, "apiToken", API_TOKEN);
    }

    @AfterEach
    void tearDown() {
        reset(restTemplate);
    }

    @Nested
    @DisplayName("search Tests")
    class SearchTests {

        @Test
        @DisplayName("Should send GET request to correct URL with pageNum parameter")
        void shouldSendGetRequest_toCorrectUrl_withPageNum() {
            // Given
            HcmUserSearchRequest request = HcmUserSearchRequest.builder()
                    .pageNum(1)
                    .build();

            mockSuccessfulResponse();

            ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);

            // When
            hcmUserRepository.search(request);

            // Then
            verify(restTemplate).exchange(
                    urlCaptor.capture(),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(HcmUserRepositoryImpl.AITableResponse.class)
            );

            String actualUrl = urlCaptor.getValue();
            assertThat(actualUrl).contains(BASE_URL);
            assertThat(actualUrl).contains("/fusion/v1/datasheets/" + DATASHEET_ID + "/records");
            assertThat(actualUrl).contains("viewId=" + VIEW_ID);
            assertThat(actualUrl).contains("fieldKey=name");
            assertThat(actualUrl).contains("pageNum=1");
            // filterByFormula temporarily disabled, filtering done in application layer
        }

        @Test
        @DisplayName("Should default to pageNum 1 when not provided")
        void shouldDefaultToPageNum1_whenNotProvided() {
            // Given
            HcmUserSearchRequest request = HcmUserSearchRequest.builder().build();

            mockSuccessfulResponse();

            ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);

            // When
            hcmUserRepository.search(request);

            // Then
            verify(restTemplate).exchange(
                    urlCaptor.capture(),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(HcmUserRepositoryImpl.AITableResponse.class)
            );

            String actualUrl = urlCaptor.getValue();
            assertThat(actualUrl).contains("pageNum=1");
        }

        @Test
        @DisplayName("Should include Bearer token in Authorization header")
        void shouldIncludeBearerToken_inAuthorizationHeader() {
            // Given
            HcmUserSearchRequest request = HcmUserSearchRequest.builder()
                    .pageNum(1)
                    .build();

            mockSuccessfulResponse();

            ArgumentCaptor<HttpEntity<?>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);

            // When
            hcmUserRepository.search(request);

            // Then
            verify(restTemplate).exchange(
                    any(String.class),
                    eq(HttpMethod.GET),
                    entityCaptor.capture(),
                    eq(HcmUserRepositoryImpl.AITableResponse.class)
            );

            HttpEntity<?> capturedEntity = entityCaptor.getValue();
            String authHeader = capturedEntity.getHeaders().getFirst(org.springframework.http.HttpHeaders.AUTHORIZATION);
            assertThat(authHeader).isNotNull();
            assertThat(authHeader).isEqualTo("Bearer " + API_TOKEN);
        }

        @Test
        @DisplayName("Should return HCM system users from API")
        void shouldReturnHcmSystemUsers_fromApi() {
            // Given
            HcmUserSearchRequest request = HcmUserSearchRequest.builder()
                    .pageNum(1)
                    .build();

            mockResponseWithHcmUsersOnly();

            // When
            HcmUserSearchResponse response = hcmUserRepository.search(request);

            // Then
            assertThat(response.getRecords()).hasSize(2);
            assertThat(response.getRecords()).allMatch(record -> "HCM".equals(record.getSystem()));
        }

        @Test
        @DisplayName("Should map AITable response fields to HcmUserDto correctly")
        void shouldMapFields_correctly() {
            // Given
            HcmUserSearchRequest request = HcmUserSearchRequest.builder()
                    .pageNum(1)
                    .build();

            mockResponseWithHcmUsers();

            // When
            HcmUserSearchResponse response = hcmUserRepository.search(request);

            // Then
            assertThat(response.getRecords()).hasSize(1);
            HcmUserDto user = response.getRecords().get(0);
            assertThat(user.getRecordId()).isEqualTo("rec123");
            assertThat(user.getTitle()).isEqualTo("John Doe");
            assertThat(user.getEmployeeId()).isEqualTo("EMP001");
            assertThat(user.getPhone()).isEqualTo("1234567890");
            assertThat(user.getSystem()).isEqualTo("HCM");
            assertThat(user.getEmail()).isEqualTo("john.doe@example.com");
        }

        @Test
        @DisplayName("Should handle null response gracefully")
        void shouldHandleNullResponse_gracefully() {
            // Given
            HcmUserSearchRequest request = HcmUserSearchRequest.builder()
                    .pageNum(1)
                    .build();

            when(restTemplate.exchange(
                    any(String.class),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(HcmUserRepositoryImpl.AITableResponse.class)
            )).thenReturn(ResponseEntity.ok(null));

            // When
            HcmUserSearchResponse response = hcmUserRepository.search(request);

            // Then
            assertThat(response.getTotal()).isEqualTo(0);
            assertThat(response.getRecords()).isEmpty();
        }
    }

    @Nested
    @DisplayName("create Tests")
    class CreateTests {

        @Test
        @DisplayName("Should send POST request to correct URL")
        void shouldSendPostRequest_toCorrectUrl() {
            // Given
            HcmUserDto user = HcmUserDto.builder()
                    .title("李四")
                    .employeeId("004")
                    .phone("13444444444")
                    .system("HCM")
                    .build();

            mockSuccessfulCreateResponse("recNew123");

            ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<HttpEntity<?>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);

            // When
            hcmUserRepository.create(user);

            // Then
            verify(restTemplate).exchange(
                    urlCaptor.capture(),
                    eq(HttpMethod.POST),
                    entityCaptor.capture(),
                    eq(HcmUserRepositoryImpl.AITableCreateResponse.class)
            );

            String actualUrl = urlCaptor.getValue();
            assertThat(actualUrl).contains(BASE_URL);
            assertThat(actualUrl).contains("/fusion/v1/datasheets/" + DATASHEET_ID + "/records");
            assertThat(actualUrl).contains("fieldKey=name");
        }

        @Test
        @DisplayName("Should send correct request body for create")
        void shouldSendCorrectRequestBody_forCreate() {
            // Given
            HcmUserDto user = HcmUserDto.builder()
                    .title("李四")
                    .employeeId("004")
                    .phone("13444444444")
                    .system("HCM")
                    .email("lisi@example.com")
                    .build();

            mockSuccessfulCreateResponse("recNew123");

            ArgumentCaptor<HttpEntity<?>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);

            // When
            hcmUserRepository.create(user);

            // Then
            verify(restTemplate).exchange(
                    any(String.class),
                    eq(HttpMethod.POST),
                    entityCaptor.capture(),
                    eq(HcmUserRepositoryImpl.AITableCreateResponse.class)
            );

            HttpEntity<?> capturedEntity = entityCaptor.getValue();
            assertThat(capturedEntity.getHeaders().getContentType().toString()).contains("application/json");
        }

        @Test
        @DisplayName("Should return created user with recordId")
        void shouldReturnCreatedUser_withRecordId() {
            // Given
            HcmUserDto user = HcmUserDto.builder()
                    .title("李四")
                    .employeeId("004")
                    .phone("13444444444")
                    .system("HCM")
                    .build();

            mockSuccessfulCreateResponse("recNew123");

            // When
            HcmUserDto created = hcmUserRepository.create(user);

            // Then
            assertThat(created.getRecordId()).isEqualTo("recNew123");
            assertThat(created.getTitle()).isEqualTo("李四");
            assertThat(created.getEmployeeId()).isEqualTo("004");
            assertThat(created.getPhone()).isEqualTo("13444444444");
            assertThat(created.getSystem()).isEqualTo("HCM");
        }
    }

    @Nested
    @DisplayName("update Tests")
    class UpdateTests {

        @Test
        @DisplayName("Should send PATCH request to correct URL")
        void shouldSendPatchRequest_toCorrectUrl() {
            // Given
            HcmUserDto user = HcmUserDto.builder()
                    .recordId("recv0mmI1d6ax")
                    .title("李四")
                    .employeeId("004")
                    .phone("13444444444")
                    .system("HCM")
                    .build();

            mockSuccessfulUpdateResponse();

            ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);

            // When
            hcmUserRepository.update(user);

            // Then
            verify(restTemplate).exchange(
                    urlCaptor.capture(),
                    eq(HttpMethod.PATCH),
                    any(HttpEntity.class),
                    eq(HcmUserRepositoryImpl.AITableUpdateResponse.class)
            );

            String actualUrl = urlCaptor.getValue();
            assertThat(actualUrl).contains(BASE_URL);
            assertThat(actualUrl).contains("/fusion/v1/datasheets/" + DATASHEET_ID + "/records");
            assertThat(actualUrl).contains("viewId=" + VIEW_ID);
            assertThat(actualUrl).contains("fieldKey=name");
        }

        @Test
        @DisplayName("Should include recordId in request body for update")
        void shouldIncludeRecordId_inRequestBody() {
            // Given
            HcmUserDto user = HcmUserDto.builder()
                    .recordId("recv0mmI1d6ax")
                    .title("李四")
                    .employeeId("004")
                    .phone("13444444444")
                    .system("HCM")
                    .build();

            mockSuccessfulUpdateResponse();

            // When
            HcmUserDto updated = hcmUserRepository.update(user);

            // Then
            assertThat(updated.getRecordId()).isEqualTo("recv0mmI1d6ax");
        }
    }

    @Nested
    @DisplayName("delete Tests")
    class DeleteTests {

        @Test
        @DisplayName("Should send DELETE request with single recordId")
        void shouldSendDeleteRequest_withSingleRecordId() {
            // Given
            String recordId = "recv0mmI1d6ax";
            mockSuccessfulDeleteResponse(1);

            ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);

            // When
            hcmUserRepository.delete(recordId);

            // Then
            verify(restTemplate).exchange(
                    urlCaptor.capture(),
                    eq(HttpMethod.DELETE),
                    any(HttpEntity.class),
                    eq(HcmUserRepositoryImpl.AITableDeleteResponse.class)
            );

            String actualUrl = urlCaptor.getValue();
            assertThat(actualUrl).contains("recordIds=" + recordId);
        }

        @Test
        @DisplayName("Should send DELETE request with multiple recordIds")
        void shouldSendDeleteRequest_withMultipleRecordIds() {
            // Given
            List<String> recordIds = List.of("recv0mmI1d6ax", "rec1HWFVYAG2z");
            mockSuccessfulDeleteResponse(2);

            ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);

            // When
            hcmUserRepository.delete(recordIds);

            // Then
            verify(restTemplate).exchange(
                    urlCaptor.capture(),
                    eq(HttpMethod.DELETE),
                    any(HttpEntity.class),
                    eq(HcmUserRepositoryImpl.AITableDeleteResponse.class)
            );

            String actualUrl = urlCaptor.getValue();
            assertThat(actualUrl).contains("recordIds=recv0mmI1d6ax");
            assertThat(actualUrl).contains("recordIds=rec1HWFVYAG2z");
        }

        @Test
        @DisplayName("Should return true when delete single record succeeds")
        void shouldReturnTrue_whenDeleteSingleRecordSucceeds() {
            // Given
            String recordId = "recv0mmI1d6ax";
            mockSuccessfulDeleteResponse(1);

            // When
            boolean result = hcmUserRepository.delete(recordId);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should return count of deleted records")
        void shouldReturnCount_ofDeletedRecords() {
            // Given
            List<String> recordIds = List.of("recv0mmI1d6ax", "rec1HWFVYAG2z");
            mockSuccessfulDeleteResponse(2);

            // When
            int count = hcmUserRepository.delete(recordIds);

            // Then
            assertThat(count).isEqualTo(2);
        }

        @Test
        @DisplayName("Should return 0 when recordIds list is empty")
        void shouldReturn0_whenRecordIdsListIsEmpty() {
            // Given
            List<String> emptyList = List.of();

            // When
            int count = hcmUserRepository.delete(emptyList);

            // Then
            assertThat(count).isEqualTo(0);
            verifyNoInteractions(restTemplate);
        }

        @Test
        @DisplayName("Should return 0 when recordIds list is null")
        void shouldReturn0_whenRecordIdsListIsNull() {
            // When
            int count = hcmUserRepository.delete((List<String>) null);

            // Then
            assertThat(count).isEqualTo(0);
            verifyNoInteractions(restTemplate);
        }
    }

    private void mockSuccessfulResponse() {
        HcmUserRepositoryImpl.AITableResponse response = new HcmUserRepositoryImpl.AITableResponse();
        HcmUserRepositoryImpl.AITableData data = new HcmUserRepositoryImpl.AITableData();
        data.setRecords(List.of());
        response.setData(data);

        when(restTemplate.exchange(
                any(String.class),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(HcmUserRepositoryImpl.AITableResponse.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));
    }

    private void mockResponseWithHcmUsersOnly() {
        HcmUserRepositoryImpl.AITableResponse response = new HcmUserRepositoryImpl.AITableResponse();
        HcmUserRepositoryImpl.AITableData data = new HcmUserRepositoryImpl.AITableData();
        data.setTotal(2);
        data.setPageNum(1);
        data.setPageSize(100);

        // API returns only HCM records (filtered by filterByFormula)
        HcmUserRepositoryImpl.AITableRecord hcmRecord1 = createRecord("rec1", "John Doe", "EMP001", "123", "HCM", "john@example.com");
        HcmUserRepositoryImpl.AITableRecord hcmRecord2 = createRecord("rec2", "Jane Smith", "EMP002", "456", "HCM", "jane@example.com");

        data.setRecords(List.of(hcmRecord1, hcmRecord2));
        response.setData(data);

        when(restTemplate.exchange(
                any(String.class),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(HcmUserRepositoryImpl.AITableResponse.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));
    }

    private void mockResponseWithHcmUsers() {
        HcmUserRepositoryImpl.AITableResponse response = new HcmUserRepositoryImpl.AITableResponse();
        HcmUserRepositoryImpl.AITableData data = new HcmUserRepositoryImpl.AITableData();
        data.setPageNum(1);
        data.setPageSize(100);

        HcmUserRepositoryImpl.AITableRecord record = createRecord("rec123", "John Doe", "EMP001", "1234567890", "HCM", "john.doe@example.com");
        data.setRecords(List.of(record));
        response.setData(data);

        when(restTemplate.exchange(
                any(String.class),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(HcmUserRepositoryImpl.AITableResponse.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));
    }

    private HcmUserRepositoryImpl.AITableRecord createRecord(String recordId, String title, String employeeId,
                                                             String phone, String system, String email) {
        HcmUserRepositoryImpl.AITableRecord record = new HcmUserRepositoryImpl.AITableRecord();
        record.setRecordId(recordId);

        HcmUserRepositoryImpl.AITableFields fields = new HcmUserRepositoryImpl.AITableFields();
        fields.setTitle(title);
        fields.setEmployeeId(employeeId);
        fields.setPhone(phone);
        fields.setSystem(system);
        fields.setEmail(email);

        record.setFields(fields);
        return record;
    }

    // ==================== Mock Methods for CRUD Tests ====================

    private void mockSuccessfulCreateResponse(String newRecordId) {
        HcmUserRepositoryImpl.AITableCreateResponse response = new HcmUserRepositoryImpl.AITableCreateResponse();
        response.setSuccess(true);
        response.setCode(200);

        HcmUserRepositoryImpl.AITableCreateData data = new HcmUserRepositoryImpl.AITableCreateData();
        HcmUserRepositoryImpl.AITableRecord record = createRecord(newRecordId, "李四", "004", "13444444444", "HCM", null);
        data.setRecords(List.of(record));
        response.setData(data);

        when(restTemplate.exchange(
                any(String.class),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(HcmUserRepositoryImpl.AITableCreateResponse.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));
    }

    private void mockSuccessfulUpdateResponse() {
        HcmUserRepositoryImpl.AITableUpdateResponse response = new HcmUserRepositoryImpl.AITableUpdateResponse();
        response.setSuccess(true);
        response.setCode(200);

        HcmUserRepositoryImpl.AITableUpdateData data = new HcmUserRepositoryImpl.AITableUpdateData();
        HcmUserRepositoryImpl.AITableRecord record = createRecord("recv0mmI1d6ax", "李四", "004", "13444444444", "HCM", null);
        data.setRecords(List.of(record));
        response.setData(data);

        when(restTemplate.exchange(
                any(String.class),
                eq(HttpMethod.PATCH),
                any(HttpEntity.class),
                eq(HcmUserRepositoryImpl.AITableUpdateResponse.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));
    }

    private void mockSuccessfulDeleteResponse(int count) {
        HcmUserRepositoryImpl.AITableDeleteResponse response = new HcmUserRepositoryImpl.AITableDeleteResponse();
        response.setSuccess(true);
        response.setCode(200);

        HcmUserRepositoryImpl.AITableDeleteData data = new HcmUserRepositoryImpl.AITableDeleteData();
        data.setRecordCount(count);
        response.setData(data);

        when(restTemplate.exchange(
                any(String.class),
                eq(HttpMethod.DELETE),
                any(HttpEntity.class),
                eq(HcmUserRepositoryImpl.AITableDeleteResponse.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));
    }
}
