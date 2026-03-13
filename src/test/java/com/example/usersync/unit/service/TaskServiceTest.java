package com.example.usersync.unit.service;

import com.example.usersync.dto.HcmUserDto;
import com.example.usersync.dto.TaskCreateRequest;
import com.example.usersync.dto.TaskDto;
import com.example.usersync.dto.TaskUpdateRequest;
import com.example.usersync.entity.Task;
import com.example.usersync.repository.TaskRepository;
import com.example.usersync.service.HcmUserService;
import com.example.usersync.service.impl.TaskServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TaskService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TaskService Unit Tests")
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private HcmUserService hcmUserService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private TaskServiceImpl taskService;

    @AfterEach
    void tearDown() {
        reset(taskRepository, applicationContext, objectMapper);
    }

    @Nested
    @DisplayName("findAll Tests")
    class FindAllTests {

        @Test
        @DisplayName("Should return all tasks as DTOs")
        void shouldReturnAllTasks_asDTOs() {
            // Given
            Task task1 = Task.builder()
                    .id(1L)
                    .serviceName("service1")
                    .methodName("method1")
                    .dtoJson("{}")
                    .status(Task.TaskStatus.TODO)
                    .build();
            Task task2 = Task.builder()
                    .id(2L)
                    .serviceName("service2")
                    .methodName("method2")
                    .dtoJson("{}")
                    .status(Task.TaskStatus.DONE)
                    .build();

            when(taskRepository.findAll()).thenReturn(List.of(task1, task2));

            // When
            List<TaskDto> result = taskService.findAll();

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getId()).isEqualTo(1L);
            assertThat(result.get(1).getId()).isEqualTo(2L);
            verify(taskRepository).findAll();
        }
    }

    @Nested
    @DisplayName("findById Tests")
    class FindByIdTests {

        @Test
        @DisplayName("Should return task when found")
        void shouldReturnTask_whenFound() {
            // Given
            Task task = Task.builder()
                    .id(1L)
                    .serviceName("service")
                    .methodName("method")
                    .dtoJson("{}")
                    .status(Task.TaskStatus.TODO)
                    .build();

            when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

            // When
            TaskDto result = taskService.findById(1L);

            // Then
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getServiceName()).isEqualTo("service");
            verify(taskRepository).findById(1L);
        }

        @Test
        @DisplayName("Should throw exception when not found")
        void shouldThrowException_whenNotFound() {
            // Given
            when(taskRepository.findById(1L)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> taskService.findById(1L))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining("Task not found");
        }
    }

    @Nested
    @DisplayName("create Tests")
    class CreateTests {

        @Test
        @DisplayName("Should create task with TODO status")
        void shouldCreateTask_withTODOStatus() {
            // Given
            TaskCreateRequest request = TaskCreateRequest.builder()
                    .serviceName("hcmUserService")
                    .methodName("onboard")
                    .dtoJson("{\"employeeId\":\"123\"}")
                    .build();

            Task savedTask = Task.builder()
                    .id(1L)
                    .serviceName("hcmUserService")
                    .methodName("onboard")
                    .dtoJson("{\"employeeId\":\"123\"}")
                    .status(Task.TaskStatus.TODO)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            when(taskRepository.save(any(Task.class))).thenReturn(savedTask);

            // When
            TaskDto result = taskService.create(request);

            // Then
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getServiceName()).isEqualTo("hcmUserService");
            assertThat(result.getStatus()).isEqualTo(TaskDto.TaskStatus.TODO);
            verify(taskRepository).save(any(Task.class));
        }
    }

    @Nested
    @DisplayName("update Tests")
    class UpdateTests {

        @Test
        @DisplayName("Should update TODO task")
        void shouldUpdateTODO_task() {
            // Given
            Task existingTask = Task.builder()
                    .id(1L)
                    .serviceName("oldService")
                    .methodName("oldMethod")
                    .dtoJson("{}")
                    .status(Task.TaskStatus.TODO)
                    .build();

            TaskUpdateRequest request = TaskUpdateRequest.builder()
                    .serviceName("newService")
                    .methodName("newMethod")
                    .dtoJson("{\"new\":true}")
                    .build();

            when(taskRepository.findById(1L)).thenReturn(Optional.of(existingTask));
            when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            TaskDto result = taskService.update(1L, request);

            // Then
            assertThat(result.getServiceName()).isEqualTo("newService");
            assertThat(result.getMethodName()).isEqualTo("newMethod");
            assertThat(result.getDtoJson()).isEqualTo("{\"new\":true}");
        }

        @Test
        @DisplayName("Should throw exception when updating non-TODO task")
        void shouldThrowException_whenUpdatingNonTodoTask() {
            // Given
            Task existingTask = Task.builder()
                    .id(1L)
                    .serviceName("service")
                    .methodName("method")
                    .dtoJson("{}")
                    .status(Task.TaskStatus.DONE)
                    .build();

            TaskUpdateRequest request = TaskUpdateRequest.builder()
                    .serviceName("newService")
                    .methodName("newMethod")
                    .dtoJson("{}")
                    .build();

            when(taskRepository.findById(1L)).thenReturn(Optional.of(existingTask));

            // When & Then
            assertThatThrownBy(() -> taskService.update(1L, request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot update task");
        }
    }

    @Nested
    @DisplayName("delete Tests")
    class DeleteTests {

        @Test
        @DisplayName("Should delete existing task")
        void shouldDelete_existingTask() {
            // Given
            when(taskRepository.existsById(1L)).thenReturn(true);
            doNothing().when(taskRepository).deleteById(1L);

            // When
            taskService.delete(1L);

            // Then
            verify(taskRepository).deleteById(1L);
        }

        @Test
        @DisplayName("Should throw exception when deleting non-existent task")
        void shouldThrowException_whenDeletingNonExistentTask() {
            // Given
            when(taskRepository.existsById(1L)).thenReturn(false);

            // When & Then
            assertThatThrownBy(() -> taskService.delete(1L))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining("Task not found");
        }
    }

    @Nested
    @DisplayName("findByStatus Tests")
    class FindByStatusTests {

        @Test
        @DisplayName("Should return tasks by status")
        void shouldReturnTasks_byStatus() {
            // Given
            Task task1 = Task.builder()
                    .id(1L)
                    .serviceName("service1")
                    .methodName("method1")
                    .dtoJson("{}")
                    .status(Task.TaskStatus.TODO)
                    .build();
            Task task2 = Task.builder()
                    .id(2L)
                    .serviceName("service2")
                    .methodName("method2")
                    .dtoJson("{}")
                    .status(Task.TaskStatus.TODO)
                    .build();

            when(taskRepository.findByStatus(Task.TaskStatus.TODO))
                    .thenReturn(List.of(task1, task2));

            // When
            List<TaskDto> result = taskService.findByStatus(Task.TaskStatus.TODO);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).allMatch(t -> t.getStatus() == TaskDto.TaskStatus.TODO);
        }
    }

    @Nested
    @DisplayName("getNextTodo Tests")
    class GetNextTodoTests {

        @Test
        @DisplayName("Should return oldest TODO task")
        void shouldReturnOldestTODO_task() {
            // Given
            Task task = Task.builder()
                    .id(1L)
                    .serviceName("service")
                    .methodName("method")
                    .dtoJson("{}")
                    .status(Task.TaskStatus.TODO)
                    .build();

            when(taskRepository.findByStatusOrderByCreatedAtAsc(Task.TaskStatus.TODO))
                    .thenReturn(List.of(task));

            // When
            TaskDto result = taskService.getNextTodo();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Should return null when no TODO tasks")
        void shouldReturnNull_whenNoTodoTasks() {
            // Given
            when(taskRepository.findByStatusOrderByCreatedAtAsc(Task.TaskStatus.TODO))
                    .thenReturn(List.of());

            // When
            TaskDto result = taskService.getNextTodo();

            // Then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("execute Tests")
    class ExecuteTests {

        @Test
        @DisplayName("Should throw exception when task not found")
        void shouldThrowException_whenTaskNotFound() {
            // Given
            when(taskRepository.findById(1L)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> taskService.execute(1L))
                    .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        @DisplayName("Should throw exception when task is not TODO")
        void shouldThrowException_whenTaskNotTodo() {
            // Given
            Task task = Task.builder()
                    .id(1L)
                    .serviceName("service")
                    .methodName("method")
                    .dtoJson("{}")
                    .status(Task.TaskStatus.DONE)
                    .build();

            when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

            // When & Then
            assertThatThrownBy(() -> taskService.execute(1L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not in TODO status");
        }
    }
}
