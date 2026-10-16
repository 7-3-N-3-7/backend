package com.platform.service;

<<<<<<< HEAD
import com.platform.core.service.BaseService;
=======
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
import com.platform.core.service.BaseService;
=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
=======
import com.platform.core.service.BaseService;
=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
>>>>>>> d6bd62f49cdd26c1f0dfc9614c1ba49cc9f83309
import com.platform.dto.UserRequestDto;
import com.platform.dto.UserResponseDto;
import com.platform.entity.UserEntity;
import com.platform.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

<<<<<<< HEAD
=======
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
>>>>>>> d6bd62f49cdd26c1f0dfc9614c1ba49cc9f83309
import java.util.UUID;

@Service
public class UserService extends BaseService<UserEntity, UserRequestDto, UUID> {
<<<<<<< HEAD
=======
=======
@Service
public class UserService {
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
<<<<<<< HEAD
=======
@Service
public class UserService {
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
>>>>>>> d6bd62f49cdd26c1f0dfc9614c1ba49cc9f83309

    private final UserRepository userRepository;

<<<<<<< HEAD
    public UserService(UserRepository userRepository) {
        super(userRepository);
=======
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
    public UserService(UserRepository userRepository) {
        super(userRepository);
=======
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
>>>>>>> fa4972dbc8a433d3937f53daa44e44c3ed6b905a
=======
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
<<<<<<< HEAD
=======
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
>>>>>>> d6bd62f49cdd26c1f0dfc9614c1ba49cc9f83309
        this.userRepository = userRepository;
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
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
        UserEntity user = toEntity(request);
=======
=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
<<<<<<< HEAD
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
        UserEntity user = new UserEntity();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
>>>>>>> fa4972dbc8a433d3937f53daa44e44c3ed6b905a
=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
=======
>>>>>>> fa4972dbc8a433d3937f53daa44e44c3ed6b905a
=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
>>>>>>> d6bd62f49cdd26c1f0dfc9614c1ba49cc9f83309

        UserEntity savedUser = userRepository.save(user);

        UserResponseDto response = new UserResponseDto();
        response.setId(savedUser.getId());
        response.setUsername(savedUser.getUsername());
        response.setRole(savedUser.getRole());

        return response;
    }
}
