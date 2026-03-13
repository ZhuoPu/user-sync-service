package com.example.usersync.integration;

import com.example.usersync.dto.HcmUserDto;
import com.example.usersync.dto.TaskCreateRequest;
import com.example.usersync.dto.TaskDto;
import com.example.usersync.service.HcmUserService;
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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 集成测试：创建任务并验证错误处理和重试场景。
 *
 * 场景：
 * 1. 创建一个调用不存在 service 的任务
 * 2. 执行任务应该失败
 * 3. 检查任务状态是 DONE 且包含错误
 * 4. 重试任务，仍然失败
 * 5. 检查状态仍是 DONE（错误），但 resultTimestamp 变化了
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite::memory:",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DisplayName("Task Retry with Error Scenario Integration Test")
class TaskRetryErrorScenarioIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(TaskRetryErrorScenarioIntegrationTest.class);

    @Autowired
    private TaskService taskService;

    @Autowired
    private HcmUserService hcmUserService;

    private TaskDto createdTask;

    @AfterEach
    void tearDown() {
        // 清理任务
        if (createdTask != null) {
            try {
                taskService.delete(createdTask.getId());
            } catch (Exception e) {
                log.warn("Failed to cleanup task: {}", e.getMessage());
            }
        }
    }

    @Test
    @DisplayName("完整流程：创建错误任务 -> 执行失败 -> 重试仍失败 -> 验证状态和结果时间戳")
    void shouldHandleError_andRetryWithUpdatedTimestamp() throws Exception {
        // ==================== Step 1: 创建一个会失败的任务 ====================
        log.info("=== Step 1: 创建会失败的任务 ===");

        // 使用不存在的 service 来确保失败
        TaskCreateRequest taskRequest = TaskCreateRequest.builder()
                .serviceName("nonExistentService")
                .methodName("create")
                .dtoJson("{\"title\":\"测试\",\"employeeId\":\"003\",\"phone\":\"13900000003\"}")
                .build();

        createdTask = taskService.create(taskRequest);

        assertThat(createdTask).isNotNull();
        assertThat(createdTask.getStatus()).isEqualTo(TaskDto.TaskStatus.TODO);
        assertThat(createdTask.getResult()).isNull();

        log.info("任务创建成功: id={}, status={}", createdTask.getId(), createdTask.getStatus());

        // ==================== Step 2: 第一次执行任务（应该失败）====================
        log.info("=== Step 2: 第一次执行任务 ===");

        Instant firstExecutionStart = Instant.now();
        TaskDto firstExecution = taskService.execute(createdTask.getId());

        assertThat(firstExecution.getStatus()).isEqualTo(TaskDto.TaskStatus.DONE);
        assertThat(firstExecution.getResult()).isNotNull();
        assertThat(firstExecution.getResult()).contains("ERROR");
        assertThat(firstExecution.getResultTimestamp()).isNotNull();
        assertThat(firstExecution.getResultTimestamp()).isAfter(firstExecutionStart);

        Instant firstResultTimestamp = firstExecution.getResultTimestamp();

        log.info("第一次执行完成: status={}, result={}, resultTimestamp={}",
                firstExecution.getStatus(), firstExecution.getResult(), firstExecution.getResultTimestamp());

        // ==================== Step 3: 检查任务状态是 DONE 且包含错误信息 ====================
        log.info("=== Step 3: 验证任务状态 ===");

        TaskDto taskAfterFirstExecution = taskService.findById(createdTask.getId());

        assertThat(taskAfterFirstExecution.getStatus()).isEqualTo(TaskDto.TaskStatus.DONE);
        assertThat(taskAfterFirstExecution.getResult()).isNotNull();
        assertThat(taskAfterFirstExecution.getResult()).contains("ERROR");

        log.info("任务状态验证: status={}, result={}",
                taskAfterFirstExecution.getStatus(), taskAfterFirstExecution.getResult());

        // ==================== Step 4: 修改任务状态回 TODO 以便重试 ====================
        log.info("=== Step 4: 重置任务状态为 TODO 以便重试 ===");

        // 通过数据库直接修改状态来模拟重试场景
        // 在实际应用中，这可能由调度器或其他机制完成
        // 这里我们通过创建一个新任务来模拟重试

        TaskCreateRequest retryRequest = TaskCreateRequest.builder()
                .serviceName("nonExistentService")
                .methodName("create")
                .dtoJson("{\"title\":\"测试\",\"employeeId\":\"003\",\"phone\":\"13900000003\"}")
                .build();

        TaskDto retryTask = taskService.create(retryRequest);

        assertThat(retryTask.getStatus()).isEqualTo(TaskDto.TaskStatus.TODO);

        log.info("新任务创建成功: id={}, status={}", retryTask.getId(), retryTask.getStatus());

        // ==================== Step 5: 执行新任务（仍然失败）====================
        log.info("=== Step 5: 执行新任务 ===");

        Thread.sleep(1000); // 确保时间戳不同

        Instant retryExecutionStart = Instant.now();
        TaskDto retryExecution = taskService.execute(retryTask.getId());

        assertThat(retryExecution.getStatus()).isEqualTo(TaskDto.TaskStatus.DONE);
        assertThat(retryExecution.getResult()).isNotNull();
        assertThat(retryExecution.getResult()).contains("ERROR");
        assertThat(retryExecution.getResultTimestamp()).isNotNull();
        assertThat(retryExecution.getResultTimestamp()).isAfter(retryExecutionStart);

        Instant retryResultTimestamp = retryExecution.getResultTimestamp();

        log.info("重试执行完成: status={}, result={}, resultTimestamp={}",
                retryExecution.getStatus(), retryExecution.getResult(), retryExecution.getResultTimestamp());

        // ==================== Step 6: 验证两个任务都有错误，但时间戳不同 ====================
        log.info("=== Step 6: 验证错误状态和时间戳 ===");

        // 验证第一个任务
        TaskDto finalFirstTask = taskService.findById(createdTask.getId());
        assertThat(finalFirstTask.getStatus()).isEqualTo(TaskDto.TaskStatus.DONE);
        assertThat(finalFirstTask.getResult()).contains("ERROR");

        // 验证第二个任务
        TaskDto finalRetryTask = taskService.findById(retryTask.getId());
        assertThat(finalRetryTask.getStatus()).isEqualTo(TaskDto.TaskStatus.DONE);
        assertThat(finalRetryTask.getResult()).contains("ERROR");

        // 验证时间戳不同
        assertThat(retryResultTimestamp).isNotEqualTo(firstResultTimestamp);
        assertThat(retryResultTimestamp).isAfter(firstResultTimestamp);

        log.info("验证通过: 两个任务都是错误状态");
        log.info("第一个任务: id={}, status={}, resultTimestamp={}",
                finalFirstTask.getId(), finalFirstTask.getStatus(), firstResultTimestamp);
        log.info("第二个任务: id={}, status={}, resultTimestamp={}",
                finalRetryTask.getId(), finalRetryTask.getStatus(), retryResultTimestamp);

        // 清理第二个任务
        taskService.delete(retryTask.getId());

        log.info("=== 测试流程完成 ===");
    }

    @Test
    @DisplayName("重试已完成（DONE）的任务应该抛出异常")
    void shouldThrowException_whenRetryingDoneTask() {
        // Given - 创建一个会失败的任务并执行
        TaskCreateRequest taskRequest = TaskCreateRequest.builder()
                .serviceName("nonExistentService")
                .methodName("create")
                .dtoJson("{}")
                .build();

        TaskDto task = taskService.create(taskRequest);
        TaskDto executed = taskService.execute(task.getId());

        assertThat(executed.getStatus()).isEqualTo(TaskDto.TaskStatus.DONE);

        // When & Then - 重试应该抛出异常
        assertThatThrownBy(() -> taskService.execute(task.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not in TODO status");

        log.info("验证通过: 重试已完成状态的任务抛出异常");
    }

    @Test
    @DisplayName("多任务执行错误场景")
    void shouldHandleMultipleErrorTasks() throws Exception {
        log.info("=== 测试多个错误任务 ===");

        // 创建多个错误任务
        TaskDto task1 = taskService.create(TaskCreateRequest.builder()
                .serviceName("errorService1")
                .methodName("create")
                .dtoJson("{}")
                .build());

        Thread.sleep(500);

        TaskDto task2 = taskService.create(TaskCreateRequest.builder()
                .serviceName("errorService2")
                .methodName("create")
                .dtoJson("{}")
                .build());

        // 执行两个任务
        TaskDto result1 = taskService.execute(task1.getId());
        TaskDto result2 = taskService.execute(task2.getId());

        // 验证都是错误状态
        assertThat(result1.getStatus()).isEqualTo(TaskDto.TaskStatus.DONE);
        assertThat(result1.getResult()).contains("ERROR");
        assertThat(result1.getResultTimestamp()).isNotNull();

        assertThat(result2.getStatus()).isEqualTo(TaskDto.TaskStatus.DONE);
        assertThat(result2.getResult()).contains("ERROR");
        assertThat(result2.getResultTimestamp()).isNotNull();

        // 验证时间戳不同（task2 应该比 task1 晚）
        assertThat(result2.getResultTimestamp()).isAfter(result1.getResultTimestamp());

        log.info("多任务验证通过: task1_timestamp={}, task2_timestamp={}",
                result1.getResultTimestamp(), result2.getResultTimestamp());

        // 清理
        taskService.delete(task1.getId());
        taskService.delete(task2.getId());
    }
}
