package com.example.usersync.integration;

import com.example.usersync.dto.HcmUserDto;
import com.example.usersync.dto.UserEvent;
import com.example.usersync.service.HcmUserService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HCM 用户入职 Kafka 集成测试
 * 使用 EmbeddedKafka 测试，独立 topic，不消费消息但能断言内容
 */
@SpringBootTest
@EmbeddedKafka(topics = {"hcm-user-onboard-test"}, ports = 0)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "app.kafka.topic=hcm-user-onboard-test"
})
@DisplayName("HCM User Kafka Integration Test")
class HcmUserKafkaIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(HcmUserKafkaIntegrationTest.class);

    @Autowired
    private HcmUserService hcmUserService;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Value("${app.kafka.topic}")
    private String testTopic;

    private Consumer<String, UserEvent> consumer;
    private String uniqueEmployeeId;

    @BeforeEach
    void setUp() {
        uniqueEmployeeId = "TEST_ONBOARD_" + System.currentTimeMillis();

        // 创建消费者用于验证消息（不使用 @KafkaListener）
        Map<String, Object> props = KafkaTestUtils.consumerProps(
                embeddedKafka.getBrokersAsString(), "test-group", "false");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");

        consumer = new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new JsonDeserializer<>(UserEvent.class, false)
        ).createConsumer();
        consumer.subscribe(java.util.List.of(testTopic));
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
        // 清理测试数据
        try {
            var response = hcmUserService.search(
                    com.example.usersync.dto.HcmUserSearchRequest.builder().pageNum(1).build());
            response.getRecords().stream()
                    .filter(u -> uniqueEmployeeId.equals(u.getEmployeeId()))
                    .forEach(u -> hcmUserService.delete(u.getRecordId()));
        } catch (Exception e) {
            log.warn("Cleanup failed: {}", e.getMessage());
        }
    }

    @Test
    @DisplayName("入职用户：先写AITable，成功后发送Kafka消息")
    void shouldOnboardUser_andSendKafkaMessage() {
        // Given - 测试用户数据
        HcmUserDto testUser = HcmUserDto.builder()
                .title("测试入职")
                .employeeId(uniqueEmployeeId)
                .phone("13900000000")
                .system("HCM")
                .email("test@example.com")
                .active(true)   // 在职
                .build();

        // When - 执行入职（写AITable + 发Kafka）
        HcmUserDto result = hcmUserService.onboard(testUser);

        // Then - 验证 AITable 写入成功
        assertThat(result.getRecordId()).isNotNull();
        assertThat(result.getEmployeeId()).isEqualTo(uniqueEmployeeId);

        // Then - 验证 Kafka 消息（直接读取，不通过 @KafkaListener）
        ConsumerRecords<String, UserEvent> records = consumer.poll(Duration.ofSeconds(5));
        assertThat(records.count()).isGreaterThan(0);

        UserEvent event = records.iterator().next().value();
        assertThat(event.getEventType()).isEqualTo(UserEvent.EventType.HCM_USER_ONBOARDED);
        assertThat(event.getEmployeeId()).isEqualTo(uniqueEmployeeId);
        assertThat(event.getTitle()).isEqualTo("测试入职");
        assertThat(event.getPhone()).isEqualTo("13900000000");
        assertThat(event.getEmail()).isEqualTo("test@example.com");
        assertThat(event.getActive()).isTrue();   // 验证在职状态
        assertThat(event.getEventTimestamp()).isNotNull();

        log.info("测试通过: 用户 {} 入职成功，Kafka消息已发送", uniqueEmployeeId);
    }
}
