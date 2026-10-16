package com.platform.dto;

import com.platform.core.dto.BaseDTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class UserRequestDto implements BaseDTO<java.util.UUID> {

    private java.util.UUID id;

    @Override
    public java.util.UUID getId() {
        return id;
    }

    @Override
    public void setId(java.util.UUID id) {
        this.id = id;
    }

    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;

    @NotBlank(message = "Role is required")
    @Pattern(regexp = "^(CLIENT|ADMIN|THERAPIST)$", message = "invalid role")
    private String role;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
