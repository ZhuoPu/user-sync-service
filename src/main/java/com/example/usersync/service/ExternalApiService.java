package com.example.usersync.service;

import com.example.usersync.entity.User;
import com.example.usersync.dto.UserDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalApiService {

    private final RestTemplate restTemplate;

    @Value("${app.rest-api.base-url}")
    private String baseUrl;

    @Value("${app.rest-api.sync-endpoint}")
    private String syncEndpoint;

    public void pushUser(User user) {
        String url = baseUrl + syncEndpoint + "/" + user.getUserId();

        UserDto userDto = UserDto.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNumber(user.getPhoneNumber())
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<UserDto> request = new HttpEntity<>(userDto, headers);

        log.info("Sending PUT request to: {}", url);
        restTemplate.exchange(url, HttpMethod.PUT, request, Void.class);
    }
}
