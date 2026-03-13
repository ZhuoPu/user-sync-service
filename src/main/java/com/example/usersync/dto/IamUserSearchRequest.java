package com.example.usersync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for searching IAM users.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IamUserSearchRequest {
    private Integer pageNum;
}
