package com.example.usersync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for IAM user search results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IamUserSearchResponse {
    private Integer total;
    private Integer pageNum;
    private Integer pageSize;
    private List<IamUserDto> records;
}
