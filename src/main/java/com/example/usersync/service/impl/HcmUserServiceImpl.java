package com.example.usersync.service.impl;

import com.example.usersync.dto.HcmUserDto;
import com.example.usersync.dto.HcmUserSearchRequest;
import com.example.usersync.dto.HcmUserSearchResponse;
import com.example.usersync.dto.UserEvent;
import com.example.usersync.repository.HcmUserRepository;
import com.example.usersync.service.HcmUserService;
import com.example.usersync.service.KafkaEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Service implementation for HCM user operations.
 */
@Slf4j
@Service("hcmUserService")
@RequiredArgsConstructor
public class HcmUserServiceImpl implements HcmUserService {

    private final HcmUserRepository hcmUserRepository;
    private final KafkaEventProducer kafkaEventProducer;

    @Override
    public HcmUserSearchResponse search(HcmUserSearchRequest request) {
        log.debug("Searching HCM users with request: {}", request);
        return hcmUserRepository.search(request);
    }

    @Override
    public HcmUserDto create(HcmUserDto user) {
        log.debug("Creating HCM user: {}", user);
        return hcmUserRepository.create(user);
    }

    @Override
    public HcmUserDto update(HcmUserDto user) {
        log.debug("Updating HCM user: {}", user);
        return hcmUserRepository.update(user);
    }

    @Override
    public int delete(List<String> recordIds) {
        log.debug("Deleting HCM users with recordIds: {}", recordIds);
        return hcmUserRepository.delete(recordIds);
    }

    @Override
    public boolean delete(String recordId) {
        log.debug("Deleting HCM user with recordId: {}", recordId);
        return hcmUserRepository.delete(recordId);
    }

    @Override
    public HcmUserDto onboard(HcmUserDto user) {
        log.info("Onboarding HCM user: {}", user);
        // 1. 先创建用户到 AITable
        HcmUserDto createdUser = hcmUserRepository.create(user);

        // 2. 成功后发送 Kafka 消息（使用原始输入的 user 数据，因为 AITable 返回可能不包含所有字段）
        UserEvent event = UserEvent.builder()
                .eventType(UserEvent.EventType.HCM_USER_ONBOARDED)
                .employeeId(user.getEmployeeId())
                .title(user.getTitle())
                .phone(user.getPhone())
                .email(user.getEmail())
                .system(user.getSystem())
                .active(user.getActive())
                .eventTimestamp(Instant.now())
                .build();
        kafkaEventProducer.sendUserEvent(event);
        log.info("Sent Kafka event for onboarded user: {}", event.getEmployeeId());

        return createdUser;
    }
}
