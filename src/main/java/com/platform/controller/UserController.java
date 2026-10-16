package com.platform.controller;

import com.platform.core.controller.BaseController;
import com.platform.dto.UserRequestDto;
import com.platform.dto.UserResponseDto;
import com.platform.entity.UserEntity;
import com.platform.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController extends BaseController<UserEntity, UserRequestDto, UUID> {

    private final UserService userService;

    public UserController(UserService userService) {
        super(userService);
        this.userService = userService;
    }

    @Override
    @PostMapping
    public ResponseEntity<UserRequestDto> create(@Valid @RequestBody UserRequestDto request) {
        return (ResponseEntity<UserRequestDto>) (ResponseEntity<?>) createUser(request);
    }

    public ResponseEntity<?> createUser(UserRequestDto request) {
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
