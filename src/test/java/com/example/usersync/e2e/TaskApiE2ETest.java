package com.example.usersync.e2e;

import com.example.usersync.dto.TaskCreateRequest;
import com.example.usersync.dto.TaskDto;
import com.example.usersync.dto.TaskUpdateRequest;
import com.example.usersync.entity.Task;
import com.example.usersync.repository.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-End Test for Task API.
 * Tests the complete HTTP request/response cycle with a real running server.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite:build/test-e2e.db",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DisplayName("Task API E2E Tests")
class TaskApiE2ETest {

    @LocalServerPort
    private int port;

    private WebTestClient webTestClient;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Create WebTestClient that connects to the real running server
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
        taskRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        taskRepository.deleteAll();
    }

    @Nested
    @DisplayName("GET /api/tasks - List Tasks")
    class GetTasksTests {

        @Test
        @DisplayName("Should return empty list when no tasks exist")
        void shouldReturnEmptyList_whenNoTasks() {
            webTestClient.get()
                    .uri("/api/tasks")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(TaskDto.class)
                    .hasSize(0);
        }

        @Test
        @DisplayName("Should return all tasks")
        void shouldReturnAllTasks() {
            // Given - Create 3 tasks via repository
            taskRepository.save(Task.builder()
                    .serviceName("hcmUserService")
                    .methodName("onboard")
                    .dtoJson("{\"employeeId\":\"001\"}")
                    .build());
            taskRepository.save(Task.builder()
                    .serviceName("iamUserService")
                    .methodName("create")
                    .dtoJson("{\"userId\":\"user1\"}")
                    .build());
            taskRepository.save(Task.builder()
                    .serviceName("hcmUserService")
                    .methodName("update")
                    .dtoJson("{\"employeeId\":\"002\"}")
                    .build());

            // When & Then
            List<TaskDto> tasks = webTestClient.get()
                    .uri("/api/tasks")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(TaskDto.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(tasks).hasSize(3);
            assertThat(tasks.get(0).getServiceName()).isEqualTo("hcmUserService");
        }

        @Test
        @DisplayName("Should filter tasks by status")
        void shouldFilterTasks_byStatus() {
            // Given - Create tasks with different statuses
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
                    .status(Task.TaskStatus.TODO)
                    .build());
            taskRepository.save(Task.builder()
                    .serviceName("service3")
                    .methodName("method3")
                    .dtoJson("{}")
                    .status(Task.TaskStatus.DONE)
                    .result("SUCCESS: completed")
                    .resultTimestamp(Instant.now())
                    .build());

            // When & Then - Request only TODO tasks
            List<TaskDto> todoTasks = webTestClient.get()
                    .uri("/api/tasks?status=TODO")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(TaskDto.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(todoTasks).hasSize(2);
            assertThat(todoTasks).allMatch(t -> t.getStatus() == TaskDto.TaskStatus.TODO);

            // When & Then - Request only DONE tasks
            List<TaskDto> doneTasks = webTestClient.get()
                    .uri("/api/tasks?status=DONE")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(TaskDto.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(doneTasks).hasSize(1);
            assertThat(doneTasks.get(0).getStatus()).isEqualTo(TaskDto.TaskStatus.DONE);
        }
    }

    @Nested
    @DisplayName("POST /api/tasks - Create Task")
    class CreateTaskTests {

        @Test
        @DisplayName("Should create task via HTTP POST")
        void shouldCreateTask_viaHttpPost() {
            TaskCreateRequest request = TaskCreateRequest.builder()
                    .serviceName("hcmUserService")
                    .methodName("onboard")
                    .dtoJson("{\"employeeId\":\"123\",\"title\":\"Test Employee\"}")
                    .build();

            TaskDto created = webTestClient.post()
                    .uri("/api/tasks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody(TaskDto.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(created).isNotNull();
            assertThat(created.getId()).isNotNull();
            assertThat(created.getServiceName()).isEqualTo("hcmUserService");
            assertThat(created.getMethodName()).isEqualTo("onboard");
            assertThat(created.getDtoJson()).isEqualTo("{\"employeeId\":\"123\",\"title\":\"Test Employee\"}");
            assertThat(created.getStatus()).isEqualTo(TaskDto.TaskStatus.TODO);
            assertThat(created.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should return 400 for invalid request")
        void shouldReturn400_forInvalidRequest() {
            TaskCreateRequest request = TaskCreateRequest.builder()
                    .serviceName("")  // Invalid: empty serviceName
                    .methodName("onboard")
                    .dtoJson("{}")
                    .build();

            webTestClient.post()
                    .uri("/api/tasks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isBadRequest();
        }
    }

    @Nested
    @DisplayName("POST /api/tasks/{id}/execute - Execute Task")
    class ExecuteTaskTests {

        @Test
        @DisplayName("Should execute task via HTTP POST")
        void shouldExecuteTask_viaHttpPost() {
            // Given - Create a task
            Task task = taskRepository.save(Task.builder()
                    .serviceName("nonExistentService")
                    .methodName("create")
                    .dtoJson("{\"title\":\"Test\"}")
                    .status(Task.TaskStatus.TODO)
                    .build());

            // When & Then
            TaskDto result = webTestClient.post()
                    .uri("/api/tasks/" + task.getId() + "/execute")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(TaskDto.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(task.getId());
            assertThat(result.getStatus()).isEqualTo(TaskDto.TaskStatus.DONE);
            assertThat(result.getResult()).isNotNull();
            assertThat(result.getResult()).contains("ERROR"); // Service doesn't exist
            assertThat(result.getResultTimestamp()).isNotNull();
        }
    }

    @Nested
    @DisplayName("POST /api/tasks/{id}/retry - Retry Task")
    class RetryTaskTests {

        @Test
        @DisplayName("Should retry failed task and get new result")
        void shouldRetryFailedTask_andGetNewResult() throws Exception {
            // Given - Create and execute a task (will fail)
            Task task = taskRepository.save(Task.builder()
                    .serviceName("nonExistentService")
                    .methodName("create")
                    .dtoJson("{\"title\":\"Test\"}")
                    .status(Task.TaskStatus.TODO)
                    .build());

            // First execution - will fail
            TaskDto firstExecution = webTestClient.post()
                    .uri("/api/tasks/" + task.getId() + "/execute")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(TaskDto.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(firstExecution.getStatus()).isEqualTo(TaskDto.TaskStatus.DONE);
            assertThat(firstExecution.getResult()).contains("ERROR");
            Instant firstTimestamp = firstExecution.getResultTimestamp();

            // Wait a bit to ensure timestamp difference
            Thread.sleep(100);

            // When - Retry the task
            TaskDto retryResult = webTestClient.post()
                    .uri("/api/tasks/" + task.getId() + "/retry")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(TaskDto.class)
                    .returnResult()
                    .getResponseBody();

            // Then - Should have new result
            assertThat(retryResult).isNotNull();
            assertThat(retryResult.getId()).isEqualTo(task.getId());
            assertThat(retryResult.getStatus()).isEqualTo(TaskDto.TaskStatus.DONE);
            assertThat(retryResult.getResult()).contains("ERROR");
            assertThat(retryResult.getResultTimestamp()).isNotNull();
            assertThat(retryResult.getResultTimestamp()).isAfter(firstTimestamp);
        }

        @Test
        @DisplayName("Should return 400 when retrying non-DONE task")
        void shouldReturn400_whenRetryingNonDoneTask() {
            // Given - Create a TODO task
            Task task = taskRepository.save(Task.builder()
                    .serviceName("hcmUserService")
                    .methodName("onboard")
                    .dtoJson("{\"employeeId\":\"123\"}")
                    .status(Task.TaskStatus.TODO)
                    .build());

            // When & Then - Try to retry TODO task
            webTestClient.post()
                    .uri("/api/tasks/" + task.getId() + "/retry")
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("Should return 404 when retrying non-existent task")
        void shouldReturn404_whenRetryingNonExistentTask() {
            // When & Then - Try to retry non-existent task
            webTestClient.post()
                    .uri("/api/tasks/99999/retry")
                    .exchange()
                    .expectStatus().isNotFound();
        }

        @Test
        @DisplayName("Full retry lifecycle: create -> execute -> retry -> verify")
        void fullRetryLifecycle() throws Exception {
            // Step 1: Create task via HTTP
            TaskCreateRequest createRequest = TaskCreateRequest.builder()
                    .serviceName("errorService")
                    .methodName("create")
                    .dtoJson("{\"data\":\"test\"}")
                    .build();

            TaskDto created = webTestClient.post()
                    .uri("/api/tasks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(createRequest)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody(TaskDto.class)
                    .returnResult()
                    .getResponseBody();

            Long taskId = created.getId();
            assertThat(created.getStatus()).isEqualTo(TaskDto.TaskStatus.TODO);
            assertThat(created.getResult()).isNull();

            // Step 2: Execute task (will fail)
            TaskDto executed = webTestClient.post()
                    .uri("/api/tasks/" + taskId + "/execute")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(TaskDto.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(executed.getStatus()).isEqualTo(TaskDto.TaskStatus.DONE);
            assertThat(executed.getResult()).contains("ERROR");
            Instant executeTimestamp = executed.getResultTimestamp();

            // Step 3: Verify task appears in DONE list
            List<TaskDto> doneTasks = webTestClient.get()
                    .uri("/api/tasks?status=DONE")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(TaskDto.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(doneTasks).hasSize(1);
            assertThat(doneTasks.get(0).getId()).isEqualTo(taskId);

            // Step 4: Retry task
            Thread.sleep(100); // Ensure timestamp difference
            TaskDto retried = webTestClient.post()
                    .uri("/api/tasks/" + taskId + "/retry")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(TaskDto.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(retried.getStatus()).isEqualTo(TaskDto.TaskStatus.DONE);
            assertThat(retried.getResult()).contains("ERROR");
            assertThat(retried.getResultTimestamp()).isAfter(executeTimestamp);

            // Step 5: Get task by ID and verify
            TaskDto verified = webTestClient.get()
                    .uri("/api/tasks/" + taskId)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(TaskDto.class)
                    .returnResult()
                    .getResponseBody();

            // Verify timestamp is the same (SQLite truncates nanoseconds, so compare epoch second)
            assertThat(verified.getResultTimestamp().getEpochSecond())
                    .isEqualTo(retried.getResultTimestamp().getEpochSecond());
        }
    }

    @Nested
    @DisplayName("GET /api/tasks/{id} - Get Task by ID")
    class GetTaskByIdTests {

        @Test
        @DisplayName("Should get task by ID")
        void shouldGetTask_byId() {
            // Given
            Task task = taskRepository.save(Task.builder()
                    .serviceName("hcmUserService")
                    .methodName("onboard")
                    .dtoJson("{\"employeeId\":\"123\"}")
                    .build());

            // When & Then
            TaskDto result = webTestClient.get()
                    .uri("/api/tasks/" + task.getId())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(TaskDto.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(result.getId()).isEqualTo(task.getId());
            assertThat(result.getServiceName()).isEqualTo("hcmUserService");
        }

        @Test
        @DisplayName("Should return 404 for non-existent task")
        void shouldReturn404_forNonExistentTask() {
            webTestClient.get()
                    .uri("/api/tasks/99999")
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }

    @Nested
    @DisplayName("DELETE /api/tasks/{id} - Delete Task")
    class DeleteTaskTests {

        @Test
        @DisplayName("Should delete task via HTTP DELETE")
        void shouldDeleteTask_viaHttpDelete() {
            // Given
            Task task = taskRepository.save(Task.builder()
                    .serviceName("service")
                    .methodName("method")
                    .dtoJson("{}")
                    .build());

            // When & Then
            webTestClient.delete()
                    .uri("/api/tasks/" + task.getId())
                    .exchange()
                    .expectStatus().isNoContent();

            assertThat(taskRepository.existsById(task.getId())).isFalse();
        }

        @Test
        @DisplayName("Should return 404 when deleting non-existent task")
        void shouldReturn404_whenDeletingNonExistentTask() {
            webTestClient.delete()
                    .uri("/api/tasks/99999")
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }

    @Nested
    @DisplayName("GET /api/tasks/next-todo - Get Next TODO Task")
    class GetNextTodoTests {

        @Test
        @DisplayName("Should get oldest TODO task")
        void shouldGetOldestTodoTask() throws Exception {
            // Given - Create multiple tasks
            taskRepository.save(Task.builder()
                    .serviceName("service1")
                    .methodName("method1")
                    .dtoJson("{}")
                    .build());
            Thread.sleep(50); // Ensure time difference
            taskRepository.save(Task.builder()
                    .serviceName("service2")
                    .methodName("method2")
                    .dtoJson("{}")
                    .build());

            // When & Then
            TaskDto result = webTestClient.get()
                    .uri("/api/tasks/next-todo")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(TaskDto.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(result.getServiceName()).isEqualTo("service1"); // First created
        }

        @Test
        @DisplayName("Should return 204 when no TODO tasks")
        void shouldReturn204_whenNoTodoTasks() {
            webTestClient.get()
                    .uri("/api/tasks/next-todo")
                    .exchange()
                    .expectStatus().isNoContent();
        }
    }

    @Nested
    @DisplayName("PUT /api/tasks/{id} - Update Task")
    class UpdateTaskTests {

        @Test
        @DisplayName("Should update TODO task")
        void shouldUpdateTodoTask() {
            // Given
            Task task = taskRepository.save(Task.builder()
                    .serviceName("oldService")
                    .methodName("oldMethod")
                    .dtoJson("{\"old\":true}")
                    .build());

            TaskUpdateRequest updateRequest = TaskUpdateRequest.builder()
                    .serviceName("newService")
                    .methodName("newMethod")
                    .dtoJson("{\"new\":true}")
                    .build();

            // When & Then
            TaskDto updated = webTestClient.put()
                    .uri("/api/tasks/" + task.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(updateRequest)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(TaskDto.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(updated.getServiceName()).isEqualTo("newService");
            assertThat(updated.getMethodName()).isEqualTo("newMethod");
            assertThat(updated.getDtoJson()).isEqualTo("{\"new\":true}");
        }

        @Test
        @DisplayName("Should return 400 when updating non-TODO task")
        void shouldReturn400_whenUpdatingNonTodoTask() {
            // Given
            Task task = taskRepository.save(Task.builder()
                    .serviceName("service")
                    .methodName("method")
                    .dtoJson("{}")
                    .status(Task.TaskStatus.DONE)
                    .result("DONE")
                    .build());

            TaskUpdateRequest updateRequest = TaskUpdateRequest.builder()
                    .serviceName("newService")
                    .methodName("newMethod")
                    .dtoJson("{}")
                    .build();

            // When & Then
            webTestClient.put()
                    .uri("/api/tasks/" + task.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(updateRequest)
                    .exchange()
                    .expectStatus().isBadRequest();
        }
    }
}
