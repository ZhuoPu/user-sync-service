package com.example.usersync.service;

import com.example.usersync.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExternalApiService Unit Tests")
class ExternalApiServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private ExternalApiService externalApiService;

    private static final String BASE_URL = "https://api.example.com";
    private static final String SYNC_ENDPOINT = "/api/v1/users";

    @BeforeEach
    void setUp() {
        externalApiService = new ExternalApiService(restTemplate);
        ReflectionTestUtils.setField(externalApiService, "baseUrl", BASE_URL);
        ReflectionTestUtils.setField(externalApiService, "syncEndpoint", SYNC_ENDPOINT);
    }

    @AfterEach
    void tearDown() {
        reset(restTemplate);
    }

    @Nested
    @DisplayName("pushUser Tests")
    class PushUserTests {

        @Test
        @DisplayName("Should send PUT request to correct URL with user data")
        void shouldSendPutRequest_toCorrectUrl() {
            // Given
            User user = User.builder()
                    .userId("user-123")
                    .username("testuser")
                    .email("test@example.com")
                    .firstName("Test")
                    .lastName("User")
                    .phoneNumber("+1234567890")
                    .build();

            String expectedUrl = BASE_URL + SYNC_ENDPOINT + "/user-123";
            mockSuccessfulResponse();

            // When
            externalApiService.pushUser(user);

            // Then
            verify(restTemplate).exchange(
                    eq(expectedUrl),
                    eq(HttpMethod.PUT),
                    any(HttpEntity.class),
                    eq(Void.class)
            );
        }

        @Test
        @DisplayName("Should include JSON content type header")
        void shouldIncludeJsonContentType_header() {
            // Given
            User user = User.builder()
                    .userId("user-123")
                    .username("testuser")
                    .email("test@example.com")
                    .firstName("Test")
                    .lastName("User")
                    .phoneNumber("+1234567890")
                    .build();

            mockSuccessfulResponse();
            ArgumentCaptor<HttpEntity<?>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);

            // When
            externalApiService.pushUser(user);

            // Then
            verify(restTemplate).exchange(
                    any(String.class),
                    eq(HttpMethod.PUT),
                    entityCaptor.capture(),
                    eq(Void.class)
            );

            HttpEntity<?> capturedEntity = entityCaptor.getValue();
            assertThat(capturedEntity.getHeaders().getContentType().toString())
                    .contains("application/json");
        }

        @Test
        @DisplayName("Should send correct UserDto in request body")
        void shouldSendCorrectUserDto_inRequestBody() {
            // Given
            User user = User.builder()
                    .userId("user-123")
                    .username("testuser")
                    .email("test@example.com")
                    .firstName("Test")
                    .lastName("User")
                    .phoneNumber("+1234567890")
                    .build();

            mockSuccessfulResponse();
            ArgumentCaptor<HttpEntity<?>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);

            // When
            externalApiService.pushUser(user);

            // Then
            verify(restTemplate).exchange(
                    any(String.class),
                    eq(HttpMethod.PUT),
                    entityCaptor.capture(),
                    eq(Void.class)
            );

            HttpEntity<?> capturedEntity = entityCaptor.getValue();
            assertThat(capturedEntity.getBody()).isInstanceOf(com.example.usersync.dto.UserDto.class);

            com.example.usersync.dto.UserDto dto = (com.example.usersync.dto.UserDto) capturedEntity.getBody();
            assertThat(dto.getUserId()).isEqualTo("user-123");
            assertThat(dto.getUsername()).isEqualTo("testuser");
            assertThat(dto.getEmail()).isEqualTo("test@example.com");
            assertThat(dto.getFirstName()).isEqualTo("Test");
            assertThat(dto.getLastName()).isEqualTo("User");
            assertThat(dto.getPhoneNumber()).isEqualTo("+1234567890");
        }

        @Test
        @DisplayName("Should construct correct URL for different user IDs")
        void shouldConstructCorrectUrl_forDifferentUserIds() {
            // Given
            User user1 = User.builder().userId("user-abc").username("user1").build();
            User user2 = User.builder().userId("user-xyz").username("user2").build();

            mockSuccessfulResponse();
            ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);

            // When
            externalApiService.pushUser(user1);
            externalApiService.pushUser(user2);

            // Then
            verify(restTemplate, times(2)).exchange(
                    urlCaptor.capture(),
                    eq(HttpMethod.PUT),
                    any(HttpEntity.class),
                    eq(Void.class)
            );

            assertThat(urlCaptor.getAllValues()).containsExactly(
                    BASE_URL + SYNC_ENDPOINT + "/user-abc",
                    BASE_URL + SYNC_ENDPOINT + "/user-xyz"
            );
        }

        @Test
        @DisplayName("Should handle user with null optional fields")
        void shouldHandleUser_withNullOptionalFields() {
            // Given
            User user = User.builder()
                    .userId("user-123")
                    .username("testuser")
                    .email("test@example.com")
                    .firstName(null)
                    .lastName(null)
                    .phoneNumber(null)
                    .build();

            mockSuccessfulResponse();
            ArgumentCaptor<HttpEntity<?>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);

            // When
            externalApiService.pushUser(user);

            // Then
            verify(restTemplate).exchange(
                    any(String.class),
                    eq(HttpMethod.PUT),
                    entityCaptor.capture(),
                    eq(Void.class)
            );

            com.example.usersync.dto.UserDto dto = (com.example.usersync.dto.UserDto) entityCaptor.getValue().getBody();
            assertThat(dto.getFirstName()).isNull();
            assertThat(dto.getLastName()).isNull();
            assertThat(dto.getPhoneNumber()).isNull();
        }
    }

    @Nested
    @DisplayName("Configuration Tests")
    class ConfigurationTests {

        @Test
        @DisplayName("Should use configured base URL")
        void shouldUseConfiguredBaseUrl() {
            // Given
            String customBaseUrl = "https://custom.api.com";
            String customEndpoint = "/sync";
            ReflectionTestUtils.setField(externalApiService, "baseUrl", customBaseUrl);
            ReflectionTestUtils.setField(externalApiService, "syncEndpoint", customEndpoint);

            User user = User.builder().userId("user-1").username("test").build();
            mockSuccessfulResponse();
            ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);

            // When
            externalApiService.pushUser(user);

            // Then
            verify(restTemplate).exchange(
                    urlCaptor.capture(),
                    eq(HttpMethod.PUT),
                    any(HttpEntity.class),
                    eq(Void.class)
            );

            assertThat(urlCaptor.getValue()).isEqualTo(customBaseUrl + customEndpoint + "/user-1");
        }
    }

    private void mockSuccessfulResponse() {
        when(restTemplate.exchange(
                any(String.class),
                any(HttpMethod.class),
                any(HttpEntity.class),
                eq(Void.class)
        )).thenReturn(new ResponseEntity<>(HttpStatus.OK));
    }
}
