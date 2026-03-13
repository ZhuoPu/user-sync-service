package com.example.usersync.entity;

import com.example.usersync.dto.TaskDto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entity representing a Task.
 */
@Entity
@Table(name = "tasks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_name", nullable = false)
    private String serviceName;

    @Column(name = "method_name", nullable = false)
    private String methodName;

    @Column(name = "dto_json", nullable = false, length = 2000)
    private String dtoJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private TaskStatus status = TaskStatus.TODO;

    @Column(name = "result", length = 2000)
    private String result;

    @Column(name = "result_timestamp")
    private Instant resultTimestamp;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum TaskStatus {
        TODO,   // 待执行
        DOING,  // 执行中
        DONE    // 已完成（成功或失败）
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Convert to DTO.
     */
    public TaskDto toDto() {
        return TaskDto.builder()
                .id(id)
                .serviceName(serviceName)
                .methodName(methodName)
                .dtoJson(dtoJson)
                .status(TaskDto.TaskStatus.valueOf(status.name()))
                .result(result)
                .resultTimestamp(resultTimestamp)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    /**
     * Create from DTO.
     */
    public static Task fromDto(TaskDto dto) {
        return Task.builder()
                .id(dto.getId())
                .serviceName(dto.getServiceName())
                .methodName(dto.getMethodName())
                .dtoJson(dto.getDtoJson())
                .status(TaskStatus.valueOf(dto.getStatus().name()))
                .result(dto.getResult())
                .resultTimestamp(dto.getResultTimestamp())
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .build();
    }
}
