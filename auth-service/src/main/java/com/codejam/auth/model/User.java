package com.codejam.auth.model;

import com.codejam.auth.util.AuthProvider;
import com.codejam.auth.dto.request.RegisterRequest;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users", schema = "auth")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 36)
    private String userId = UUID.randomUUID().toString();

    @Column(length = 500)
    private String name;

    @Column(unique = true, nullable = false, length = 255)
    private String email;

    @Column(nullable = true)
    private String password;

    @Column(name = "profile_image_url", columnDefinition = "TEXT")
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private AuthProvider provider;

    @Column(name = "provider_id", length = 500)
    private String providerId;

    @Column(name = "profile_image", columnDefinition = "BYTEA")
    private byte[] profileImage;

    private boolean enabled = true;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }


    public User(String name, String email, String password, boolean enabled) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.userId = UUID.randomUUID().toString();
        this.enabled = enabled;
    }

    public User(RegisterRequest request,  String password, boolean enabled) {
        this.name = request.getName();
        this.email = request.getEmail();
        this.password = password;
        this.userId = UUID.randomUUID().toString();
        this.enabled = enabled;
    }


    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

}
