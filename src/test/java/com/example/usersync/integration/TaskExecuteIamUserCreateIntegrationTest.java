package com.example.usersync.integration;

import com.example.usersync.dto.IamUserDto;
import com.example.usersync.dto.IamUserSearchResponse;
import com.example.usersync.dto.TaskCreateRequest;
import com.example.usersync.dto.TaskDto;
import com.example.usersync.entity.Task;
import com.example.usersync.service.IamUserService;
import com.example.usersync.service.TaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 集成测试：创建 Task 调用 IAM UserService create 方法，
 * 执行任务，验证用户创建、任务状态，最后清理。
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite::memory:",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DisplayName("Task Execute IAM User Create Integration Test")
class TaskExecuteIamUserCreateIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(TaskExecuteIamUserCreateIntegrationTest.class);

    @Autowired
    private TaskService taskService;

    @Autowired
    private IamUserService iamUserService;

    private String uniqueEmployeeId;
    private IamUserDto testUser;
    private TaskDto createdTask;

    @BeforeEach
    void setUp() {
        // 生成唯一的 employeeId 避免冲突
        uniqueEmployeeId = "TEST_IAM_TASK_" + System.currentTimeMillis();

        // 准备测试用户数据
        testUser = IamUserDto.builder()
                .title("测试任务用户")
                .employeeId(uniqueEmployeeId)
                .phone("13900000001")
                .system("IAM")
                .email("task-test@example.com")
                .active(true)
                .build();
    }

    @AfterEach
    void tearDown() {
        // 清理：删除创建的 IAM 用户
        if (uniqueEmployeeId != null) {
            try {
                IamUserSearchResponse response = iamUserService.search(
                        com.example.usersync.dto.IamUserSearchRequest.builder().pageNum(1).build());

                response.getRecords().stream()
                        .filter(u -> uniqueEmployeeId.equals(u.getEmployeeId()))
                        .forEach(u -> {
                            log.info("Cleaning up IAM user: recordId={}, employeeId={}",
                                    u.getRecordId(), u.getEmployeeId());
                            iamUserService.delete(u.getRecordId());
                        });
            } catch (Exception e) {
                log.warn("Failed to cleanup IAM user: {}", e.getMessage());
            }
        }

        // 清理：删除创建的任务
        if (createdTask != null) {
            try {
                taskService.delete(createdTask.getId());
            } catch (Exception e) {
                log.warn("Failed to cleanup task: {}", e.getMessage());
            }
        }
    }

    @Test
    @DisplayName("完整流程：创建任务 -> 执行任务 -> 验证用户创建 -> 验证任务状态 -> 清理")
    void shouldCompleteFullWorkflow() {
        // ==================== Step 1: 创建任务 ====================
        log.info("=== Step 1: 创建任务 ===");

        TaskCreateRequest taskRequest = TaskCreateRequest.builder()
                .serviceName("iamUserService")
                .methodName("create")
                .dtoJson("{\"title\":\"测试任务用户\",\"employeeId\":\"" + uniqueEmployeeId + "\",\"phone\":\"13900000001\",\"system\":\"IAM\",\"email\":\"task-test@example.com\",\"active\":true}")
                .build();

        createdTask = taskService.create(taskRequest);

        assertThat(createdTask).isNotNull();
        assertThat(createdTask.getId()).isNotNull();
        assertThat(createdTask.getServiceName()).isEqualTo("iamUserService");
        assertThat(createdTask.getMethodName()).isEqualTo("create");
        assertThat(createdTask.getStatus()).isEqualTo(TaskDto.TaskStatus.TODO);
        assertThat(createdTask.getResult()).isNull();
        assertThat(createdTask.getResultTimestamp()).isNull();

        log.info("任务创建成功: id={}, service={}, method={}, status={}",
                createdTask.getId(), createdTask.getServiceName(),
                createdTask.getMethodName(), createdTask.getStatus());

        // ==================== Step 2: 执行任务 ====================
        log.info("=== Step 2: 执行任务 ===");

        TaskDto executedTask = taskService.execute(createdTask.getId());

        assertThat(executedTask).isNotNull();
        assertThat(executedTask.getId()).isEqualTo(createdTask.getId());
        assertThat(executedTask.getStatus()).isEqualTo(TaskDto.TaskStatus.DONE);
        assertThat(executedTask.getResult()).isNotNull();
        assertThat(executedTask.getResult()).contains("SUCCESS"); // 应该成功
        assertThat(executedTask.getResultTimestamp()).isNotNull();

        log.info("任务执行完成: status={}, result={}", executedTask.getStatus(), executedTask.getResult());

        // ==================== Step 3: 验证 IAM 用户创建成功 ====================
        log.info("=== Step 3: 验证 IAM 用户创建成功 ===");

        // 等待 AITable 索引更新
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        IamUserSearchResponse searchResponse = iamUserService.search(
                com.example.usersync.dto.IamUserSearchRequest.builder().pageNum(1).build());

        assertThat(searchResponse.getRecords()).isNotEmpty();

        // 查找创建的用户
        IamUserDto createdUser = searchResponse.getRecords().stream()
                .filter(u -> uniqueEmployeeId.equals(u.getEmployeeId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("IAM user not found after task execution"));

        assertThat(createdUser.getRecordId()).isNotNull();
        assertThat(createdUser.getEmployeeId()).isEqualTo(uniqueEmployeeId);
        assertThat(createdUser.getTitle()).isEqualTo("测试任务用户");
        assertThat(createdUser.getPhone()).isEqualTo("13900000001");
        assertThat(createdUser.getSystem()).isEqualTo("IAM");
        assertThat(createdUser.getActive()).isTrue();

        log.info("IAM 用户验证成功: recordId={}, employeeId={}, title={}",
                createdUser.getRecordId(), createdUser.getEmployeeId(), createdUser.getTitle());

        // ==================== Step 4: 验证任务状态为 DONE ====================
        log.info("=== Step 4: 验证任务状态 ===");

        TaskDto finalTask = taskService.findById(createdTask.getId());

        assertThat(finalTask.getStatus()).isEqualTo(TaskDto.TaskStatus.DONE);
        assertThat(finalTask.getResult()).isNotNull();
        assertThat(finalTask.getResult()).contains("SUCCESS");

        log.info("任务状态验证完成: status={}, result={}", finalTask.getStatus(), finalTask.getStatus());

        // ==================== Step 5: 删除 IAM 用户 ====================
        log.info("=== Step 5: 删除 IAM 用户 ===");

        boolean deleted = iamUserService.delete(createdUser.getRecordId());

        assertThat(deleted).isTrue();

        log.info("IAM 用户删除成功: recordId={}", createdUser.getRecordId());

        // ==================== Step 6: 验证用户已删除 ====================
        log.info("=== Step 6: 验证用户已删除 ===");

        // 等待索引更新
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        IamUserSearchResponse afterDeleteResponse = iamUserService.search(
                com.example.usersync.dto.IamUserSearchRequest.builder().pageNum(1).build());

        boolean userExists = afterDeleteResponse.getRecords().stream()
                .anyMatch(u -> uniqueEmployeeId.equals(u.getEmployeeId()));

        assertThat(userExists).isFalse();

        log.info("=== 测试流程完成 ===");
    }

    @Test
    @DisplayName("任务执行失败时应该记录错误")
    void shouldRecordError_whenTaskExecutionFails() {
        // Given - 创建一个无效的任务（不存在的 service）
        TaskCreateRequest taskRequest = TaskCreateRequest.builder()
                .serviceName("nonExistentService")
                .methodName("create")
                .dtoJson("{}")
                .build();

        TaskDto task = taskService.create(taskRequest);

        // When - 执行任务
        TaskDto executedTask = taskService.execute(task.getId());

        // Then - 应该失败
        assertThat(executedTask.getStatus()).isEqualTo(TaskDto.TaskStatus.DONE);
        assertThat(executedTask.getResult()).isNotNull();
        assertThat(executedTask.getResult()).contains("ERROR");

        log.info("任务执行失败被正确记录: result={}", executedTask.getResult());
    }
}
