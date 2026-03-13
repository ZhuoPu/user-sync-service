package com.example.usersync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO representing a Task.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDto {
    private Long id;
    private String serviceName;
    private String methodName;
    private String dtoJson;
    private TaskStatus status;
    private String result;
    private Instant resultTimestamp;
    private Instant createdAt;
    private Instant updatedAt;

    public enum TaskStatus {
        TODO,   // 待执行
        DOING,  // 执行中
        DONE    // 已完成（成功或失败）
    }
}
