package com.platform.dto;

<<<<<<< HEAD
<<<<<<< HEAD
import com.platform.core.dto.BaseDTO;

public class UserResponseDto implements BaseDTO<java.util.UUID> {
=======
public class UserResponseDto {
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======
public class UserResponseDto {
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
    private java.util.UUID id;
    private String username;
    private String role;

<<<<<<< HEAD
<<<<<<< HEAD
    @Override
=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
    public java.util.UUID getId() {
        return id;
    }

<<<<<<< HEAD
<<<<<<< HEAD
    @Override
=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
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
