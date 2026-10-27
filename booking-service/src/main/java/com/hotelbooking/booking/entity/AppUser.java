package com.hotelbooking.booking.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "password_change_required", nullable = false)
    private boolean passwordChangeRequired = false;

    @Column(name = "token_version", nullable = false)
    private long tokenVersion = 0;

    @Column(name = "token_identity", nullable = false,
            unique = true, updatable = false, length = 36)
    private String tokenIdentity = UUID.randomUUID().toString();

    protected AppUser() {
    }

    public AppUser(String email, String passwordHash, Role role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public void changeEmail(String email) {
        this.email = email;
    }

    public String getTokenIdentity() {
        return tokenIdentity;
    }

    public void changeRole(Role role) {
        if (this.role != role) {
            this.role = role;
            this.tokenVersion++;
        }
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
    public boolean isPasswordChangeRequired() {
        return passwordChangeRequired;
    }

    public long getTokenVersion() {
        return tokenVersion;
    }

    public void requirePasswordChange() {
        this.passwordChangeRequired = true;
        this.tokenVersion++;
    }

    public void completePasswordChange(String passwordHash) {
        this.passwordHash = passwordHash;
        this.passwordChangeRequired = false;
        this.tokenVersion++;
    }

}