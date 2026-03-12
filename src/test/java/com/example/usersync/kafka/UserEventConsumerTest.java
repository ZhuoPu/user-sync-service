package com.example.usersync.kafka;

import com.example.usersync.dto.UserEvent;
import com.example.usersync.service.UserSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserEventConsumer Unit Tests")
class UserEventConsumerTest {

    @Mock
    private UserSyncService userSyncService;

    private UserEventConsumer userEventConsumer;

    private UserEvent testEvent;

    @BeforeEach
    void setUp() {
        userEventConsumer = new UserEventConsumer(userSyncService);

        testEvent = UserEvent.builder()
                .userId("user-123")
                .username("testuser")
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .phoneNumber("+1234567890")
                .eventType(UserEvent.EventType.CREATE)
                .eventTimestamp(Instant.now())
                .dataVersion(1L)
                .build();
    }

    @Nested
    @DisplayName("consumeUserEvent Tests")
    class ConsumeUserEventTests {

        @Test
        @DisplayName("Should call UserSyncService to process the event")
        void shouldCallUserSyncService_toProcessEvent() {
            // Given
            String topic = "user-events";
            String key = "user-123";
            int partition = 0;
            long offset = 100L;

            // When
            userEventConsumer.consumeUserEvent(testEvent, topic, key, partition, offset);

            // Then
            verify(userSyncService).processUserEvent(testEvent);
        }

        @Test
        @DisplayName("Should pass the correct UserEvent to service")
        void shouldPassCorrectUserEvent_toService() {
            // Given
            ArgumentCaptor<UserEvent> eventCaptor = ArgumentCaptor.forClass(UserEvent.class);

            // When
            userEventConsumer.consumeUserEvent(testEvent, "user-events", "user-123", 0, 100L);

            // Then
            verify(userSyncService).processUserEvent(eventCaptor.capture());
            UserEvent capturedEvent = eventCaptor.getValue();

            assertThat(capturedEvent.getUserId()).isEqualTo("user-123");
            assertThat(capturedEvent.getUsername()).isEqualTo("testuser");
            assertThat(capturedEvent.getEmail()).isEqualTo("test@example.com");
            assertThat(capturedEvent.getEventType()).isEqualTo(UserEvent.EventType.CREATE);
        }

        @Test
        @DisplayName("Should process multiple events sequentially")
        void shouldProcessMultipleEvents_sequentially() {
            // Given
            UserEvent event1 = UserEvent.builder()
                    .userId("user-1")
                    .username("user1")
                    .eventType(UserEvent.EventType.CREATE)
                    .build();

            UserEvent event2 = UserEvent.builder()
                    .userId("user-2")
                    .username("user2")
                    .eventType(UserEvent.EventType.UPDATE)
                    .build();

            // When
            userEventConsumer.consumeUserEvent(event1, "user-events", "user-1", 0, 100L);
            userEventConsumer.consumeUserEvent(event2, "user-events", "user-2", 0, 101L);

            // Then
            verify(userSyncService).processUserEvent(event1);
            verify(userSyncService).processUserEvent(event2);
        }

        @Test
        @DisplayName("Should handle DELETE events")
        void shouldHandleDeleteEvents() {
            // Given
            UserEvent deleteEvent = UserEvent.builder()
                    .userId("user-123")
                    .eventType(UserEvent.EventType.DELETE)
                    .eventTimestamp(Instant.now())
                    .build();

            // When
            userEventConsumer.consumeUserEvent(deleteEvent, "user-events", "user-123", 0, 100L);

            // Then
            verify(userSyncService).processUserEvent(deleteEvent);
        }

        @Test
        @DisplayName("Should handle UPDATE events")
        void shouldHandleUpdateEvents() {
            // Given
            UserEvent updateEvent = UserEvent.builder()
                    .userId("user-123")
                    .username("updateduser")
                    .email("updated@example.com")
                    .eventType(UserEvent.EventType.UPDATE)
                    .eventTimestamp(Instant.now())
                    .dataVersion(2L)
                    .build();

            // When
            userEventConsumer.consumeUserEvent(updateEvent, "user-events", "user-123", 0, 100L);

            // Then
            verify(userSyncService).processUserEvent(updateEvent);
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should not throw exception when service throws exception")
        void shouldNotThrowException_whenServiceThrowsException() {
            // Given
            doThrow(new RuntimeException("Service error"))
                    .when(userSyncService).processUserEvent(any(UserEvent.class));

            // When/Then - should not throw
            userEventConsumer.consumeUserEvent(testEvent, "user-events", "user-123", 0, 100L);

            verify(userSyncService).processUserEvent(testEvent);
        }

        @Test
        @DisplayName("Should continue processing after exception")
        void shouldContinueProcessing_afterException() {
            // Given
            doThrow(new RuntimeException("Service error"))
                    .when(userSyncService).processUserEvent(any(UserEvent.class));

            // When - first event fails
            userEventConsumer.consumeUserEvent(testEvent, "user-events", "user-123", 0, 100L);

            // Reset for next call
            reset(userSyncService);

            UserEvent secondEvent = UserEvent.builder()
                    .userId("user-456")
                    .eventType(UserEvent.EventType.CREATE)
                    .build();

            // When - second event should be processed normally
            userEventConsumer.consumeUserEvent(secondEvent, "user-events", "user-456", 0, 101L);

            // Then
            verify(userSyncService).processUserEvent(secondEvent);
        }

        @Test
        @DisplayName("Should log error when service throws exception")
        void shouldLogError_whenServiceThrowsException() {
            // Given
            String expectedErrorMessage = "Database connection failed";
            doThrow(new RuntimeException(expectedErrorMessage))
                    .when(userSyncService).processUserEvent(any(UserEvent.class));

            // When/Then - should not throw, error is logged
            userEventConsumer.consumeUserEvent(testEvent, "user-events", "user-123", 0, 100L);

            verify(userSyncService).processUserEvent(testEvent);
        }
    }

    @Nested
    @DisplayName("Kafka Header Tests")
    class KafkaHeaderTests {

        @Test
        @DisplayName("Should accept different topic names")
        void shouldAcceptDifferentTopicNames() {
            // Given
            String[] topics = {"user-events", "user-events-v2", "production-user-events"};

            for (String topic : topics) {
                reset(userSyncService);

                // When
                userEventConsumer.consumeUserEvent(testEvent, topic, "user-123", 0, 100L);

                // Then
                verify(userSyncService).processUserEvent(testEvent);
            }
        }

        @Test
        @DisplayName("Should accept different partition values")
        void shouldAcceptDifferentPartitionValues() {
            // Given
            int[] partitions = {0, 1, 2, 5, 10};

            for (int partition : partitions) {
                reset(userSyncService);

                // When
                userEventConsumer.consumeUserEvent(testEvent, "user-events", "user-123", partition, 100L);

                // Then
                verify(userSyncService).processUserEvent(testEvent);
            }
        }

        @Test
        @DisplayName("Should accept different offset values")
        void shouldAcceptDifferentOffsetValues() {
            // Given
            long[] offsets = {0L, 100L, 1000L, Long.MAX_VALUE};

            for (long offset : offsets) {
                reset(userSyncService);

                // When
                userEventConsumer.consumeUserEvent(testEvent, "user-events", "user-123", 0, offset);

                // Then
                verify(userSyncService).processUserEvent(testEvent);
            }
        }

        @Test
        @DisplayName("Should accept different key values")
        void shouldAcceptDifferentKeyValues() {
            // Given
            String[] keys = {"user-123", "key-1", "", null};

            for (String key : keys) {
                reset(userSyncService);

                // When
                userEventConsumer.consumeUserEvent(testEvent, "user-events", key, 0, 100L);

                // Then
                verify(userSyncService).processUserEvent(testEvent);
            }
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle event with minimal required fields")
        void shouldHandleEvent_withMinimalRequiredFields() {
            // Given
            UserEvent minimalEvent = UserEvent.builder()
                    .userId("user-1")
                    .eventType(UserEvent.EventType.CREATE)
                    .build();

            // When
            userEventConsumer.consumeUserEvent(minimalEvent, "user-events", "user-1", 0, 100L);

            // Then
            ArgumentCaptor<UserEvent> eventCaptor = ArgumentCaptor.forClass(UserEvent.class);
            verify(userSyncService).processUserEvent(eventCaptor.capture());

            UserEvent capturedEvent = eventCaptor.getValue();
            assertThat(capturedEvent.getUserId()).isEqualTo("user-1");
            assertThat(capturedEvent.getEventType()).isEqualTo(UserEvent.EventType.CREATE);
        }

        @Test
        @DisplayName("Should handle event with all null optional fields")
        void shouldHandleEvent_withAllNullOptionalFields() {
            // Given
            UserEvent nullFieldsEvent = UserEvent.builder()
                    .userId("user-1")
                    .eventType(UserEvent.EventType.CREATE)
                    .username(null)
                    .email(null)
                    .firstName(null)
                    .lastName(null)
                    .phoneNumber(null)
                    .eventTimestamp(null)
                    .dataVersion(null)
                    .build();

            // When
            userEventConsumer.consumeUserEvent(nullFieldsEvent, "user-events", "user-1", 0, 100L);

            // Then
            verify(userSyncService).processUserEvent(nullFieldsEvent);
        }
    }
}
