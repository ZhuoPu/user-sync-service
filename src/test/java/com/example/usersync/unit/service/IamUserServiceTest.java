package com.example.usersync.unit.service;

import com.example.usersync.dto.IamUserDto;
import com.example.usersync.dto.IamUserSearchRequest;
import com.example.usersync.dto.IamUserSearchResponse;
import com.example.usersync.repository.IamUserRepository;
import com.example.usersync.service.IamUserService;
import com.example.usersync.service.impl.IamUserServiceImpl;
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
@DisplayName("IamUserService Unit Tests")
class IamUserServiceTest {

    @Mock
    private IamUserRepository iamUserRepository;

    private IamUserService iamUserService;

    @BeforeEach
    void setUp() {
        iamUserService = new IamUserServiceImpl(iamUserRepository);
    }

    @AfterEach
    void tearDown() {
        reset(iamUserRepository);
    }

    @Nested
    @DisplayName("search Tests")
    class SearchTests {

        @Test
        @DisplayName("Should delegate to repository and return response")
        void shouldDelegateToRepository_andReturnResponse() {
            // Given
            IamUserSearchRequest request = IamUserSearchRequest.builder()
                    .pageNum(1)
                    .build();

            IamUserSearchResponse expectedResponse = createMockResponse();
            when(iamUserRepository.search(any(IamUserSearchRequest.class))).thenReturn(expectedResponse);

            // When
            IamUserSearchResponse actualResponse = iamUserService.search(request);

            // Then
            assertThat(actualResponse).isNotNull();
            assertThat(actualResponse.getTotal()).isEqualTo(2);
            assertThat(actualResponse.getPageNum()).isEqualTo(1);
            assertThat(actualResponse.getRecords()).hasSize(2);

            verify(iamUserRepository).search(request);
        }
    }

    @Nested
    @DisplayName("create Tests")
    class CreateTests {

        @Test
        @DisplayName("Should delegate to repository and return created user")
        void shouldDelegateToRepository_andReturnCreatedUser() {
            // Given
            IamUserDto user = IamUserDto.builder()
                    .title("张三")
                    .employeeId("005")
                    .phone("13555555555")
                    .system("IAM")
                    .active(true)
                    .build();

            IamUserDto createdUser = IamUserDto.builder()
                    .recordId("recNew123")
                    .title("张三")
                    .employeeId("005")
                    .phone("13555555555")
                    .system("IAM")
                    .active(true)
                    .build();

            when(iamUserRepository.create(any(IamUserDto.class))).thenReturn(createdUser);

            // When
            IamUserDto result = iamUserService.create(user);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getRecordId()).isEqualTo("recNew123");
            verify(iamUserRepository).create(user);
        }
    }

    @Nested
    @DisplayName("update Tests")
    class UpdateTests {

        @Test
        @DisplayName("Should delegate to repository and return updated user")
        void shouldDelegateToRepository_andReturnUpdatedUser() {
            // Given
            IamUserDto user = IamUserDto.builder()
                    .recordId("recv0mmI1d6ax")
                    .title("张三")
                    .employeeId("005")
                    .phone("13555555555")
                    .system("IAM")
                    .active(true)
                    .build();

            IamUserDto updatedUser = IamUserDto.builder()
                    .recordId("recv0mmI1d6ax")
                    .title("张三")
                    .employeeId("005")
                    .phone("13555555555")
                    .system("IAM")
                    .active(true)
                    .build();

            when(iamUserRepository.update(any(IamUserDto.class))).thenReturn(updatedUser);

            // When
            IamUserDto result = iamUserService.update(user);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getRecordId()).isEqualTo("recv0mmI1d6ax");
            verify(iamUserRepository).update(user);
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
            when(iamUserRepository.delete(recordId)).thenReturn(true);

            // When
            boolean result = iamUserService.delete(recordId);

            // Then
            assertThat(result).isTrue();
            verify(iamUserRepository).delete(recordId);
        }

        @Test
        @DisplayName("Should delegate to repository with multiple recordIds")
        void shouldDelegateToRepository_withMultipleRecordIds() {
            // Given
            List<String> recordIds = List.of("recv0mmI1d6ax", "rec1HWFVYAG2z");
            when(iamUserRepository.delete(recordIds)).thenReturn(2);

            // When
            int count = iamUserService.delete(recordIds);

            // Then
            assertThat(count).isEqualTo(2);
            verify(iamUserRepository).delete(recordIds);
        }

        @Test
        @DisplayName("Should return false when repository returns false")
        void shouldReturnFalse_whenRepositoryReturnsFalse() {
            // Given
            String recordId = "nonexistent";
            when(iamUserRepository.delete(recordId)).thenReturn(false);

            // When
            boolean result = iamUserService.delete(recordId);

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("resign Tests")
    class ResignTests {

        @Test
        @DisplayName("Should delegate to repository and return resigned user")
        void shouldDelegateToRepository_andReturnResignedUser() {
            // Given
            String recordId = "rec123";

            IamUserDto resignedUser = IamUserDto.builder()
                    .recordId(recordId)
                    .title("张三")
                    .employeeId("005")
                    .phone("_13555555555")
                    .system("IAM")
                    .active(false)
                    .build();

            when(iamUserRepository.resign(recordId)).thenReturn(resignedUser);

            // When
            IamUserDto result = iamUserService.resign(recordId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getActive()).isFalse();
            assertThat(result.getPhone()).startsWith("_");
            verify(iamUserRepository).resign(recordId);
        }
    }

    private IamUserSearchResponse createMockResponse() {
        IamUserDto user1 = IamUserDto.builder()
                .recordId("rec1")
                .title("John Doe")
                .employeeId("EMP001")
                .phone("1234567890")
                .system("IAM")
                .email("john@example.com")
                .active(true)
                .build();

        IamUserDto user2 = IamUserDto.builder()
                .recordId("rec2")
                .title("Jane Smith")
                .employeeId("EMP002")
                .phone("0987654321")
                .system("IAM")
                .email("jane@example.com")
                .active(true)
                .build();

        return IamUserSearchResponse.builder()
                .total(2)
                .pageNum(1)
                .pageSize(100)
                .records(List.of(user1, user2))
                .build();
    }
}
