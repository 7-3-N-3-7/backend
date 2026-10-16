package com.platform.controller;

<<<<<<< HEAD
<<<<<<< HEAD
import com.platform.core.controller.BaseController;
import com.platform.dto.UserRequestDto;
import com.platform.dto.UserResponseDto;
import com.platform.entity.UserEntity;
=======
import com.platform.dto.UserRequestDto;
import com.platform.dto.UserResponseDto;
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======
import com.platform.dto.UserRequestDto;
import com.platform.dto.UserResponseDto;
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
import com.platform.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

<<<<<<< HEAD
<<<<<<< HEAD
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController extends BaseController<UserEntity, UserRequestDto, UUID> {
=======
@RestController
@RequestMapping("/api/users")
public class UserController {
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======
@RestController
@RequestMapping("/api/users")
public class UserController {
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92

    private final UserService userService;

    public UserController(UserService userService) {
<<<<<<< HEAD
<<<<<<< HEAD
        super(userService);
        this.userService = userService;
    }

    @Override
    @PostMapping
    public ResponseEntity<UserRequestDto> create(@Valid @RequestBody UserRequestDto request) {
        return (ResponseEntity<UserRequestDto>) (ResponseEntity<?>) createUser(request);
    }

    public ResponseEntity<?> createUser(UserRequestDto request) {
=======
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<?> createUser(@Valid @RequestBody UserRequestDto request) {
<<<<<<< HEAD
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
        try {
            UserResponseDto createdUser = userService.createUser(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<String> handleValidationExceptions(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return ResponseEntity.badRequest().body(errorMessage);
    }

}
