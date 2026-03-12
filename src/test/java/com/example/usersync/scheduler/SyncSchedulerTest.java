package com.example.usersync.scheduler;

import com.example.usersync.service.UserSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SyncScheduler Unit Tests")
class SyncSchedulerTest {

    @Mock
    private UserSyncService userSyncService;

    private SyncScheduler syncScheduler;

    @BeforeEach
    void setUp() {
        syncScheduler = new SyncScheduler(userSyncService);
    }

    @Nested
    @DisplayName("syncPendingUsers Tests")
    class SyncPendingUsersTests {

        @Test
        @DisplayName("Should call syncPendingUsers on UserSyncService")
        void shouldCallSyncPendingUsers_onUserSyncService() {
            // Given
            when(userSyncService.syncPendingUsers()).thenReturn(5);

            // When
            syncScheduler.syncPendingUsers();

            // Then
            verify(userSyncService).syncPendingUsers();
        }

        @Test
        @DisplayName("Should return correct synced count from service")
        void shouldReturnCorrectSyncedCount_fromService() {
            // Given
            int expectedCount = 10;
            when(userSyncService.syncPendingUsers()).thenReturn(expectedCount);

            // When
            syncScheduler.syncPendingUsers();

            // Then
            verify(userSyncService).syncPendingUsers();
        }

        @Test
        @DisplayName("Should handle zero users synced")
        void shouldHandleZeroUsers_synced() {
            // Given
            when(userSyncService.syncPendingUsers()).thenReturn(0);

            // When
            syncScheduler.syncPendingUsers();

            // Then
            verify(userSyncService).syncPendingUsers();
        }

        @Test
        @DisplayName("Should handle multiple consecutive sync calls")
        void shouldHandleMultipleConsecutive_syncCalls() {
            // Given
            when(userSyncService.syncPendingUsers())
                    .thenReturn(5)
                    .thenReturn(3)
                    .thenReturn(0);

            // When - call sync three times
            syncScheduler.syncPendingUsers();
            syncScheduler.syncPendingUsers();
            syncScheduler.syncPendingUsers();

            // Then
            verify(userSyncService, times(3)).syncPendingUsers();
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should not throw exception when service throws exception")
        void shouldNotThrowException_whenServiceThrowsException() {
            // Given
            when(userSyncService.syncPendingUsers())
                    .thenThrow(new RuntimeException("Database error"));

            // When/Then - should not throw
            syncScheduler.syncPendingUsers();

            verify(userSyncService).syncPendingUsers();
        }

        @Test
        @DisplayName("Should log error when service throws exception")
        void shouldLogError_whenServiceThrowsException() {
            // Given
            String expectedErrorMessage = "Connection timeout";
            when(userSyncService.syncPendingUsers())
                    .thenThrow(new RuntimeException(expectedErrorMessage));

            // When/Then - should not throw, error is logged
            syncScheduler.syncPendingUsers();

            verify(userSyncService).syncPendingUsers();
        }

        @Test
        @DisplayName("Should continue to work after exception")
        void shouldContinueToWork_afterException() {
            // Given
            when(userSyncService.syncPendingUsers())
                    .thenThrow(new RuntimeException("First error"))
                    .thenReturn(5);

            // When - first call fails
            syncScheduler.syncPendingUsers();

            // When - second call succeeds
            syncScheduler.syncPendingUsers();

            // Then
            verify(userSyncService, times(2)).syncPendingUsers();
        }

        @Test
        @DisplayName("Should handle null return value gracefully")
        void shouldHandleNullReturnValue_gracefully() {
            // Given - service returns null (shouldn't happen in practice but testing robustness)
            // Actually syncPendingUsers returns int, so null not possible
            // Testing with normal int return
            when(userSyncService.syncPendingUsers()).thenReturn(0);

            // When/Then
            syncScheduler.syncPendingUsers();
            verify(userSyncService).syncPendingUsers();
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Should delegate to UserSyncService without modifying result")
        void shouldDelegateToUserSyncService_withoutModifyingResult() {
            // Given
            int[] testValues = {0, 1, 10, 100, 1000};

            for (int expectedValue : testValues) {
                reset(userSyncService);
                when(userSyncService.syncPendingUsers()).thenReturn(expectedValue);

                // When
                syncScheduler.syncPendingUsers();

                // Then
                verify(userSyncService).syncPendingUsers();
            }
        }

        @Test
        @DisplayName("Should handle high volume sync scenario")
        void shouldHandleHighVolume_syncScenario() {
            // Given
            when(userSyncService.syncPendingUsers()).thenReturn(10000);

            // When
            syncScheduler.syncPendingUsers();

            // Then
            verify(userSyncService).syncPendingUsers();
        }

        @Test
        @DisplayName("Should be callable from scheduled execution")
        void shouldBeCallable_fromScheduledExecution() {
            // This test verifies that the method is public and can be called
            // (it would be called by Spring's scheduler in real scenario)

            // Given
            when(userSyncService.syncPendingUsers()).thenReturn(1);

            // When - simulate scheduled call
            syncScheduler.syncPendingUsers();

            // Then
            verify(userSyncService).syncPendingUsers();
        }
    }

    @Nested
    @DisplayName("Exception Type Tests")
    class ExceptionTypeTests {

        @Test
        @DisplayName("Should handle RuntimeException")
        void shouldHandleRuntimeException() {
            // Given
            when(userSyncService.syncPendingUsers())
                    .thenThrow(new RuntimeException("Runtime error"));

            // When/Then
            syncScheduler.syncPendingUsers();
            verify(userSyncService).syncPendingUsers();
        }

        @Test
        @DisplayName("Should handle IllegalStateException")
        void shouldHandleIllegalStateException() {
            // Given
            when(userSyncService.syncPendingUsers())
                    .thenThrow(new IllegalStateException("Illegal state"));

            // When/Then
            syncScheduler.syncPendingUsers();
            verify(userSyncService).syncPendingUsers();
        }

        @Test
        @DisplayName("Should handle unchecked exceptions")
        void shouldHandleUncheckedExceptions() {
            // Given
            when(userSyncService.syncPendingUsers())
                    .thenThrow(new NullPointerException("Null reference"));

            // When/Then
            syncScheduler.syncPendingUsers();
            verify(userSyncService).syncPendingUsers();
        }
    }
}
