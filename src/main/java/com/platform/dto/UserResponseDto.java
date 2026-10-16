package com.platform.dto;

import com.platform.core.dto.BaseDTO;

public class UserResponseDto implements BaseDTO<java.util.UUID> {
    private java.util.UUID id;
    private String username;
    private String role;

    @Override
    public java.util.UUID getId() {
        return id;
    }

    @Override
    public void setId(java.util.UUID id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
