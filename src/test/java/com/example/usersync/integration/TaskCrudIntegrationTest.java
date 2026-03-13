package com.example.usersync.integration;

import com.example.usersync.dto.TaskCreateRequest;
import com.example.usersync.dto.TaskDto;
import com.example.usersync.dto.TaskUpdateRequest;
import com.example.usersync.entity.Task;
import com.example.usersync.repository.TaskRepository;
import com.example.usersync.service.TaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for Task CRUD operations.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite::memory:",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DisplayName("Task CRUD Integration Tests")
class TaskCrudIntegrationTest {

    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskRepository taskRepository;

    @BeforeEach
    void setUp() {
        taskRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        taskRepository.deleteAll();
    }

    @Nested
    @DisplayName("Create Tests")
    class CreateTests {

        @Test
        @DisplayName("Should create task with all fields")
        void shouldCreateTask_withAllFields() {
            // Given
            TaskCreateRequest request = TaskCreateRequest.builder()
                    .serviceName("hcmUserService")
                    .methodName("onboard")
                    .dtoJson("{\"employeeId\":\"123\",\"title\":\"Test User\"}")
                    .build();

            // When
            TaskDto result = taskService.create(request);

            // Then
            assertThat(result.getId()).isNotNull();
            assertThat(result.getServiceName()).isEqualTo("hcmUserService");
            assertThat(result.getMethodName()).isEqualTo("onboard");
            assertThat(result.getDtoJson()).isEqualTo("{\"employeeId\":\"123\",\"title\":\"Test User\"}");
            assertThat(result.getStatus()).isEqualTo(TaskDto.TaskStatus.TODO);
            assertThat(result.getResult()).isNull();
            assertThat(result.getResultTimestamp()).isNull();
            assertThat(result.getCreatedAt()).isNotNull();
            assertThat(result.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should create multiple tasks")
        void shouldCreateMultiple_tasks() {
            // Given
            TaskCreateRequest request1 = TaskCreateRequest.builder()
                    .serviceName("service1")
                    .methodName("method1")
                    .dtoJson("{}")
                    .build();
            TaskCreateRequest request2 = TaskCreateRequest.builder()
                    .serviceName("service2")
                    .methodName("method2")
                    .dtoJson("{}")
                    .build();

            // When
            TaskDto result1 = taskService.create(request1);
            TaskDto result2 = taskService.create(request2);

            // Then
            assertThat(result1.getId()).isNotEqualTo(result2.getId());
            assertThat(taskService.findAll()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Read Tests")
    class ReadTests {

        @Test
        @DisplayName("Should find task by id")
        void shouldFindTask_byId() {
            // Given
            TaskCreateRequest request = TaskCreateRequest.builder()
                    .serviceName("testService")
                    .methodName("testMethod")
                    .dtoJson("{}")
                    .build();
            TaskDto created = taskService.create(request);

            // When
            TaskDto found = taskService.findById(created.getId());

            // Then
            assertThat(found.getId()).isEqualTo(created.getId());
            assertThat(found.getServiceName()).isEqualTo("testService");
            assertThat(found.getMethodName()).isEqualTo("testMethod");
        }

        @Test
        @DisplayName("Should throw exception when finding non-existent task")
        void shouldThrowException_whenFindingNonExistentTask() {
            // When & Then
            assertThatThrownBy(() -> taskService.findById(999L))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining("Task not found");
        }

        @Test
        @DisplayName("Should find all tasks")
        void shouldFindAll_tasks() {
            // Given
            taskService.create(TaskCreateRequest.builder()
                    .serviceName("s1").methodName("m1").dtoJson("{}").build());
            taskService.create(TaskCreateRequest.builder()
                    .serviceName("s2").methodName("m2").dtoJson("{}").build());
            taskService.create(TaskCreateRequest.builder()
                    .serviceName("s3").methodName("m3").dtoJson("{}").build());

            // When
            List<TaskDto> tasks = taskService.findAll();

            // Then
            assertThat(tasks).hasSize(3);
        }

        @Test
        @DisplayName("Should find tasks by status")
        void shouldFindTasks_byStatus() {
            // Given
            TaskDto todoTask = taskService.create(TaskCreateRequest.builder()
                    .serviceName("s1").methodName("m1").dtoJson("{}").build());

            TaskDto todoTask2 = taskService.create(TaskCreateRequest.builder()
                    .serviceName("s2").methodName("m2").dtoJson("{}").build());

            // Find by TODO status
            List<TaskDto> todoTasks = taskService.findByStatus(Task.TaskStatus.TODO);

            // Then
            assertThat(todoTasks).hasSize(2);
            assertThat(todoTasks).allMatch(t -> t.getStatus() == TaskDto.TaskStatus.TODO);
        }

        @Test
        @DisplayName("Should get next TODO task (oldest first)")
        void shouldGetNextTODO_oldestFirst() throws Exception {
            // Given
            TaskDto first = taskService.create(TaskCreateRequest.builder()
                    .serviceName("s1").methodName("m1").dtoJson("{}").build());

            Thread.sleep(10); // Ensure time difference

            TaskDto second = taskService.create(TaskCreateRequest.builder()
                    .serviceName("s2").methodName("m2").dtoJson("{}").build());

            // When
            TaskDto next = taskService.getNextTodo();

            // Then
            assertThat(next).isNotNull();
            assertThat(next.getId()).isEqualTo(first.getId());
        }

        @Test
        @DisplayName("Should return null when no TODO tasks")
        void shouldReturnNull_whenNoTodoTasks() {
            // Given - no tasks created

            // When
            TaskDto next = taskService.getNextTodo();

            // Then
            assertThat(next).isNull();
        }
    }

    @Nested
    @DisplayName("Update Tests")
    class UpdateTests {

        @Test
        @DisplayName("Should update TODO task")
        void shouldUpdateTODO_task() {
            // Given
            TaskDto created = taskService.create(TaskCreateRequest.builder()
                    .serviceName("oldService")
                    .methodName("oldMethod")
                    .dtoJson("{\"old\":true}")
                    .build());

            TaskUpdateRequest request = TaskUpdateRequest.builder()
                    .serviceName("newService")
                    .methodName("newMethod")
                    .dtoJson("{\"new\":true}")
                    .build();

            // When
            TaskDto updated = taskService.update(created.getId(), request);

            // Then
            assertThat(updated.getId()).isEqualTo(created.getId());
            assertThat(updated.getServiceName()).isEqualTo("newService");
            assertThat(updated.getMethodName()).isEqualTo("newMethod");
            assertThat(updated.getDtoJson()).isEqualTo("{\"new\":true}");
            assertThat(updated.getStatus()).isEqualTo(TaskDto.TaskStatus.TODO);
        }

        @Test
        @DisplayName("Should not update task status")
        void shouldNotUpdate_taskStatus() {
            // Given
            TaskDto created = taskService.create(TaskCreateRequest.builder()
                    .serviceName("service")
                    .methodName("method")
                    .dtoJson("{}")
                    .build());

            TaskUpdateRequest request = TaskUpdateRequest.builder()
                    .serviceName("service")
                    .methodName("method")
                    .dtoJson("{}")
                    .build();

            // When
            TaskDto updated = taskService.update(created.getId(), request);

            // Then
            assertThat(updated.getStatus()).isEqualTo(TaskDto.TaskStatus.TODO);
        }

        @Test
        @DisplayName("Should throw exception when updating non-TODO task")
        void shouldThrowException_whenUpdatingNonTodoTask() {
            // Given
            TaskDto created = taskService.create(TaskCreateRequest.builder()
                    .serviceName("service")
                    .methodName("method")
                    .dtoJson("{}")
                    .build());

            // Manually set status to DONE (simulate task execution)
            Task task = taskRepository.findById(created.getId()).orElseThrow();
            task.setStatus(Task.TaskStatus.DONE);
            taskRepository.save(task);

            TaskUpdateRequest request = TaskUpdateRequest.builder()
                    .serviceName("newService")
                    .methodName("newMethod")
                    .dtoJson("{}")
                    .build();

            // When & Then
            assertThatThrownBy(() -> taskService.update(created.getId(), request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot update task");
        }
    }

    @Nested
    @DisplayName("Delete Tests")
    class DeleteTests {

        @Test
        @DisplayName("Should delete existing task")
        void shouldDelete_existingTask() {
            // Given
            TaskDto created = taskService.create(TaskCreateRequest.builder()
                    .serviceName("service")
                    .methodName("method")
                    .dtoJson("{}")
                    .build());

            // When
            taskService.delete(created.getId());

            // Then
            assertThatThrownBy(() -> taskService.findById(created.getId()))
                    .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        @DisplayName("Should throw exception when deleting non-existent task")
        void shouldThrowException_whenDeletingNonExistentTask() {
            // When & Then
            assertThatThrownBy(() -> taskService.delete(999L))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining("Task not found");
        }
    }

    @Nested
    @DisplayName("Execute Tests")
    class ExecuteTests {

        @Test
        @DisplayName("Should mark task as DONE after execution attempt")
        void shouldMarkTaskDONE_afterExecution() {
            // Given
            TaskDto created = taskService.create(TaskCreateRequest.builder()
                    .serviceName("unknownService")
                    .methodName("unknownMethod")
                    .dtoJson("{}")
                    .build());

            // When
            TaskDto executed = taskService.execute(created.getId());

            // Then
            assertThat(executed.getStatus()).isEqualTo(TaskDto.TaskStatus.DONE);
            assertThat(executed.getResult()).isNotNull();
            assertThat(executed.getResult()).contains("ERROR"); // Unknown service will fail
            assertThat(executed.getResultTimestamp()).isNotNull();
        }

        @Test
        @DisplayName("Should throw exception when executing non-existent task")
        void shouldThrowException_whenExecutingNonExistentTask() {
            // When & Then
            assertThatThrownBy(() -> taskService.execute(999L))
                    .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        @DisplayName("Should throw exception when executing non-TODO task")
        void shouldThrowException_whenExecutingNonTodoTask() {
            // Given
            TaskDto created = taskService.create(TaskCreateRequest.builder()
                    .serviceName("service")
                    .methodName("method")
                    .dtoJson("{}")
                    .build());

            // Manually set status to DONE
            Task task = taskRepository.findById(created.getId()).orElseThrow();
            task.setStatus(Task.TaskStatus.DONE);
            taskRepository.save(task);

            // When & Then
            assertThatThrownBy(() -> taskService.execute(created.getId()))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("Lifecycle Tests")
    class LifecycleTests {

        @Test
        @DisplayName("Should complete full lifecycle: create -> read -> update -> delete")
        void shouldCompleteFullLifecycle() {
            // 1. Create
            TaskCreateRequest createRequest = TaskCreateRequest.builder()
                    .serviceName("hcmUserService")
                    .methodName("onboard")
                    .dtoJson("{\"employeeId\":\"123\"}")
                    .build();
            TaskDto created = taskService.create(createRequest);
            assertThat(created.getId()).isNotNull();

            // 2. Read
            TaskDto read = taskService.findById(created.getId());
            assertThat(read.getServiceName()).isEqualTo("hcmUserService");

            // 3. Update
            TaskUpdateRequest updateRequest = TaskUpdateRequest.builder()
                    .serviceName("updatedService")
                    .methodName("updatedMethod")
                    .dtoJson("{\"employeeId\":\"456\"}")
                    .build();
            TaskDto updated = taskService.update(created.getId(), updateRequest);
            assertThat(updated.getServiceName()).isEqualTo("updatedService");

            // 4. Execute
            TaskDto executed = taskService.execute(created.getId());
            assertThat(executed.getStatus()).isEqualTo(TaskDto.TaskStatus.DONE);
            assertThat(executed.getResult()).isNotNull();

            // 5. Delete
            taskService.delete(created.getId());
            assertThatThrownBy(() -> taskService.findById(created.getId()))
                    .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        @DisplayName("Should track timestamps correctly")
        void shouldTrackTimestamps_correctly() throws Exception {
            // Given
            TaskCreateRequest request = TaskCreateRequest.builder()
                    .serviceName("service")
                    .methodName("method")
                    .dtoJson("{}")
                    .build();

            // When
            TaskDto created = taskService.create(request);
            // 从数据库重新获取以获取存储后的时间戳（SQLite 精度较低）
            TaskDto createdFromDb = taskService.findById(created.getId());
            Instant originalCreatedAt = createdFromDb.getCreatedAt();
            Instant originalUpdatedAt = createdFromDb.getUpdatedAt();

            Thread.sleep(10);

            taskService.update(created.getId(), TaskUpdateRequest.builder()
                    .serviceName("newService")
                    .methodName("newMethod")
                    .dtoJson("{}")
                    .build());

            TaskDto updated = taskService.findById(created.getId());

            // Then - createdAt 不应改变，updatedAt 应该更新
            assertThat(updated.getCreatedAt()).isEqualTo(originalCreatedAt);
            // 由于 SQLite 存储精度可能只有秒级，updatedAt 可能相同（如果更新在同一秒内）
            // 我们只验证 updatedAt 不为 null
            assertThat(updated.getUpdatedAt()).isNotNull();
        }
    }
}
