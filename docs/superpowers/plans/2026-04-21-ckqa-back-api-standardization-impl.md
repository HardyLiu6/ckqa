# CKQA Back API Standardization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `ckqa-back` 的代码生成器迁移到测试目录，统一所有控制器的 `/api/v1` 路由风格，并落地统一返回体、全局异常处理、参数校验与 `users` 示例接口。

**Status:** 2026-04-21 已按本计划完成实现，并通过 `./mvnw -q test`、`./mvnw -q -DskipTests compile` 与基于现有 MySQL 参数的启动验证。

**Architecture:** 在不推翻已有 MyBatis-Plus 基线的前提下，新增 API 基础设施层与 `users` DTO/业务方法；保留生成代码主体，仅做增量改造。代码生成器迁移到测试侧后通过 test classpath 执行，业务运行时代码只保留真正服务请求所需部分。

**Tech Stack:** Java 21, Spring Boot 4.0.5, MyBatis-Plus 3.5.16, Maven, JUnit 5, MockMvc, Jakarta Validation

---

### Task 1: 迁移代码生成器到测试目录

**Files:**
- Move: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/codegen/MybatisPlusCodeGenerator.java` -> `backend/ckqa-back/src/test/java/org/ysu/ckqaback/support/codegen/MybatisPlusCodeGenerator.java`
- Move: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/codegen/MybatisPlusCodeGeneratorTest.java` -> `backend/ckqa-back/src/test/java/org/ysu/ckqaback/support/codegen/MybatisPlusCodeGeneratorTest.java`
- Modify: `backend/ckqa-back/pom.xml`
- Modify: `backend/ckqa-back/README.md`

- [ ] **Step 1: 写失败测试**

```java
package org.ysu.ckqaback.support.codegen;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MybatisPlusCodeGeneratorTest {

    @Test
    void shouldUseAllTablesByDefault() {
        MybatisPlusCodeGenerator.GeneratorOptions options =
                MybatisPlusCodeGenerator.GeneratorOptions.parse(new String[0]);

        assertThat(options.tables()).contains("users", "roles", "authorization_audit_logs");
        assertThat(options.overwrite()).isFalse();
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd backend/ckqa-back && ./mvnw -q -Dtest=org.ysu.ckqaback.support.codegen.MybatisPlusCodeGeneratorTest test`

Expected: FAIL，因为类还没迁移到新包路径。

- [ ] **Step 3: 写最小实现**

将生成器与测试迁移到 `src/test/java/org/ysu/ckqaback/support/codegen/`，更新 `package` 声明，并把 `pom.xml` 中：

- `mybatis-plus-generator`
- `freemarker`

改为 `test` 作用域。

README 生成命令改为：

```bash
./mvnw -q -DskipTests test-compile exec:java \
  -Dexec.classpathScope=test \
  -Dexec.mainClass=org.ysu.ckqaback.support.codegen.MybatisPlusCodeGenerator \
  -Dexec.args="--overwrite=true"
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd backend/ckqa-back && ./mvnw -q -Dtest=org.ysu.ckqaback.support.codegen.MybatisPlusCodeGeneratorTest test`

Expected: PASS

### Task 2: 增加统一返回体与 API 路由常量

**Files:**
- Create: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/api/ApiResponseUtilsTest.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiPaths.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiResponse.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiErrorDetail.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiPageData.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiResponseUtils.java`

- [ ] **Step 1: 写失败测试**

```java
package org.ysu.ckqaback.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseUtilsTest {

    @Test
    void shouldBuildSuccessResponse() {
        ApiResponse<String> response = ApiResponseUtils.success("ok");

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("操作成功");
        assertThat(response.getData()).isEqualTo("ok");
        assertThat(response.getTimestamp()).isNotNull();
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd backend/ckqa-back && ./mvnw -q -Dtest=ApiResponseUtilsTest test`

Expected: FAIL，因为统一返回体类尚不存在。

- [ ] **Step 3: 写最小实现**

实现最小返回体结构：

```java
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;
    private LocalDateTime timestamp;
}
```

并在 `ApiResponseUtils` 中提供：

```java
public static <T> ApiResponse<T> success(T data)
public static <T> ApiResponse<T> success(String message, T data)
public static <T> ApiResponse<T> error(int code, String message, T data)
```

`ApiPaths` 中定义 `public static final String` 常量，至少覆盖全部 21 个资源路径。

- [ ] **Step 4: 运行测试确认通过**

Run: `cd backend/ckqa-back && ./mvnw -q -Dtest=ApiResponseUtilsTest test`

Expected: PASS

### Task 3: 增加校验依赖与全局异常处理

**Files:**
- Modify: `backend/ckqa-back/pom.xml`
- Create: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/exception/GlobalExceptionHandlerWebMvcTest.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/exception/BusinessException.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: 写失败测试**

```java
package org.ysu.ckqaback.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GlobalExceptionHandlerWebMvcTest.DemoController.class)
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnUnifiedValidationError() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001))
                .andExpect(jsonPath("$.message").value("参数校验失败"));
    }

    @RestController
    static class DemoController {
        @PostMapping("/test/validate")
        String validate(@Valid @RequestBody DemoRequest request) {
            return "ok";
        }
    }

    record DemoRequest(@NotBlank(message = "name不能为空") String name) {}
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd backend/ckqa-back && ./mvnw -q -Dtest=GlobalExceptionHandlerWebMvcTest test`

Expected: FAIL，因为 validation 依赖或异常处理器尚未到位。

- [ ] **Step 3: 写最小实现**

在 `pom.xml` 加入：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

实现：

- `BusinessException`：包含 `code`、`HttpStatus`、`message`
- `GlobalExceptionHandler`：处理 `MethodArgumentNotValidException`、`BindException`、`ConstraintViolationException`、`HttpMessageNotReadableException`、`IllegalArgumentException`、`BusinessException`、`Exception`

- [ ] **Step 4: 运行测试确认通过**

Run: `cd backend/ckqa-back && ./mvnw -q -Dtest=GlobalExceptionHandlerWebMvcTest test`

Expected: PASS

### Task 4: 统一所有 controller 路由到 `/api/v1`

**Files:**
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/*.java`

- [ ] **Step 1: 写失败测试**

新增一个路由测试，至少锁定 `UsersController`：

```java
mockMvc.perform(get("/users/1"))
       .andExpect(status().isNotFound());
```

并补一个成功路径测试预期：

```java
mockMvc.perform(get("/api/v1/users/1"))
       .andExpect(status().isOk());
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd backend/ckqa-back && ./mvnw -q -Dtest=UsersControllerWebMvcTest test`

Expected: FAIL，因为控制器仍是旧路径或还没有可用接口。

- [ ] **Step 3: 写最小实现**

将所有控制器类级别的 `@RequestMapping` 统一改为 `ApiPaths` 常量，例如：

```java
@RestController
@RequestMapping(ApiPaths.USERS)
public class UsersController {
}
```

其余控制器只改路径，不加业务方法。

- [ ] **Step 4: 跑编译验证**

Run: `cd backend/ckqa-back && ./mvnw -q -DskipTests compile`

Expected: PASS

### Task 5: 落地 `users` 查询与创建接口

**Files:**
- Create: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/controller/UsersControllerWebMvcTest.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/user/dto/UserCreateRequest.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/user/dto/UserQueryRequest.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/user/dto/UserResponse.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/UsersController.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/UsersService.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/UsersServiceImpl.java`

- [ ] **Step 1: 写失败测试**

```java
package org.ysu.ckqaback.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.entity.Users;
import org.ysu.ckqaback.exception.GlobalExceptionHandler;
import org.ysu.ckqaback.service.UsersService;

import java.time.LocalDateTime;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UsersController.class)
@Import(GlobalExceptionHandler.class)
class UsersControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UsersService usersService;

    @Test
    void shouldReturnUnifiedUserDetail() throws Exception {
        Users user = new Users();
        user.setId(1L);
        user.setUserCode("u001");
        user.setUsername("tom");
        user.setDisplayName("Tom");
        user.setStatus("active");
        user.setCreatedAt(LocalDateTime.now());

        given(usersService.getRequiredById(1L)).willReturn(user);

        mockMvc.perform(get(ApiPaths.USERS + "/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("tom"));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd backend/ckqa-back && ./mvnw -q -Dtest=UsersControllerWebMvcTest test`

Expected: FAIL，因为接口与服务方法尚不存在。

- [ ] **Step 3: 写最小实现**

在 `UsersController` 中实现：

- `GET /api/v1/users/{id}`
- `GET /api/v1/users`
- `POST /api/v1/users`

在 `UsersService` / `UsersServiceImpl` 中增加：

```java
Users getRequiredById(Long id);
IPage<Users> pageUsers(UserQueryRequest request);
Users createUser(UserCreateRequest request);
```

创建逻辑要求：

- 校验 `userCode` 唯一
- 校验 `username` 唯一
- 默认 `status=active`

响应要求：

- 返回 `ApiResponse<UserResponse>`
- 不暴露 `passwordHash`

- [ ] **Step 4: 运行测试确认通过**

Run: `cd backend/ckqa-back && ./mvnw -q -Dtest=UsersControllerWebMvcTest test`

Expected: PASS

### Task 6: 补齐校验失败与业务异常测试

**Files:**
- Modify: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/controller/UsersControllerWebMvcTest.java`

- [ ] **Step 1: 添加参数失败测试**

增加：

```java
mockMvc.perform(post(ApiPaths.USERS)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{}"))
    .andExpect(status().isBadRequest())
    .andExpect(jsonPath("$.code").value(4001));
```

- [ ] **Step 2: 添加业务异常测试**

增加：

```java
given(usersService.getRequiredById(999L))
        .willThrow(new BusinessException(4044, HttpStatus.NOT_FOUND, "用户不存在"));
```

- [ ] **Step 3: 运行测试确认通过**

Run: `cd backend/ckqa-back && ./mvnw -q -Dtest=UsersControllerWebMvcTest,GlobalExceptionHandlerWebMvcTest test`

Expected: PASS

### Task 7: 更新文档并完成最终验证

**Files:**
- Modify: `backend/ckqa-back/README.md`

- [ ] **Step 1: 更新 README**

补充：

- 生成器已迁移到测试目录
- 新的生成命令
- `/api/v1` 统一风格
- `users` 示例接口说明

- [ ] **Step 2: 跑完整验证**

Run:

```bash
cd backend/ckqa-back
./mvnw -q test
./mvnw -q -DskipTests compile
./mvnw -q -DskipTests spring-boot:run -Dspring-boot.run.arguments=--spring.main.web-application-type=none
```

Expected:

- 测试通过
- 编译通过
- 应用启动成功

### Self-Review

- 规格覆盖：已覆盖生成器迁移、API 统一、返回体、异常处理、参数校验、`users` 示例接口和文档更新。
- 占位扫描：无 `TODO/TBD` 占位。
- 类型一致性：统一使用 `ApiPaths`、`ApiResponse`、`BusinessException`、`UsersController`、`UsersService` 命名。
