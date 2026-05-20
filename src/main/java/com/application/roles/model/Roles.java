package com.application.roles.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "roles")
public class Roles {

    @Id
    @Column(name="role_id")
    private String roleId;

    @Column(name="role_type")
    private String roleType;

    @Column(name="description")
    private String description;

    @Column(name="created_at")
    private LocalDateTime createdAt;

    @Column(name="updated_at")
    private LocalDateTime updatedAt;

    @Column(name="status")
    private String status;

    @Transient
    private Users users;
}
