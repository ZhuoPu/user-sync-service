package com.example.usersync.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Service for producing user events to Kafka.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topic:user-data-sync}")
    private String topic;

    /**
     * Send a user event to the configured Kafka topic.
     *
     * @param event the event to send
     */
    public void sendUserEvent(Object event) {
        log.debug("Sending user event to topic {}: {}", topic, event);
        kafkaTemplate.send(topic, event);
        log.info("Sent user event to topic: {}", topic);
    }
}
