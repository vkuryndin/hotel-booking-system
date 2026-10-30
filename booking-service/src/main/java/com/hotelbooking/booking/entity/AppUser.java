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

    @Column(nullable = false, unique = true, length = 30)
    private String username;

    @Column(length = 16)
    private String phone;

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "password_change_required", nullable = false)
    private boolean passwordChangeRequired = false;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

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
    public AppUser(
            String email,
            String passwordHash,
            Role role,
            String username,
            String phone,
            String firstName,
            String lastName
    ) {
        this(email, passwordHash, role);
        this.username = username;
        this.phone = phone;
        this.firstName = firstName;
        this.lastName = lastName;
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

    public String getUsername() {
        return username;
    }

    public String getPhone() {
        return phone;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void changeProfile(
            String username,
            String phone,
            String firstName,
            String lastName
    ) {
        this.username = username;
        this.phone = phone;
        this.firstName = firstName;
        this.lastName = lastName;
    }

}