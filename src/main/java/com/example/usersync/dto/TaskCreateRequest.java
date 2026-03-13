package com.example.usersync.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a Task.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskCreateRequest {

    @NotBlank(message = "serviceName is required")
    private String serviceName;

    @NotBlank(message = "methodName is required")
    private String methodName;

    @NotBlank(message = "dtoJson is required")
    private String dtoJson;
}
