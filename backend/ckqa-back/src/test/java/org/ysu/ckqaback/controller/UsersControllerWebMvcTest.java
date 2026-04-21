package org.ysu.ckqaback.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.entity.Users;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.exception.GlobalExceptionHandler;
import org.ysu.ckqaback.service.UsersService;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UsersControllerWebMvcTest {

    private UsersService usersService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        usersService = Mockito.mock(UsersService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new UsersController(usersService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void shouldReturnUnifiedUserDetail() throws Exception {
        Users user = buildUser(1L, "u001", "tom", "Tom", "active");
        given(usersService.getRequiredById(1L)).willReturn(user);

        mockMvc.perform(get(ApiPaths.USERS + "/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.username").value("tom"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void shouldReturnUnifiedUserPage() throws Exception {
        Page<Users> page = new Page<>(2, 5, 11);
        page.setRecords(List.of(buildUser(2L, "u002", "jerry", "Jerry", "active")));
        given(usersService.pageUsers(2L, 5L, "jer", "active")).willReturn(page);

                mockMvc.perform(get(ApiPaths.USERS)
                        .param("page", "2")
                        .param("size", "5")
                        .param("username", "jer")
                        .param("status", "active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"))
                .andExpect(jsonPath("$.data.current").value(2))
                .andExpect(jsonPath("$.data.size").value(5))
                .andExpect(jsonPath("$.data.total").value(11))
                .andExpect(jsonPath("$.data.items[0].username").value("jerry"));
    }

    @Test
    void shouldReturnUnifiedValidationErrorWhenCreateRequestInvalid() throws Exception {
        mockMvc.perform(post(ApiPaths.USERS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001))
                .andExpect(jsonPath("$.message").value("参数校验失败"));
    }

    @Test
    void shouldCreateUserSuccessfully() throws Exception {
        Users createdUser = buildUser(3L, "u003", "alice", "Alice", "active");
        given(usersService.createUser(any())).willReturn(createdUser);

                mockMvc.perform(post(ApiPaths.USERS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userCode": "u003",
                                  "username": "alice",
                                  "displayName": "Alice",
                                  "passwordHash": "hash-001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"))
                .andExpect(jsonPath("$.data.id").value(3))
                .andExpect(jsonPath("$.data.username").value("alice"));
    }

    @Test
    void shouldReturnBusinessErrorWhenUserNotFound() throws Exception {
        given(usersService.getRequiredById(999L))
                .willThrow(new BusinessException(4044, org.springframework.http.HttpStatus.NOT_FOUND, "用户不存在"));

        mockMvc.perform(get(ApiPaths.USERS + "/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(4044))
                .andExpect(jsonPath("$.message").value("用户不存在"));
    }

    private Users buildUser(Long id, String userCode, String username, String displayName, String status) {
        Users user = new Users();
        user.setId(id);
        user.setUserCode(userCode);
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setStatus(status);
        user.setCreatedAt(LocalDateTime.of(2026, 4, 21, 11, 30));
        user.setUpdatedAt(LocalDateTime.of(2026, 4, 21, 11, 30));
        return user;
    }
}
