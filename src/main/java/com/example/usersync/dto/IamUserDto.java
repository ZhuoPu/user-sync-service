package com.example.usersync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing an IAM system user from AITable.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IamUserDto {
    private String recordId;
    private String title;      // Title
    private String employeeId; // 工号
    private String phone;      // 电话
    private String system;     // 系统 (IAM)
    private String email;      // 邮箱
    private Boolean active;    // 在职
}
