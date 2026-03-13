package com.example.usersync.service.impl;

import com.example.usersync.dto.TaskCreateRequest;
import com.example.usersync.dto.TaskDto;
import com.example.usersync.dto.TaskUpdateRequest;
import com.example.usersync.entity.Task;
import com.example.usersync.repository.TaskRepository;
import com.example.usersync.service.TaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Service implementation for Task operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final ApplicationContext applicationContext;
    private final ObjectMapper objectMapper;

    @Override
    public List<TaskDto> findAll() {
        return taskRepository.findAll().stream()
                .map(Task::toDto)
                .toList();
    }

    @Override
    public TaskDto findById(Long id) {
        return taskRepository.findById(id)
                .map(Task::toDto)
                .orElseThrow(() -> new NoSuchElementException("Task not found: " + id));
    }

    @Override
    @Transactional
    public TaskDto create(TaskCreateRequest request) {
        Task task = Task.builder()
                .serviceName(request.getServiceName())
                .methodName(request.getMethodName())
                .dtoJson(request.getDtoJson())
                .status(Task.TaskStatus.TODO)
                .build();
        Task saved = taskRepository.save(task);
        log.info("Created task: id={}, service={}, method={}",
                saved.getId(), saved.getServiceName(), saved.getMethodName());
        return saved.toDto();
    }

    @Override
    @Transactional
    public TaskDto update(Long id, TaskUpdateRequest request) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Task not found: " + id));

        // Only allow updating TODO tasks
        if (task.getStatus() != Task.TaskStatus.TODO) {
            throw new IllegalStateException("Cannot update task with status: " + task.getStatus());
        }

        task.setServiceName(request.getServiceName());
        task.setMethodName(request.getMethodName());
        task.setDtoJson(request.getDtoJson());
        Task updated = taskRepository.save(task);
        log.info("Updated task: id={}", id);
        return updated.toDto();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (!taskRepository.existsById(id)) {
            throw new NoSuchElementException("Task not found: " + id);
        }
        taskRepository.deleteById(id);
        log.info("Deleted task: id={}", id);
    }

    @Override
    public List<TaskDto> findByStatus(Task.TaskStatus status) {
        return taskRepository.findByStatus(status).stream()
                .map(Task::toDto)
                .toList();
    }

    @Override
    @Transactional
    public TaskDto execute(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Task not found: " + id));

        if (task.getStatus() != Task.TaskStatus.TODO) {
            throw new IllegalStateException("Task is not in TODO status: " + task.getStatus());
        }

        // Update status to DOING
        task.setStatus(Task.TaskStatus.DOING);
        taskRepository.save(task);

        String result;
        try {
            // Execute the task
            result = executeTask(task);
            task.setStatus(Task.TaskStatus.DONE);
            task.setResult("SUCCESS: " + result);
        } catch (Exception e) {
            task.setStatus(Task.TaskStatus.DONE);
            task.setResult("ERROR: " + e.getMessage());
            log.error("Task execution failed: id={}", id, e);
        }

        task.setResultTimestamp(Instant.now());
        Task saved = taskRepository.save(task);
        log.info("Executed task: id={}, result={}", id, saved.getResult());
        return saved.toDto();
    }

    @Override
    public TaskDto getNextTodo() {
        List<Task> todoTasks = taskRepository.findByStatusOrderByCreatedAtAsc(Task.TaskStatus.TODO);
        return todoTasks.isEmpty() ? null : todoTasks.get(0).toDto();
    }

    @Override
    @Transactional
    public TaskDto retry(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Task not found: " + id));

        // Only allow retrying DONE tasks
        if (task.getStatus() != Task.TaskStatus.DONE) {
            throw new IllegalStateException("Can only retry tasks with DONE status, current: " + task.getStatus());
        }

        // Reset to TODO and clear previous results
        task.setStatus(Task.TaskStatus.TODO);
        task.setResult(null);
        task.setResultTimestamp(null);
        taskRepository.save(task);
        log.info("Reset task for retry: id={}", id);

        // Execute the task
        return execute(id);
    }

    /**
     * Execute a task by calling the service method with the DTO.
     */
    private String executeTask(Task task) throws Exception {
        // Get the service bean
        Object service = applicationContext.getBean(task.getServiceName());

        // Parse the DTO JSON
        Class<?> dtoClass = getDtoClassForService(task.getServiceName(), task.getMethodName());
        Object dto = objectMapper.readValue(task.getDtoJson(), dtoClass);

        // Use reflection to call the method
        java.lang.reflect.Method method = service.getClass().getMethod(
                task.getMethodName(), dtoClass);
        Object result = method.invoke(service, dto);

        // Return the result as JSON string
        return result != null ? objectMapper.writeValueAsString(result) : "null";
    }

    /**
     * Determine the DTO class based on service and method name.
     * This is a simplified implementation - in production you might use a registry.
     */
    private Class<?> getDtoClassForService(String serviceName, String methodName) {
        // Simplified mapping - in production use a proper registry
        if ("hcmUserService".equals(serviceName) && "onboard".equals(methodName)) {
            return com.example.usersync.dto.HcmUserDto.class;
        }
        if ("hcmUserService".equals(serviceName) && "create".equals(methodName)) {
            return com.example.usersync.dto.HcmUserDto.class;
        }
        if ("iamUserService".equals(serviceName) && "create".equals(methodName)) {
            return com.example.usersync.dto.IamUserDto.class;
        }
        throw new IllegalArgumentException("Unknown service method: " + serviceName + "." + methodName);
    }
}
