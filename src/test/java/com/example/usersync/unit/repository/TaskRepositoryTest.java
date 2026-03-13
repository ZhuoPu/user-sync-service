package com.example.usersync.unit.repository;

import com.example.usersync.entity.Task;
import com.example.usersync.repository.TaskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TaskRepository.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite::memory:",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DisplayName("TaskRepository Unit Tests")
class TaskRepositoryTest {

    @Autowired
    private TaskRepository taskRepository;

    @AfterEach
    void tearDown() {
        taskRepository.deleteAll();
    }

    @Nested
    @DisplayName("Create Tests")
    class CreateTests {

        @Test
        @DisplayName("Should save task with all fields")
        void shouldSaveTask_withAllFields() {
            // Given
            Task task = Task.builder()
                    .serviceName("hcmUserService")
                    .methodName("onboard")
                    .dtoJson("{\"employeeId\":\"123\"}")
                    .status(Task.TaskStatus.TODO)
                    .build();

            // When
            Task saved = taskRepository.save(task);

            // Then
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getServiceName()).isEqualTo("hcmUserService");
            assertThat(saved.getMethodName()).isEqualTo("onboard");
            assertThat(saved.getDtoJson()).isEqualTo("{\"employeeId\":\"123\"}");
            assertThat(saved.getStatus()).isEqualTo(Task.TaskStatus.TODO);
            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(saved.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should set default status to TODO")
        void shouldSetDefaultStatus_toTODO() {
            // Given
            Task task = Task.builder()
                    .serviceName("testService")
                    .methodName("testMethod")
                    .dtoJson("{}")
                    .build();

            // When
            Task saved = taskRepository.save(task);

            // Then
            assertThat(saved.getStatus()).isEqualTo(Task.TaskStatus.TODO);
        }
    }

    @Nested
    @DisplayName("Read Tests")
    class ReadTests {

        @Test
        @DisplayName("Should find task by id")
        void shouldFindTask_byId() {
            // Given
            Task task = taskRepository.save(Task.builder()
                    .serviceName("service")
                    .methodName("method")
                    .dtoJson("{}")
                    .build());

            // When
            Optional<Task> found = taskRepository.findById(task.getId());

            // Then
            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(task.getId());
        }

        @Test
        @DisplayName("Should find all tasks")
        void shouldFindAll_tasks() {
            // Given
            taskRepository.save(Task.builder()
                    .serviceName("service1")
                    .methodName("method1")
                    .dtoJson("{}")
                    .build());
            taskRepository.save(Task.builder()
                    .serviceName("service2")
                    .methodName("method2")
                    .dtoJson("{}")
                    .build());

            // When
            List<Task> tasks = taskRepository.findAll();

            // Then
            assertThat(tasks).hasSize(2);
        }

        @Test
        @DisplayName("Should find tasks by status")
        void shouldFindTasks_byStatus() {
            // Given
            taskRepository.save(Task.builder()
                    .serviceName("service1")
                    .methodName("method1")
                    .dtoJson("{}")
                    .status(Task.TaskStatus.TODO)
                    .build());
            taskRepository.save(Task.builder()
                    .serviceName("service2")
                    .methodName("method2")
                    .dtoJson("{}")
                    .status(Task.TaskStatus.DONE)
                    .build());
            taskRepository.save(Task.builder()
                    .serviceName("service3")
                    .methodName("method3")
                    .dtoJson("{}")
                    .status(Task.TaskStatus.TODO)
                    .build());

            // When
            List<Task> todoTasks = taskRepository.findByStatus(Task.TaskStatus.TODO);
            List<Task> doneTasks = taskRepository.findByStatus(Task.TaskStatus.DONE);

            // Then
            assertThat(todoTasks).hasSize(2);
            assertThat(doneTasks).hasSize(1);
        }

        @Test
        @DisplayName("Should find tasks by service name")
        void shouldFindTasks_byServiceName() {
            // Given
            taskRepository.save(Task.builder()
                    .serviceName("hcmUserService")
                    .methodName("method1")
                    .dtoJson("{}")
                    .build());
            taskRepository.save(Task.builder()
                    .serviceName("hcmUserService")
                    .methodName("method2")
                    .dtoJson("{}")
                    .build());
            taskRepository.save(Task.builder()
                    .serviceName("iamUserService")
                    .methodName("method1")
                    .dtoJson("{}")
                    .build());

            // When
            List<Task> hcmTasks = taskRepository.findByServiceName("hcmUserService");

            // Then
            assertThat(hcmTasks).hasSize(2);
            assertThat(hcmTasks).allMatch(t -> t.getServiceName().equals("hcmUserService"));
        }

        @Test
        @DisplayName("Should find tasks by status ordered by created time")
        void shouldFindTasks_byStatusOrderedByCreatedTime() {
            // Given
            Task task1 = taskRepository.save(Task.builder()
                    .serviceName("service")
                    .methodName("method1")
                    .dtoJson("{}")
                    .status(Task.TaskStatus.TODO)
                    .build());

            // Small delay to ensure different timestamps
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            Task task2 = taskRepository.save(Task.builder()
                    .serviceName("service")
                    .methodName("method2")
                    .dtoJson("{}")
                    .status(Task.TaskStatus.TODO)
                    .build());

            // When
            List<Task> tasks = taskRepository.findByStatusOrderByCreatedAtAsc(Task.TaskStatus.TODO);

            // Then
            assertThat(tasks).hasSize(2);
            assertThat(tasks.get(0).getId()).isEqualTo(task1.getId());
            assertThat(tasks.get(1).getId()).isEqualTo(task2.getId());
        }
    }

    @Nested
    @DisplayName("Update Tests")
    class UpdateTests {

        @Test
        @DisplayName("Should update task fields")
        void shouldUpdateTask_fields() {
            // Given
            Task task = taskRepository.save(Task.builder()
                    .serviceName("service")
                    .methodName("method")
                    .dtoJson("{}")
                    .status(Task.TaskStatus.TODO)
                    .build());

            // When
            task.setServiceName("updatedService");
            task.setMethodName("updatedMethod");
            task.setStatus(Task.TaskStatus.DOING);
            task.setResult("SUCCESS");
            Task updated = taskRepository.save(task);

            // Then
            assertThat(updated.getServiceName()).isEqualTo("updatedService");
            assertThat(updated.getMethodName()).isEqualTo("updatedMethod");
            assertThat(updated.getStatus()).isEqualTo(Task.TaskStatus.DOING);
            assertThat(updated.getResult()).isEqualTo("SUCCESS");
        }

        @Test
        @DisplayName("Should update updated_at timestamp on update")
        void shouldUpdateUpdatedAt_onUpdate() throws Exception {
            // Given
            Task task = taskRepository.save(Task.builder()
                    .serviceName("service")
                    .methodName("method")
                    .dtoJson("{}")
                    .build());

            Instant originalUpdatedAt = task.getUpdatedAt();
            Thread.sleep(10); // Ensure time difference

            // When
            task.setServiceName("updated");
            Task updated = taskRepository.save(task);

            // Then
            assertThat(updated.getUpdatedAt()).isAfter(originalUpdatedAt);
        }
    }

    @Nested
    @DisplayName("Delete Tests")
    class DeleteTests {

        @Test
        @DisplayName("Should delete task by id")
        void shouldDeleteTask_byId() {
            // Given
            Task task = taskRepository.save(Task.builder()
                    .serviceName("service")
                    .methodName("method")
                    .dtoJson("{}")
                    .build());

            // When
            taskRepository.deleteById(task.getId());

            // Then
            Optional<Task> found = taskRepository.findById(task.getId());
            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("Should check if task exists by id")
        void shouldCheckExistence_byId() {
            // Given
            Task task = taskRepository.save(Task.builder()
                    .serviceName("service")
                    .methodName("method")
                    .dtoJson("{}")
                    .build());

            // When & Then
            assertThat(taskRepository.existsById(task.getId())).isTrue();
            assertThat(taskRepository.existsById(999L)).isFalse();
        }
    }
}
