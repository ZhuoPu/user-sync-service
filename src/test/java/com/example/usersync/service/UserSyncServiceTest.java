package com.example.usersync.service;

import com.example.usersync.dto.UserEvent;
import com.example.usersync.entity.User;
import com.example.usersync.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserSyncService Unit Tests")
class UserSyncServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ExternalApiService externalApiService;

    @InjectMocks
    private UserSyncService userSyncService;

    private UserEvent testEvent;
    private User existingUser;

    @BeforeEach
    void setUp() {
        testEvent = UserEvent.builder()
                .userId("user-123")
                .username("testuser")
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .phoneNumber("+1234567890")
                .eventType(UserEvent.EventType.CREATE)
                .eventTimestamp(java.time.Instant.now())
                .dataVersion(1L)
                .build();

        existingUser = User.builder()
                .userId("user-123")
                .username("olduser")
                .email("old@example.com")
                .firstName("Old")
                .lastName("User")
                .phoneNumber("+0987654321")
                .dataHash("old-hash")
                .syncStatus(User.SyncStatus.SYNCED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .retryCount(0)
                .build();
    }

    @Nested
    @DisplayName("processUserEvent - CREATE Event")
    class ProcessUserEventCreateTests {

        @Test
        @DisplayName("Should create new user when user does not exist")
        void shouldCreateNewUser_whenUserDoesNotExist() {
            // Given
            when(userRepository.findById(anyString())).thenReturn(Optional.empty());

            // When
            userSyncService.processUserEvent(testEvent);

            // Then
            verify(userRepository).save(argThat((User user) ->
                    user.getUserId().equals("user-123") &&
                            user.getUsername().equals("testuser") &&
                            user.getEmail().equals("test@example.com") &&
                            user.getSyncStatus() == User.SyncStatus.PENDING &&
                            user.getRetryCount() == 0
            ));
        }

        @Test
        @DisplayName("Should calculate correct data hash for new user")
        void shouldCalculateCorrectDataHash_forNewUser() {
            // Given
            when(userRepository.findById(anyString())).thenReturn(Optional.empty());

            // When
            userSyncService.processUserEvent(testEvent);

            // Then
            verify(userRepository).save(argThat((User user) ->
                    user.getDataHash() != null &&
                            !user.getDataHash().isEmpty() &&
                            user.getDataHash().length() == 64 // SHA-256 produces 64 hex chars
            ));
        }
    }

    @Nested
    @DisplayName("processUserEvent - UPDATE Event with Existing User")
    class ProcessUserEventUpdateTests {

        @Test
        @DisplayName("Should mark user as CHANGED when data has changed")
        void shouldMarkUserAsChanged_whenDataHasChanged() {
            // Given
            when(userRepository.findById("user-123")).thenReturn(Optional.of(existingUser));

            // When
            userSyncService.processUserEvent(testEvent);

            // Then
            verify(userRepository).save(argThat((User user) ->
                    user.getSyncStatus() == User.SyncStatus.CHANGED &&
                            user.getUsername().equals("testuser") &&
                            user.getEmail().equals("test@example.com")
            ));
        }

        @Test
        @DisplayName("Should not update user status when data has not changed")
        void shouldNotUpdateUserStatus_whenDataHasNotChanged() {
            // Given - Create a copy of the event with existing user's data
            UserEvent sameDataEvent = UserEvent.builder()
                    .userId(existingUser.getUserId())
                    .username(existingUser.getUsername())
                    .email(existingUser.getEmail())
                    .firstName(existingUser.getFirstName())
                    .lastName(existingUser.getLastName())
                    .phoneNumber(existingUser.getPhoneNumber())
                    .eventType(UserEvent.EventType.UPDATE)
                    .eventTimestamp(java.time.Instant.now())
                    .dataVersion(2L)
                    .build();

            // Calculate the hash the same way the service does
            String existingHash = calculateDataHash(sameDataEvent);
            existingUser.setDataHash(existingHash);

            when(userRepository.findById("user-123")).thenReturn(Optional.of(existingUser));

            // When
            userSyncService.processUserEvent(sameDataEvent);

            // Then
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("Should update user fields when data has changed")
        void shouldUpdateUserFields_whenDataHasChanged() {
            // Given
            when(userRepository.findById("user-123")).thenReturn(Optional.of(existingUser));

            // When
            userSyncService.processUserEvent(testEvent);

            // Then
            verify(userRepository).save(argThat((User user) ->
                    user.getUsername().equals("testuser") &&
                            user.getEmail().equals("test@example.com") &&
                            user.getFirstName().equals("Test") &&
                            user.getLastName().equals("User") &&
                            user.getPhoneNumber().equals("+1234567890")
            ));
        }
    }

    @Nested
    @DisplayName("processUserEvent - DELETE Event")
    class ProcessUserEventDeleteTests {

        @Test
        @DisplayName("Should delete existing user when DELETE event is received")
        void shouldDeleteUser_whenDeleteEventReceived() {
            // Given
            UserEvent deleteEvent = UserEvent.builder()
                    .userId("user-123")
                    .eventType(UserEvent.EventType.DELETE)
                    .build();
            when(userRepository.findById("user-123")).thenReturn(Optional.of(existingUser));

            // When
            userSyncService.processUserEvent(deleteEvent);

            // Then
            verify(userRepository).delete(existingUser);
        }

        @Test
        @DisplayName("Should not throw error when deleting non-existent user")
        void shouldNotThrowError_whenDeletingNonExistentUser() {
            // Given
            UserEvent deleteEvent = UserEvent.builder()
                    .userId("non-existent")
                    .eventType(UserEvent.EventType.DELETE)
                    .build();
            when(userRepository.findById("non-existent")).thenReturn(Optional.empty());

            // When/Then - should not throw
            userSyncService.processUserEvent(deleteEvent);

            verify(userRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("syncPendingUsers Tests")
    class SyncPendingUsersTests {

        @Test
        @DisplayName("Should sync all PENDING users successfully")
        void shouldSyncPendingUsers_whenPendingUsersExist() {
            // Given
            User pendingUser1 = User.builder()
                    .userId("user-1")
                    .username("user1")
                    .syncStatus(User.SyncStatus.PENDING)
                    .retryCount(0)
                    .build();
            User pendingUser2 = User.builder()
                    .userId("user-2")
                    .username("user2")
                    .syncStatus(User.SyncStatus.PENDING)
                    .retryCount(0)
                    .build();

            when(userRepository.findBySyncStatusAndRetryCountLessThan(User.SyncStatus.PENDING, 3))
                    .thenReturn(List.of(pendingUser1, pendingUser2));
            when(userRepository.findBySyncStatusAndRetryCountLessThan(User.SyncStatus.CHANGED, 3))
                    .thenReturn(List.of());
            when(userRepository.findBySyncStatusAndRetryCountLessThan(User.SyncStatus.FAILED, 3))
                    .thenReturn(List.of());
            doNothing().when(externalApiService).pushUser(any(User.class));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            int syncedCount = userSyncService.syncPendingUsers();

            // Then
            assertThat(syncedCount).isEqualTo(2);
            verify(externalApiService, times(2)).pushUser(any(User.class));
        }

        @Test
        @DisplayName("Should sync all CHANGED users successfully")
        void shouldSyncChangedUsers_whenChangedUsersExist() {
            // Given
            User changedUser = User.builder()
                    .userId("user-1")
                    .username("user1")
                    .syncStatus(User.SyncStatus.CHANGED)
                    .retryCount(0)
                    .build();

            when(userRepository.findBySyncStatusAndRetryCountLessThan(User.SyncStatus.PENDING, 3))
                    .thenReturn(List.of());
            when(userRepository.findBySyncStatusAndRetryCountLessThan(User.SyncStatus.CHANGED, 3))
                    .thenReturn(List.of(changedUser));
            when(userRepository.findBySyncStatusAndRetryCountLessThan(User.SyncStatus.FAILED, 3))
                    .thenReturn(List.of());
            doNothing().when(externalApiService).pushUser(any(User.class));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            int syncedCount = userSyncService.syncPendingUsers();

            // Then
            assertThat(syncedCount).isEqualTo(1);
            verify(externalApiService).pushUser(changedUser);
        }

        @Test
        @DisplayName("Should sync all FAILED users successfully")
        void shouldSyncFailedUsers_whenFailedUsersExist() {
            // Given
            User failedUser = User.builder()
                    .userId("user-1")
                    .username("user1")
                    .syncStatus(User.SyncStatus.FAILED)
                    .retryCount(1)
                    .build();

            when(userRepository.findBySyncStatusAndRetryCountLessThan(User.SyncStatus.PENDING, 3))
                    .thenReturn(List.of());
            when(userRepository.findBySyncStatusAndRetryCountLessThan(User.SyncStatus.CHANGED, 3))
                    .thenReturn(List.of());
            when(userRepository.findBySyncStatusAndRetryCountLessThan(User.SyncStatus.FAILED, 3))
                    .thenReturn(List.of(failedUser));
            doNothing().when(externalApiService).pushUser(any(User.class));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            int syncedCount = userSyncService.syncPendingUsers();

            // Then
            assertThat(syncedCount).isEqualTo(1);
            verify(externalApiService).pushUser(failedUser);
        }

        @Test
        @DisplayName("Should mark user as SYNCED after successful sync")
        void shouldMarkUserAsSynced_afterSuccessfulSync() {
            // Given
            User pendingUser = User.builder()
                    .userId("user-1")
                    .username("user1")
                    .syncStatus(User.SyncStatus.PENDING)
                    .retryCount(2)
                    .build();

            when(userRepository.findBySyncStatusAndRetryCountLessThan(User.SyncStatus.PENDING, 3))
                    .thenReturn(List.of(pendingUser));
            when(userRepository.findBySyncStatusAndRetryCountLessThan(User.SyncStatus.CHANGED, 3))
                    .thenReturn(List.of());
            when(userRepository.findBySyncStatusAndRetryCountLessThan(User.SyncStatus.FAILED, 3))
                    .thenReturn(List.of());
            doNothing().when(externalApiService).pushUser(any(User.class));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            userSyncService.syncPendingUsers();

            // Then
            verify(userRepository).save(argThat((User user) ->
                    user.getSyncStatus() == User.SyncStatus.SYNCED &&
                            user.getLastSyncedAt() != null &&
                            user.getRetryCount() == 0
            ));
        }
    }

    @Nested
    @DisplayName("syncUserToExternalApi - Failure Scenarios")
    class SyncFailureTests {

        @Test
        @DisplayName("Should mark user as FAILED and increment retry count on sync failure")
        void shouldMarkUserAsFailed_whenSyncFails() {
            // Given
            User pendingUser = User.builder()
                    .userId("user-1")
                    .username("user1")
                    .syncStatus(User.SyncStatus.PENDING)
                    .retryCount(0)
                    .build();

            when(userRepository.findBySyncStatusAndRetryCountLessThan(User.SyncStatus.PENDING, 3))
                    .thenReturn(List.of(pendingUser));
            when(userRepository.findBySyncStatusAndRetryCountLessThan(User.SyncStatus.CHANGED, 3))
                    .thenReturn(List.of());
            when(userRepository.findBySyncStatusAndRetryCountLessThan(User.SyncStatus.FAILED, 3))
                    .thenReturn(List.of());
            doThrow(new RuntimeException("API Error")).when(externalApiService).pushUser(any(User.class));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            int syncedCount = userSyncService.syncPendingUsers();

            // Then
            assertThat(syncedCount).isEqualTo(0);
            verify(userRepository).save(argThat((User user) ->
                    user.getSyncStatus() == User.SyncStatus.FAILED &&
                            user.getRetryCount() == 1
            ));
        }

        @Test
        @DisplayName("Should increment existing retry count on subsequent failure")
        void shouldIncrementRetryCount_onSubsequentFailure() {
            // Given
            User failedUser = User.builder()
                    .userId("user-1")
                    .username("user1")
                    .syncStatus(User.SyncStatus.FAILED)
                    .retryCount(2)
                    .build();

            when(userRepository.findBySyncStatusAndRetryCountLessThan(User.SyncStatus.PENDING, 3))
                    .thenReturn(List.of());
            when(userRepository.findBySyncStatusAndRetryCountLessThan(User.SyncStatus.CHANGED, 3))
                    .thenReturn(List.of());
            when(userRepository.findBySyncStatusAndRetryCountLessThan(User.SyncStatus.FAILED, 3))
                    .thenReturn(List.of(failedUser));
            doThrow(new RuntimeException("API Error")).when(externalApiService).pushUser(any(User.class));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            userSyncService.syncPendingUsers();

            // Then
            verify(userRepository).save(argThat((User user) ->
                    user.getSyncStatus() == User.SyncStatus.FAILED &&
                            user.getRetryCount() == 3
            ));
        }
    }

    @Nested
    @DisplayName("calculateDataHash Tests")
    class DataHashTests {

        @Test
        @DisplayName("Should generate consistent hash for same data")
        void shouldGenerateConsistentHash_forSameData() {
            // Given
            when(userRepository.findById(anyString())).thenReturn(Optional.empty());

            // When - process same event twice
            userSyncService.processUserEvent(testEvent);

            // Then - capture the saved user
            var captor = org.mockito.ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            String firstHash = captor.getValue().getDataHash();

            // Reset and process again
            reset(userRepository);
            when(userRepository.findById(anyString())).thenReturn(Optional.empty());
            userSyncService.processUserEvent(testEvent);

            var captor2 = org.mockito.ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor2.capture());
            String secondHash = captor2.getValue().getDataHash();

            assertThat(firstHash).isEqualTo(secondHash);
        }

        @Test
        @DisplayName("Should generate different hashes for different data")
        void shouldGenerateDifferentHash_forDifferentData() {
            // Given
            UserEvent event1 = UserEvent.builder()
                    .userId("user-1")
                    .username("user1")
                    .email("user1@example.com")
                    .eventType(UserEvent.EventType.CREATE)
                    .build();

            UserEvent event2 = UserEvent.builder()
                    .userId("user-2")
                    .username("user2")
                    .email("user2@example.com")
                    .eventType(UserEvent.EventType.CREATE)
                    .build();

            when(userRepository.findById(anyString())).thenReturn(Optional.empty());

            // When
            userSyncService.processUserEvent(event1);
            var captor1 = org.mockito.ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor1.capture());
            String hash1 = captor1.getValue().getDataHash();

            reset(userRepository);
            when(userRepository.findById(anyString())).thenReturn(Optional.empty());
            userSyncService.processUserEvent(event2);

            var captor2 = org.mockito.ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor2.capture());
            String hash2 = captor2.getValue().getDataHash();

            // Then
            assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        @DisplayName("Should include all user fields in hash calculation")
        void shouldIncludeAllFields_inHashCalculation() {
            // Given - Create two events that differ only by email
            UserEvent event1 = UserEvent.builder()
                    .userId("same-user")
                    .username("sameuser")
                    .email("email1@example.com")
                    .firstName("Same")
                    .lastName("Name")
                    .phoneNumber("+1234567890")
                    .eventType(UserEvent.EventType.CREATE)
                    .build();

            UserEvent event2 = UserEvent.builder()
                    .userId("same-user")
                    .username("sameuser")
                    .email("email2@example.com") // Different email
                    .firstName("Same")
                    .lastName("Name")
                    .phoneNumber("+1234567890")
                    .eventType(UserEvent.EventType.CREATE)
                    .build();

            when(userRepository.findById(anyString())).thenReturn(Optional.empty());

            // When
            userSyncService.processUserEvent(event1);
            var captor1 = org.mockito.ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor1.capture());
            String hash1 = captor1.getValue().getDataHash();

            reset(userRepository);
            when(userRepository.findById(anyString())).thenReturn(Optional.empty());
            userSyncService.processUserEvent(event2);

            var captor2 = org.mockito.ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor2.capture());
            String hash2 = captor2.getValue().getDataHash();

            // Then
            assertThat(hash1).isNotEqualTo(hash2);
        }
    }

    /**
     * Helper method to calculate data hash the same way as the service.
     * Used for testing that unchanged data doesn't trigger updates.
     */
    private String calculateDataHash(UserEvent event) {
        try {
            String data = String.format("%s|%s|%s|%s|%s|%s",
                    event.getUserId(),
                    event.getUsername(),
                    event.getEmail(),
                    event.getFirstName(),
                    event.getLastName(),
                    event.getPhoneNumber()
            );

            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to calculate data hash", e);
        }
    }
}
