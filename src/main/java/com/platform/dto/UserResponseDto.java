package com.platform.dto;

<<<<<<< HEAD
import com.platform.core.dto.BaseDTO;

public class UserResponseDto implements BaseDTO<java.util.UUID> {
=======
public class UserResponseDto {
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
    private java.util.UUID id;
    private String username;
    private String role;

<<<<<<< HEAD
    @Override
=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
    public java.util.UUID getId() {
        return id;
    }

<<<<<<< HEAD
    @Override
=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
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
