package com.example.usersync.controller;

import com.example.usersync.dto.TaskCreateRequest;
import com.example.usersync.dto.TaskDto;
import com.example.usersync.dto.TaskUpdateRequest;
import com.example.usersync.entity.Task;
import com.example.usersync.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for Task operations.
 */
@Slf4j
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @GetMapping
    public ResponseEntity<List<TaskDto>> getAllTasks(
            @RequestParam(required = false) Task.TaskStatus status) {
        if (status != null) {
            return ResponseEntity.ok(taskService.findByStatus(status));
        }
        return ResponseEntity.ok(taskService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskDto> getTaskById(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.findById(id));
    }

    @GetMapping("/next-todo")
    public ResponseEntity<TaskDto> getNextTodo() {
        TaskDto task = taskService.getNextTodo();
        return task != null ? ResponseEntity.ok(task) : ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<TaskDto> createTask(@Valid @RequestBody TaskCreateRequest request) {
        TaskDto created = taskService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaskDto> updateTask(
            @PathVariable Long id,
            @Valid @RequestBody TaskUpdateRequest request) {
        return ResponseEntity.ok(taskService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
        taskService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/execute")
    public ResponseEntity<TaskDto> executeTask(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.execute(id));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<TaskDto> retryTask(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.retry(id));
    }
}
