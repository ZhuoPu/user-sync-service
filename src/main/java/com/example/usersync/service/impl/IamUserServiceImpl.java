package com.example.usersync.service.impl;

import com.example.usersync.dto.IamUserDto;
import com.example.usersync.dto.IamUserSearchRequest;
import com.example.usersync.dto.IamUserSearchResponse;
import com.example.usersync.repository.IamUserRepository;
import com.example.usersync.service.IamUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service implementation for IAM user operations.
 */
@Slf4j
@Service("iamUserService")
@RequiredArgsConstructor
public class IamUserServiceImpl implements IamUserService {

    private final IamUserRepository iamUserRepository;

    @Override
    public IamUserSearchResponse search(IamUserSearchRequest request) {
        log.debug("Searching IAM users with request: {}", request);
        return iamUserRepository.search(request);
    }

    @Override
    public IamUserDto create(IamUserDto user) {
        log.debug("Creating IAM user: {}", user);
        return iamUserRepository.create(user);
    }

    @Override
    public IamUserDto update(IamUserDto user) {
        log.debug("Updating IAM user: {}", user);
        return iamUserRepository.update(user);
    }

    @Override
    public int delete(List<String> recordIds) {
        log.debug("Deleting IAM users with recordIds: {}", recordIds);
        return iamUserRepository.delete(recordIds);
    }

    @Override
    public boolean delete(String recordId) {
        log.debug("Deleting IAM user with recordId: {}", recordId);
        return iamUserRepository.delete(recordId);
    }

    @Override
    public IamUserDto resign(String recordId) {
        log.debug("Resigning IAM user with recordId: {}", recordId);
        return iamUserRepository.resign(recordId);
    }
}
