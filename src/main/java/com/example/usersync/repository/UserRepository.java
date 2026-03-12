package com.example.usersync.repository;

import com.example.usersync.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    List<User> findBySyncStatus(User.SyncStatus syncStatus);

    List<User> findBySyncStatusAndRetryCountLessThan(User.SyncStatus syncStatus, Integer retryCount);
}
