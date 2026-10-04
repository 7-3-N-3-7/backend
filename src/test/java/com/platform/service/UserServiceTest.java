package com.platform.service;

import com.platform.dto.UserRequestDto;
import com.platform.dto.UserResponseDto;
import com.platform.entity.UserEntity;
import com.platform.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void createUser_ShouldReturnCreatedUser_WhenUsernameIsUniqueAndRoleIsValid() {
        // Arrange
        UserRequestDto request = new UserRequestDto();
        request.setUsername("newclient");
        request.setPassword("securepassword");
        request.setRole("CLIENT");

        when(userRepository.existsByUsername("newclient")).thenReturn(false);

        UserEntity savedEntity = new UserEntity();
        savedEntity.setId(java.util.UUID.randomUUID());
        savedEntity.setUsername("newclient");
        savedEntity.setRole("CLIENT");
        when(userRepository.save(any(UserEntity.class))).thenReturn(savedEntity);

        // Act
        UserResponseDto response = userService.createUser(request);

        // Assert
        assertNotNull(response);
        assertEquals("newclient", response.getUsername());
        assertEquals("CLIENT", response.getRole());
        verify(userRepository).save(any(UserEntity.class));
    }

    @Test
    void createUser_ShouldThrowException_WhenUsernameAlreadyExists() {
        // Arrange
        UserRequestDto request = new UserRequestDto();
        request.setUsername("existinguser");
        request.setPassword("securepassword");
        request.setRole("CLIENT");

        when(userRepository.existsByUsername("existinguser")).thenReturn(true);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            userService.createUser(request);
        });
        assertEquals("username already exists", exception.getMessage());
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    void createUser_ShouldThrowException_WhenRoleIsInvalid() {
        // Arrange
        UserRequestDto request = new UserRequestDto();
        request.setUsername("user1");
        request.setPassword("secure");
        request.setRole("INVALID_ROLE");

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            userService.createUser(request);
        });
        assertEquals("invalid role", exception.getMessage());
        verify(userRepository, never()).save(any(UserEntity.class));
    }
}
