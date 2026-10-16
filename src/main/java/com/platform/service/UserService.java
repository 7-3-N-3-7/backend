package com.platform.service;

<<<<<<< HEAD
<<<<<<< HEAD
import com.platform.core.service.BaseService;
=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
import com.platform.dto.UserRequestDto;
import com.platform.dto.UserResponseDto;
import com.platform.entity.UserEntity;
import com.platform.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

<<<<<<< HEAD
<<<<<<< HEAD
import java.util.UUID;

@Service
public class UserService extends BaseService<UserEntity, UserRequestDto, UUID> {
=======
@Service
public class UserService {
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======
@Service
public class UserService {
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
    public UserService(UserRepository userRepository) {
        super(userRepository);
=======
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
>>>>>>> fa4972dbc8a433d3937f53daa44e44c3ed6b905a
=======
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
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
<<<<<<< HEAD
<<<<<<< HEAD
        UserEntity user = toEntity(request);
=======
=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
        UserEntity user = new UserEntity();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
<<<<<<< HEAD
<<<<<<< HEAD
>>>>>>> fa4972dbc8a433d3937f53daa44e44c3ed6b905a
=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92

        UserEntity savedUser = userRepository.save(user);

        UserResponseDto response = new UserResponseDto();
        response.setId(savedUser.getId());
        response.setUsername(savedUser.getUsername());
        response.setRole(savedUser.getRole());

        return response;
    }
}
