package com.example.usersync.unit.repository;

import com.example.usersync.dto.IamUserDto;
import com.example.usersync.dto.IamUserSearchRequest;
import com.example.usersync.dto.IamUserSearchResponse;
import com.example.usersync.repository.impl.IamUserRepositoryImpl;
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
@DisplayName("IamUserRepository Unit Tests")
class IamUserRepositoryTest {

    private RestTemplate restTemplate;

    private IamUserRepositoryImpl iamUserRepository;

    private static final String BASE_URL = "https://aitable.ai";
    private static final String DATASHEET_ID = "dsthiermXySxq5drDV";
    private static final String VIEW_ID = "viwE79FRt4XA8";
    private static final String API_TOKEN = "test-token";

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        iamUserRepository = new IamUserRepositoryImpl(restTemplate);
        ReflectionTestUtils.setField(iamUserRepository, "baseUrl", BASE_URL);
        ReflectionTestUtils.setField(iamUserRepository, "datasheetId", DATASHEET_ID);
        ReflectionTestUtils.setField(iamUserRepository, "viewId", VIEW_ID);
        ReflectionTestUtils.setField(iamUserRepository, "apiToken", API_TOKEN);
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
            IamUserSearchRequest request = IamUserSearchRequest.builder()
                    .pageNum(1)
                    .build();

            mockSuccessfulResponse();

            ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);

            // When
            iamUserRepository.search(request);

            // Then
            verify(restTemplate).exchange(
                    urlCaptor.capture(),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(IamUserRepositoryImpl.AITableResponse.class)
            );

            String actualUrl = urlCaptor.getValue();
            assertThat(actualUrl).contains(BASE_URL);
            assertThat(actualUrl).contains("/fusion/v1/datasheets/" + DATASHEET_ID + "/records");
            assertThat(actualUrl).contains("viewId=" + VIEW_ID);
            assertThat(actualUrl).contains("fieldKey=name");
            assertThat(actualUrl).contains("pageNum=1");
        }

        @Test
        @DisplayName("Should include Bearer token in Authorization header")
        void shouldIncludeBearerToken_inAuthorizationHeader() {
            // Given
            IamUserSearchRequest request = IamUserSearchRequest.builder()
                    .pageNum(1)
                    .build();

            mockSuccessfulResponse();

            ArgumentCaptor<HttpEntity<?>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);

            // When
            iamUserRepository.search(request);

            // Then
            verify(restTemplate).exchange(
                    any(String.class),
                    eq(HttpMethod.GET),
                    entityCaptor.capture(),
                    eq(IamUserRepositoryImpl.AITableResponse.class)
            );

            HttpEntity<?> capturedEntity = entityCaptor.getValue();
            String authHeader = capturedEntity.getHeaders().getFirst(org.springframework.http.HttpHeaders.AUTHORIZATION);
            assertThat(authHeader).isNotNull();
            assertThat(authHeader).isEqualTo("Bearer " + API_TOKEN);
        }

        @Test
        @DisplayName("Should filter records to only include IAM system users")
        void shouldFilterRecords_toOnlyIncludeIamSystem() {
            // Given
            IamUserSearchRequest request = IamUserSearchRequest.builder()
                    .pageNum(1)
                    .build();

            mockResponseWithMixedSystems();

            // When
            IamUserSearchResponse response = iamUserRepository.search(request);

            // Then
            assertThat(response.getRecords()).hasSize(2);
            assertThat(response.getRecords()).allMatch(record -> "IAM".equals(record.getSystem()));
        }

        @Test
        @DisplayName("Should map AITable response fields to IamUserDto correctly")
        void shouldMapFields_correctly() {
            // Given
            IamUserSearchRequest request = IamUserSearchRequest.builder()
                    .pageNum(1)
                    .build();

            mockResponseWithImaUsers();

            // When
            IamUserSearchResponse response = iamUserRepository.search(request);

            // Then
            assertThat(response.getRecords()).hasSize(1);
            IamUserDto user = response.getRecords().get(0);
            assertThat(user.getRecordId()).isEqualTo("rec123");
            assertThat(user.getTitle()).isEqualTo("John Doe");
            assertThat(user.getEmployeeId()).isEqualTo("EMP001");
            assertThat(user.getPhone()).isEqualTo("1234567890");
            assertThat(user.getSystem()).isEqualTo("IAM");
            assertThat(user.getEmail()).isEqualTo("john.doe@example.com");
        }
    }

    @Nested
    @DisplayName("create Tests")
    class CreateTests {

        @Test
        @DisplayName("Should send POST request to correct URL")
        void shouldSendPostRequest_toCorrectUrl() {
            // Given
            IamUserDto user = IamUserDto.builder()
                    .title("张三")
                    .employeeId("005")
                    .phone("13555555555")
                    .system("IAM")
                    .build();

            mockSuccessfulCreateResponse("recNew123");

            ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);

            // When
            iamUserRepository.create(user);

            // Then
            verify(restTemplate).exchange(
                    urlCaptor.capture(),
                    eq(HttpMethod.POST),
                    any(HttpEntity.class),
                    eq(IamUserRepositoryImpl.AITableCreateResponse.class)
            );

            String actualUrl = urlCaptor.getValue();
            assertThat(actualUrl).contains(BASE_URL);
            assertThat(actualUrl).contains("/fusion/v1/datasheets/" + DATASHEET_ID + "/records");
            assertThat(actualUrl).contains("fieldKey=name");
        }

        @Test
        @DisplayName("Should return created user with recordId")
        void shouldReturnCreatedUser_withRecordId() {
            // Given
            IamUserDto user = IamUserDto.builder()
                    .title("张三")
                    .employeeId("005")
                    .phone("13555555555")
                    .system("IAM")
                    .build();

            mockSuccessfulCreateResponse("recNew123");

            // When
            IamUserDto created = iamUserRepository.create(user);

            // Then
            assertThat(created.getRecordId()).isEqualTo("recNew123");
            assertThat(created.getTitle()).isEqualTo("张三");
            assertThat(created.getEmployeeId()).isEqualTo("005");
            assertThat(created.getSystem()).isEqualTo("IAM");
        }
    }

    @Nested
    @DisplayName("update Tests")
    class UpdateTests {

        @Test
        @DisplayName("Should send PATCH request to correct URL")
        void shouldSendPatchRequest_toCorrectUrl() {
            // Given
            IamUserDto user = IamUserDto.builder()
                    .recordId("recv0mmI1d6ax")
                    .title("张三")
                    .employeeId("005")
                    .phone("13555555555")
                    .system("IAM")
                    .build();

            mockSuccessfulUpdateResponse();

            ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);

            // When
            iamUserRepository.update(user);

            // Then
            verify(restTemplate).exchange(
                    urlCaptor.capture(),
                    eq(HttpMethod.PATCH),
                    any(HttpEntity.class),
                    eq(IamUserRepositoryImpl.AITableUpdateResponse.class)
            );

            String actualUrl = urlCaptor.getValue();
            assertThat(actualUrl).contains(BASE_URL);
            assertThat(actualUrl).contains("/fusion/v1/datasheets/" + DATASHEET_ID + "/records");
            assertThat(actualUrl).contains("viewId=" + VIEW_ID);
            assertThat(actualUrl).contains("fieldKey=name");
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
            iamUserRepository.delete(recordId);

            // Then
            verify(restTemplate).exchange(
                    urlCaptor.capture(),
                    eq(HttpMethod.DELETE),
                    any(HttpEntity.class),
                    eq(IamUserRepositoryImpl.AITableDeleteResponse.class)
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
            iamUserRepository.delete(recordIds);

            // Then
            verify(restTemplate).exchange(
                    urlCaptor.capture(),
                    eq(HttpMethod.DELETE),
                    any(HttpEntity.class),
                    eq(IamUserRepositoryImpl.AITableDeleteResponse.class)
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
            boolean result = iamUserRepository.delete(recordId);

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
            int count = iamUserRepository.delete(recordIds);

            // Then
            assertThat(count).isEqualTo(2);
        }

        @Test
        @DisplayName("Should return 0 when recordIds list is empty")
        void shouldReturn0_whenRecordIdsListIsEmpty() {
            // When
            int count = iamUserRepository.delete(List.of());

            // Then
            assertThat(count).isEqualTo(0);
            verifyNoInteractions(restTemplate);
        }
    }

    @Nested
    @DisplayName("resign Tests")
    class ResignTests {

        @Test
        @DisplayName("Should fetch user, update with active=false and prefixed phone, then update back")
        void shouldResignUser_withActiveFalseAndPrefixedPhone() {
            // Given
            String recordId = "rec123";
            IamUserRepositoryImpl.AITableRecord existingRecord = createRecord(recordId, "张三", "005", "13555555555", "IAM", "zhangsan@example.com");
            existingRecord.getFields().setActive(true);

            IamUserRepositoryImpl.AITableResponse getResponse = new IamUserRepositoryImpl.AITableResponse();
            IamUserRepositoryImpl.AITableData data = new IamUserRepositoryImpl.AITableData();
            data.setRecords(List.of(existingRecord));
            getResponse.setData(data);

            // Mock GET request (fetch current user)
            when(restTemplate.exchange(
                    any(String.class),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(IamUserRepositoryImpl.AITableResponse.class)
            )).thenReturn(new ResponseEntity<>(getResponse, HttpStatus.OK));

            // Mock PATCH request (update user)
            IamUserRepositoryImpl.AITableUpdateResponse updateResponse = new IamUserRepositoryImpl.AITableUpdateResponse();
            updateResponse.setSuccess(true);
            IamUserRepositoryImpl.AITableUpdateData updateData = new IamUserRepositoryImpl.AITableUpdateData();
            IamUserRepositoryImpl.AITableRecord updatedRecord = createRecord(recordId, "张三", "005", "_13555555555", "IAM", "zhangsan@example.com");
            updatedRecord.getFields().setActive(false);
            updateData.setRecords(List.of(updatedRecord));
            updateResponse.setData(updateData);

            when(restTemplate.exchange(
                    any(String.class),
                    eq(HttpMethod.PATCH),
                    any(HttpEntity.class),
                    eq(IamUserRepositoryImpl.AITableUpdateResponse.class)
            )).thenReturn(new ResponseEntity<>(updateResponse, HttpStatus.OK));

            // When
            IamUserDto result = iamUserRepository.resign(recordId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getRecordId()).isEqualTo(recordId);
            assertThat(result.getActive()).isFalse();
            assertThat(result.getPhone()).startsWith("_");
        }

        @Test
        @DisplayName("Should handle null phone when prefixing with _")
        void shouldHandleNullPhone_whenPrefixing() {
            // Given
            String recordId = "rec456";
            IamUserRepositoryImpl.AITableRecord existingRecord = createRecord(recordId, "李四", "006", null, "IAM", null);
            existingRecord.getFields().setActive(true);

            IamUserRepositoryImpl.AITableResponse getResponse = new IamUserRepositoryImpl.AITableResponse();
            IamUserRepositoryImpl.AITableData data = new IamUserRepositoryImpl.AITableData();
            data.setRecords(List.of(existingRecord));
            getResponse.setData(data);

            when(restTemplate.exchange(
                    any(String.class),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(IamUserRepositoryImpl.AITableResponse.class)
            )).thenReturn(new ResponseEntity<>(getResponse, HttpStatus.OK));

            IamUserRepositoryImpl.AITableUpdateResponse updateResponse = new IamUserRepositoryImpl.AITableUpdateResponse();
            updateResponse.setSuccess(true);
            IamUserRepositoryImpl.AITableUpdateData updateData = new IamUserRepositoryImpl.AITableUpdateData();
            IamUserRepositoryImpl.AITableRecord updatedRecord = createRecord(recordId, "李四", "006", "_", "IAM", null);
            updatedRecord.getFields().setActive(false);
            updateData.setRecords(List.of(updatedRecord));
            updateResponse.setData(updateData);

            when(restTemplate.exchange(
                    any(String.class),
                    eq(HttpMethod.PATCH),
                    any(HttpEntity.class),
                    eq(IamUserRepositoryImpl.AITableUpdateResponse.class)
            )).thenReturn(new ResponseEntity<>(updateResponse, HttpStatus.OK));

            // When
            IamUserDto result = iamUserRepository.resign(recordId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getActive()).isFalse();
            assertThat(result.getPhone()).isEqualTo("_");
        }
    }

    // ==================== Mock Methods ====================

    private void mockSuccessfulResponse() {
        IamUserRepositoryImpl.AITableResponse response = new IamUserRepositoryImpl.AITableResponse();
        IamUserRepositoryImpl.AITableData data = new IamUserRepositoryImpl.AITableData();
        data.setRecords(List.of());
        response.setData(data);

        when(restTemplate.exchange(
                any(String.class),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(IamUserRepositoryImpl.AITableResponse.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));
    }

    private void mockResponseWithMixedSystems() {
        IamUserRepositoryImpl.AITableResponse response = new IamUserRepositoryImpl.AITableResponse();
        IamUserRepositoryImpl.AITableData data = new IamUserRepositoryImpl.AITableData();
        data.setTotal(3);
        data.setPageNum(1);
        data.setPageSize(100);

        IamUserRepositoryImpl.AITableRecord imaRecord1 = createRecord("rec1", "John", "EMP001", "123", "IAM", "john@example.com");
        IamUserRepositoryImpl.AITableRecord imaRecord2 = createRecord("rec2", "Jane", "EMP002", "456", "IAM", "jane@example.com");
        IamUserRepositoryImpl.AITableRecord hcmRecord = createRecord("rec3", "Bob", "EMP003", "789", "HCM", "bob@example.com");

        data.setRecords(List.of(imaRecord1, imaRecord2, hcmRecord));
        response.setData(data);

        when(restTemplate.exchange(
                any(String.class),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(IamUserRepositoryImpl.AITableResponse.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));
    }

    private void mockResponseWithImaUsers() {
        IamUserRepositoryImpl.AITableResponse response = new IamUserRepositoryImpl.AITableResponse();
        IamUserRepositoryImpl.AITableData data = new IamUserRepositoryImpl.AITableData();
        data.setPageNum(1);
        data.setPageSize(100);

        IamUserRepositoryImpl.AITableRecord record = createRecord("rec123", "John Doe", "EMP001", "1234567890", "IAM", "john.doe@example.com");
        data.setRecords(List.of(record));
        response.setData(data);

        when(restTemplate.exchange(
                any(String.class),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(IamUserRepositoryImpl.AITableResponse.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));
    }

    private void mockSuccessfulCreateResponse(String newRecordId) {
        IamUserRepositoryImpl.AITableCreateResponse response = new IamUserRepositoryImpl.AITableCreateResponse();
        response.setSuccess(true);
        response.setCode(200);

        IamUserRepositoryImpl.AITableCreateData data = new IamUserRepositoryImpl.AITableCreateData();
        IamUserRepositoryImpl.AITableRecord record = createRecord(newRecordId, "张三", "005", "13555555555", "IAM", null);
        data.setRecords(List.of(record));
        response.setData(data);

        when(restTemplate.exchange(
                any(String.class),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(IamUserRepositoryImpl.AITableCreateResponse.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));
    }

    private void mockSuccessfulUpdateResponse() {
        IamUserRepositoryImpl.AITableUpdateResponse response = new IamUserRepositoryImpl.AITableUpdateResponse();
        response.setSuccess(true);
        response.setCode(200);

        IamUserRepositoryImpl.AITableUpdateData data = new IamUserRepositoryImpl.AITableUpdateData();
        IamUserRepositoryImpl.AITableRecord record = createRecord("recv0mmI1d6ax", "张三", "005", "13555555555", "IAM", null);
        data.setRecords(List.of(record));
        response.setData(data);

        when(restTemplate.exchange(
                any(String.class),
                eq(HttpMethod.PATCH),
                any(HttpEntity.class),
                eq(IamUserRepositoryImpl.AITableUpdateResponse.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));
    }

    private void mockSuccessfulDeleteResponse(int count) {
        IamUserRepositoryImpl.AITableDeleteResponse response = new IamUserRepositoryImpl.AITableDeleteResponse();
        response.setSuccess(true);
        response.setCode(200);

        IamUserRepositoryImpl.AITableDeleteData data = new IamUserRepositoryImpl.AITableDeleteData();
        data.setRecordCount(count);
        response.setData(data);

        when(restTemplate.exchange(
                any(String.class),
                eq(HttpMethod.DELETE),
                any(HttpEntity.class),
                eq(IamUserRepositoryImpl.AITableDeleteResponse.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));
    }

    private IamUserRepositoryImpl.AITableRecord createRecord(String recordId, String title, String employeeId,
                                                             String phone, String system, String email) {
        return createRecord(recordId, title, employeeId, phone, system, email, true);
    }

    private IamUserRepositoryImpl.AITableRecord createRecord(String recordId, String title, String employeeId,
                                                             String phone, String system, String email, Boolean active) {
        IamUserRepositoryImpl.AITableRecord record = new IamUserRepositoryImpl.AITableRecord();
        record.setRecordId(recordId);

        IamUserRepositoryImpl.AITableFields fields = new IamUserRepositoryImpl.AITableFields();
        fields.setTitle(title);
        fields.setEmployeeId(employeeId);
        fields.setPhone(phone);
        fields.setSystem(system);
        fields.setEmail(email);
        fields.setActive(active);

        record.setFields(fields);
        return record;
    }
}
