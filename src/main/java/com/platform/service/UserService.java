package com.platform.service;

import com.platform.core.service.BaseService;
import com.platform.dto.UserRequestDto;
import com.platform.dto.UserResponseDto;
import com.platform.entity.UserEntity;
import com.platform.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService extends BaseService<UserEntity, UserRequestDto, UUID> {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

<<<<<<< HEAD
    public UserService(UserRepository userRepository) {
        super(userRepository);
=======
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
>>>>>>> fa4972dbc8a433d3937f53daa44e44c3ed6b905a
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponseDto createUser(UserRequestDto request) {
        if (!request.getRole().matches("^(CLIENT|ADMIN|THERAPIST)$")) {
            throw new IllegalArgumentException("invalid role");
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("username already exists");
        }

<<<<<<< HEAD
        UserEntity user = toEntity(request);
=======
        UserEntity user = new UserEntity();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
>>>>>>> fa4972dbc8a433d3937f53daa44e44c3ed6b905a

        UserEntity savedUser = userRepository.save(user);

        UserResponseDto response = new UserResponseDto();
        response.setId(savedUser.getId());
        response.setUsername(savedUser.getUsername());
        response.setRole(savedUser.getRole());

        return response;
    }
}
