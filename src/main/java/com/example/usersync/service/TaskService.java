package com.example.usersync.service;

import com.example.usersync.dto.TaskCreateRequest;
import com.example.usersync.dto.TaskDto;
import com.example.usersync.dto.TaskUpdateRequest;
import com.example.usersync.entity.Task;

import java.util.List;

/**
 * Service interface for Task operations.
 */
public interface TaskService {

    /**
     * Get all tasks.
     */
    List<TaskDto> findAll();

    /**
     * Get task by id.
     */
    TaskDto findById(Long id);

    /**
     * Create a new task.
     */
    TaskDto create(TaskCreateRequest request);

    /**
     * Update an existing task.
     */
    TaskDto update(Long id, TaskUpdateRequest request);

    /**
     * Delete a task by id.
     */
    void delete(Long id);

    /**
     * Find tasks by status.
     */
    List<TaskDto> findByStatus(Task.TaskStatus status);

    /**
     * Execute a task.
     * Updates status to DOING, then executes, then updates to DONE with result.
     */
    TaskDto execute(Long id);

    /**
     * Get next TODO task (oldest first).
     */
    TaskDto getNextTodo();

    /**
     * Retry a completed task.
     * Resets status to TODO and clears result, then executes the task.
     *
     * @param id the task id
     * @return the executed task result
     * @throws NoSuchElementException if task not found
     */
    TaskDto retry(Long id);
}
