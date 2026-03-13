package com.example.usersync.repository;

import com.example.usersync.entity.Task;
import com.example.usersync.entity.Task.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Task entity.
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    /**
     * Find tasks by status.
     */
    List<Task> findByStatus(TaskStatus status);

    /**
     * Find tasks by service name.
     */
    List<Task> findByServiceName(String serviceName);

    /**
     * Find tasks by status and order by created time.
     */
    List<Task> findByStatusOrderByCreatedAtAsc(TaskStatus status);

    /**
     * Find tasks by service name and method name.
     */
    List<Task> findByServiceNameAndMethodName(String serviceName, String methodName);
}
