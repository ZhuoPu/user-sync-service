package com.example.usersync.unit.service;

import com.example.usersync.dto.HcmUserDto;
import com.example.usersync.dto.HcmUserSearchRequest;
import com.example.usersync.dto.HcmUserSearchResponse;
import com.example.usersync.repository.HcmUserRepository;
import com.example.usersync.service.HcmUserService;
import com.example.usersync.service.KafkaEventProducer;
import com.example.usersync.service.impl.HcmUserServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
@DisplayName("HcmUserService Unit Tests")
class HcmUserServiceTest {

    @Mock
    private HcmUserRepository hcmUserRepository;

    @Mock
    private KafkaEventProducer kafkaEventProducer;

    private HcmUserService hcmUserService;

    @BeforeEach
    void setUp() {
        hcmUserService = new HcmUserServiceImpl(hcmUserRepository, kafkaEventProducer);
    }

    @AfterEach
    void tearDown() {
        reset(hcmUserRepository, kafkaEventProducer);
    }

    @Nested
    @DisplayName("search Tests")
    class SearchTests {

        @Test
        @DisplayName("Should delegate to repository and return response")
        void shouldDelegateToRepository_andReturnResponse() {
            // Given
            HcmUserSearchRequest request = HcmUserSearchRequest.builder()
                    .pageNum(1)
                    .build();

            HcmUserSearchResponse expectedResponse = createMockResponse();
            when(hcmUserRepository.search(any(HcmUserSearchRequest.class))).thenReturn(expectedResponse);

            // When
            HcmUserSearchResponse actualResponse = hcmUserService.search(request);

            // Then
            assertThat(actualResponse).isNotNull();
            assertThat(actualResponse.getTotal()).isEqualTo(2);
            assertThat(actualResponse.getPageNum()).isEqualTo(1);
            assertThat(actualResponse.getRecords()).hasSize(2);

            verify(hcmUserRepository).search(request);
        }

        @Test
        @DisplayName("Should pass request object to repository unchanged")
        void shouldPassRequest_toRepositoryUnchanged() {
            // Given
            HcmUserSearchRequest request = HcmUserSearchRequest.builder()
                    .pageNum(3)
                    .build();

            when(hcmUserRepository.search(any(HcmUserSearchRequest.class))).thenReturn(createMockResponse());

            // When
            hcmUserService.search(request);

            // Then
            verify(hcmUserRepository).search(request);
        }

        @Test
        @DisplayName("Should return empty response when repository returns empty")
        void shouldReturnEmptyResponse_whenRepositoryReturnsEmpty() {
            // Given
            HcmUserSearchRequest request = HcmUserSearchRequest.builder()
                    .pageNum(1)
                    .build();

            HcmUserSearchResponse emptyResponse = HcmUserSearchResponse.builder()
                    .total(0)
                    .pageNum(1)
                    .pageSize(0)
                    .records(List.of())
                    .build();

            when(hcmUserRepository.search(any(HcmUserSearchRequest.class))).thenReturn(emptyResponse);

            // When
            HcmUserSearchResponse actualResponse = hcmUserService.search(request);

            // Then
            assertThat(actualResponse.getRecords()).isEmpty();
            assertThat(actualResponse.getTotal()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should handle null pageNum in request")
        void shouldHandleNullPageNum_inRequest() {
            // Given
            HcmUserSearchRequest request = HcmUserSearchRequest.builder()
                    .pageNum(null)
                    .build();

            when(hcmUserRepository.search(any(HcmUserSearchRequest.class))).thenReturn(createMockResponse());

            // When
            HcmUserSearchResponse actualResponse = hcmUserService.search(request);

            // Then
            assertThat(actualResponse).isNotNull();
            verify(hcmUserRepository).search(request);
        }
    }

    @Nested
    @DisplayName("create Tests")
    class CreateTests {

        @Test
        @DisplayName("Should delegate to repository and return created user")
        void shouldDelegateToRepository_andReturnCreatedUser() {
            // Given
            HcmUserDto user = HcmUserDto.builder()
                    .title("李四")
                    .employeeId("004")
                    .phone("13444444444")
                    .system("HCM")
                    .build();

            HcmUserDto createdUser = HcmUserDto.builder()
                    .recordId("recNew123")
                    .title("李四")
                    .employeeId("004")
                    .phone("13444444444")
                    .system("HCM")
                    .build();

            when(hcmUserRepository.create(any(HcmUserDto.class))).thenReturn(createdUser);

            // When
            HcmUserDto result = hcmUserService.create(user);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getRecordId()).isEqualTo("recNew123");
            assertThat(result.getTitle()).isEqualTo("李四");
            verify(hcmUserRepository).create(user);
        }
    }

    @Nested
    @DisplayName("update Tests")
    class UpdateTests {

        @Test
        @DisplayName("Should delegate to repository and return updated user")
        void shouldDelegateToRepository_andReturnUpdatedUser() {
            // Given
            HcmUserDto user = HcmUserDto.builder()
                    .recordId("recv0mmI1d6ax")
                    .title("李四")
                    .employeeId("004")
                    .phone("13444444444")
                    .system("HCM")
                    .build();

            HcmUserDto updatedUser = HcmUserDto.builder()
                    .recordId("recv0mmI1d6ax")
                    .title("李四")
                    .employeeId("004")
                    .phone("13444444444")
                    .system("HCM")
                    .build();

            when(hcmUserRepository.update(any(HcmUserDto.class))).thenReturn(updatedUser);

            // When
            HcmUserDto result = hcmUserService.update(user);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getRecordId()).isEqualTo("recv0mmI1d6ax");
            verify(hcmUserRepository).update(user);
        }
    }

    @Nested
    @DisplayName("delete Tests")
    class DeleteTests {

        @Test
        @DisplayName("Should delegate to repository with single recordId")
        void shouldDelegateToRepository_withSingleRecordId() {
            // Given
            String recordId = "recv0mmI1d6ax";
            when(hcmUserRepository.delete(recordId)).thenReturn(true);

            // When
            boolean result = hcmUserService.delete(recordId);

            // Then
            assertThat(result).isTrue();
            verify(hcmUserRepository).delete(recordId);
        }

        @Test
        @DisplayName("Should delegate to repository with multiple recordIds")
        void shouldDelegateToRepository_withMultipleRecordIds() {
            // Given
            List<String> recordIds = List.of("recv0mmI1d6ax", "rec1HWFVYAG2z");
            when(hcmUserRepository.delete(recordIds)).thenReturn(2);

            // When
            int count = hcmUserService.delete(recordIds);

            // Then
            assertThat(count).isEqualTo(2);
            verify(hcmUserRepository).delete(recordIds);
        }

        @Test
        @DisplayName("Should return false when repository returns false")
        void shouldReturnFalse_whenRepositoryReturnsFalse() {
            // Given
            String recordId = "nonexistent";
            when(hcmUserRepository.delete(recordId)).thenReturn(false);

            // When
            boolean result = hcmUserService.delete(recordId);

            // Then
            assertThat(result).isFalse();
        }
    }

    private HcmUserSearchResponse createMockResponse() {
        HcmUserDto user1 = HcmUserDto.builder()
                .recordId("rec1")
                .title("John Doe")
                .employeeId("EMP001")
                .phone("1234567890")
                .system("HCM")
                .email("john@example.com")
                .build();

        HcmUserDto user2 = HcmUserDto.builder()
                .recordId("rec2")
                .title("Jane Smith")
                .employeeId("EMP002")
                .phone("0987654321")
                .system("HCM")
                .email("jane@example.com")
                .build();

        return HcmUserSearchResponse.builder()
                .total(2)
                .pageNum(1)
                .pageSize(100)
                .records(List.of(user1, user2))
                .build();
    }
}
