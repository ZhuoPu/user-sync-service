package com.example.usersync.integration;

import com.example.usersync.dto.TaskCreateRequest;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for TaskController REST API.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite::memory:",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DisplayName("TaskController Integration Tests")
class TaskControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        taskRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        taskRepository.deleteAll();
    }

    @Nested
    @DisplayName("GET /api/tasks Tests")
    class GetAllTests {

        @Test
        @DisplayName("Should return empty list when no tasks")
        void shouldReturnEmptyList_whenNoTasks() throws Exception {
            mockMvc.perform(get("/api/tasks"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("Should return all tasks")
        void shouldReturnAll_tasks() throws Exception {
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

            // When & Then
            mockMvc.perform(get("/api/tasks"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].serviceName").value("service1"))
                    .andExpect(jsonPath("$[1].serviceName").value("service2"));
        }

        @Test
        @DisplayName("Should filter tasks by status")
        void shouldFilterTasks_byStatus() throws Exception {
            // Given
            taskRepository.save(Task.builder()
                    .serviceName("s1")
                    .methodName("m1")
                    .dtoJson("{}")
                    .status(Task.TaskStatus.TODO)
                    .build());
            taskRepository.save(Task.builder()
                    .serviceName("s2")
                    .methodName("m2")
                    .dtoJson("{}")
                    .status(Task.TaskStatus.DONE)
                    .build());

            // When & Then
            mockMvc.perform(get("/api/tasks").param("status", "TODO"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].status").value("TODO"));
        }
    }

    @Nested
    @DisplayName("GET /api/tasks/{id} Tests")
    class GetByIdTests {

        @Test
        @DisplayName("Should return task by id")
        void shouldReturnTask_byId() throws Exception {
            // Given
            Task task = taskRepository.save(Task.builder()
                    .serviceName("hcmUserService")
                    .methodName("onboard")
                    .dtoJson("{\"employeeId\":\"123\"}")
                    .build());

            // When & Then
            mockMvc.perform(get("/api/tasks/" + task.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(task.getId()))
                    .andExpect(jsonPath("$.serviceName").value("hcmUserService"))
                    .andExpect(jsonPath("$.methodName").value("onboard"))
                    .andExpect(jsonPath("$.dtoJson").value("{\"employeeId\":\"123\"}"))
                    .andExpect(jsonPath("$.status").value("TODO"));
        }

        @Test
        @DisplayName("Should return 404 when task not found")
        void shouldReturn404_whenTaskNotFound() throws Exception {
            mockMvc.perform(get("/api/tasks/999"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/tasks/next-todo Tests")
    class GetNextTodoTests {

        @Test
        @DisplayName("Should return next TODO task")
        void shouldReturnNextTODO_task() throws Exception {
            // Given
            Task task = taskRepository.save(Task.builder()
                    .serviceName("service")
                    .methodName("method")
                    .dtoJson("{}")
                    .status(Task.TaskStatus.TODO)
                    .build());

            // When & Then
            mockMvc.perform(get("/api/tasks/next-todo"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(task.getId()))
                    .andExpect(jsonPath("$.status").value("TODO"));
        }

        @Test
        @DisplayName("Should return 204 when no TODO tasks")
        void shouldReturn204_whenNoTodoTasks() throws Exception {
            mockMvc.perform(get("/api/tasks/next-todo"))
                    .andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("POST /api/tasks Tests")
    class CreateTests {

        @Test
        @DisplayName("Should create task and return 201")
        void shouldCreateTask_andReturn201() throws Exception {
            // Given
            TaskCreateRequest request = TaskCreateRequest.builder()
                    .serviceName("hcmUserService")
                    .methodName("onboard")
                    .dtoJson("{\"employeeId\":\"123\",\"title\":\"Test\"}")
                    .build();

            // When & Then
            mockMvc.perform(post("/api/tasks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").exists())
                    .andExpect(jsonPath("$.serviceName").value("hcmUserService"))
                    .andExpect(jsonPath("$.methodName").value("onboard"))
                    .andExpect(jsonPath("$.dtoJson").value("{\"employeeId\":\"123\",\"title\":\"Test\"}"))
                    .andExpect(jsonPath("$.status").value("TODO"))
                    .andExpect(jsonPath("$.createdAt").exists())
                    .andExpect(jsonPath("$.updatedAt").exists());
        }

        @Test
        @DisplayName("Should return 400 when serviceName is blank")
        void shouldReturn400_whenServiceNameIsBlank() throws Exception {
            // Given
            TaskCreateRequest request = TaskCreateRequest.builder()
                    .serviceName("")
                    .methodName("onboard")
                    .dtoJson("{}")
                    .build();

            // When & Then
            mockMvc.perform(post("/api/tasks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when methodName is blank")
        void shouldReturn400_whenMethodNameIsBlank() throws Exception {
            // Given
            TaskCreateRequest request = TaskCreateRequest.builder()
                    .serviceName("service")
                    .methodName("")
                    .dtoJson("{}")
                    .build();

            // When & Then
            mockMvc.perform(post("/api/tasks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when dtoJson is blank")
        void shouldReturn400_whenDtoJsonIsBlank() throws Exception {
            // Given
            TaskCreateRequest request = TaskCreateRequest.builder()
                    .serviceName("service")
                    .methodName("method")
                    .dtoJson("")
                    .build();

            // When & Then
            mockMvc.perform(post("/api/tasks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("PUT /api/tasks/{id} Tests")
    class UpdateTests {

        @Test
        @DisplayName("Should update TODO task")
        void shouldUpdateTODO_task() throws Exception {
            // Given
            Task task = taskRepository.save(Task.builder()
                    .serviceName("oldService")
                    .methodName("oldMethod")
                    .dtoJson("{\"old\":true}")
                    .build());

            TaskUpdateRequest request = TaskUpdateRequest.builder()
                    .serviceName("newService")
                    .methodName("newMethod")
                    .dtoJson("{\"new\":true}")
                    .build();

            // When & Then
            mockMvc.perform(put("/api/tasks/" + task.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(task.getId()))
                    .andExpect(jsonPath("$.serviceName").value("newService"))
                    .andExpect(jsonPath("$.methodName").value("newMethod"))
                    .andExpect(jsonPath("$.dtoJson").value("{\"new\":true}"));
        }

        @Test
        @DisplayName("Should return 400 when updating non-TODO task")
        void shouldReturn400_whenUpdatingNonTodoTask() throws Exception {
            // Given
            Task task = taskRepository.save(Task.builder()
                    .serviceName("service")
                    .methodName("method")
                    .dtoJson("{}")
                    .status(Task.TaskStatus.DONE)
                    .build());

            TaskUpdateRequest request = TaskUpdateRequest.builder()
                    .serviceName("newService")
                    .methodName("newMethod")
                    .dtoJson("{}")
                    .build();

            // When & Then
            mockMvc.perform(put("/api/tasks/" + task.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 404 when updating non-existent task")
        void shouldReturn404_whenUpdatingNonExistentTask() throws Exception {
            // Given
            TaskUpdateRequest request = TaskUpdateRequest.builder()
                    .serviceName("service")
                    .methodName("method")
                    .dtoJson("{}")
                    .build();

            // When & Then
            mockMvc.perform(put("/api/tasks/999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /api/tasks/{id} Tests")
    class DeleteTests {

        @Test
        @DisplayName("Should delete task and return 204")
        void shouldDeleteTask_andReturn204() throws Exception {
            // Given
            Task task = taskRepository.save(Task.builder()
                    .serviceName("service")
                    .methodName("method")
                    .dtoJson("{}")
                    .build());

            // When & Then
            mockMvc.perform(delete("/api/tasks/" + task.getId()))
                    .andExpect(status().isNoContent());

            // Verify task is deleted
            assertFalse(taskRepository.existsById(task.getId()));
        }

        @Test
        @DisplayName("Should return 404 when deleting non-existent task")
        void shouldReturn404_whenDeletingNonExistentTask() throws Exception {
            mockMvc.perform(delete("/api/tasks/999"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /api/tasks/{id}/execute Tests")
    class ExecuteTests {

        @Test
        @DisplayName("Should execute task and return result")
        void shouldExecuteTask_andReturnResult() throws Exception {
            // Given
            Task task = taskRepository.save(Task.builder()
                    .serviceName("unknownService")
                    .methodName("unknownMethod")
                    .dtoJson("{}")
                    .status(Task.TaskStatus.TODO)
                    .build());

            // When & Then
            mockMvc.perform(post("/api/tasks/" + task.getId() + "/execute"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(task.getId()))
                    .andExpect(jsonPath("$.status").value("DONE"))
                    .andExpect(jsonPath("$.result").exists())
                    .andExpect(jsonPath("$.resultTimestamp").exists());
        }

        @Test
        @DisplayName("Should return 404 when executing non-existent task")
        void shouldReturn404_whenExecutingNonExistentTask() throws Exception {
            mockMvc.perform(post("/api/tasks/999/execute"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 400 when executing non-TODO task")
        void shouldReturn400_whenExecutingNonTodoTask() throws Exception {
            // Given
            Task task = taskRepository.save(Task.builder()
                    .serviceName("service")
                    .methodName("method")
                    .dtoJson("{}")
                    .status(Task.TaskStatus.DONE)
                    .build());

            // When & Then
            mockMvc.perform(post("/api/tasks/" + task.getId() + "/execute"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Full API Flow Tests")
    class FlowTests {

        @Test
        @DisplayName("Should complete full CRUD lifecycle via API")
        void shouldCompleteFullCrudLifecycle_viaAPI() throws Exception {
            // 1. Create
            String responseJson = mockMvc.perform(post("/api/tasks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"serviceName\":\"hcmUserService\",\"methodName\":\"onboard\",\"dtoJson\":\"{\\\"employeeId\\\":\\\"123\\\"}\"}"))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            Long taskId = objectMapper.readTree(responseJson).get("id").asLong();

            // 2. Read
            mockMvc.perform(get("/api/tasks/" + taskId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.serviceName").value("hcmUserService"));

            // 3. Update
            mockMvc.perform(put("/api/tasks/" + taskId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"serviceName\":\"updatedService\",\"methodName\":\"updatedMethod\",\"dtoJson\":\"{\\\"employeeId\\\":\\\"456\\\"}\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.serviceName").value("updatedService"));

            // 4. Execute
            mockMvc.perform(post("/api/tasks/" + taskId + "/execute"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DONE"));

            // 5. Delete
            mockMvc.perform(delete("/api/tasks/" + taskId))
                    .andExpect(status().isNoContent());

            // Verify deleted
            mockMvc.perform(get("/api/tasks/" + taskId))
                    .andExpect(status().isNotFound());
        }
    }
}
