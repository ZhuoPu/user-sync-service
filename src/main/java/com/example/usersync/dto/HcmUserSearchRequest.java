package com.example.usersync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for searching HCM users.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HcmUserSearchRequest {
    private Integer pageNum;
}
