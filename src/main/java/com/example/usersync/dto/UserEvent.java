package com.example.usersync.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEvent {

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("username")
    private String username;

    private String email;

    @JsonProperty("first_name")
    private String firstName;

    @JsonProperty("last_name")
    private String lastName;

    @JsonProperty("phone_number")
    private String phoneNumber;

    @JsonProperty("event_type")
    private EventType eventType;

    @JsonProperty("event_timestamp")
    private Instant eventTimestamp;

    @JsonProperty("data_version")
    private Long dataVersion;

    public enum EventType {
        CREATE, UPDATE, DELETE
    }
}
