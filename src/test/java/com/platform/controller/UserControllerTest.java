package com.platform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.dto.UserRequestDto;
import com.platform.dto.UserResponseDto;
import com.platform.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false) // Bypass security filters for controller logic testing
@ActiveProfiles("test")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @Test
    void createUser_ShouldReturn201_WhenUserIsCreated() throws Exception {
        UserRequestDto request = new UserRequestDto();
        request.setUsername("newclient");
        request.setPassword("securepassword");
        request.setRole("CLIENT");

        UserResponseDto response = new UserResponseDto();
        response.setId(1L);
        response.setUsername("newclient");
        response.setRole("CLIENT");

        when(userService.createUser(any(UserRequestDto.class))).thenReturn(response);

        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("newclient"))
                .andExpect(jsonPath("$.role").value("CLIENT"));
    }

    @Test
    void createUser_ShouldReturn400_WhenUsernameExists() throws Exception {
        UserRequestDto request = new UserRequestDto();
        request.setUsername("existinguser");
        request.setPassword("securepassword");
        request.setRole("CLIENT");

        when(userService.createUser(any(UserRequestDto.class)))
                .thenThrow(new IllegalArgumentException("username already exists"));

        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("username already exists"));
    }
}
