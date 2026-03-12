package com.example.usersync.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    private String userId;

    @Column(nullable = false)
    private String username;

    private String email;

    private String firstName;

    private String lastName;

    private String phoneNumber;

    @Column(length = 1000)
    private String dataHash;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private SyncStatus syncStatus = SyncStatus.PENDING;

    @Column(updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private LocalDateTime lastSyncedAt;

    private Integer retryCount;

    @Version
    private Integer version;

    public enum SyncStatus {
        PENDING,       // Received from Kafka, not yet synced
        SYNCED,        // Successfully synced to external API
        FAILED,        // Sync failed, needs retry
        CHANGED        // Data changed, needs re-sync
    }
}
