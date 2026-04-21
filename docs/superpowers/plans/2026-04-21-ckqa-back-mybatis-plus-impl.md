# CKQA Back MyBatis-Plus Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `backend/ckqa-back/` 中接入 MyBatis-Plus、连接现有 `ocqa` MySQL 库，并一次性生成全部 21 张表的标准 `entity/mapper/service/controller` 骨架代码。

**Architecture:** 保持现有 Spring Boot 4 骨架不变，在其上补充 MyBatis-Plus Boot4 starter、基础配置类和一个可重复执行的代码生成器。运行时通过环境变量连接数据库，生成结果平铺到标准包结构，后续业务开发直接在生成代码基础上迭代。

**Tech Stack:** Java 21, Spring Boot 4.0.5, Maven, MyBatis-Plus 3.5.16, MySQL 8, Freemarker, JUnit 5

---

### Task 1: 补齐文档与依赖边界

**Files:**
- Create: `docs/superpowers/specs/2026-04-21-ckqa-back-mybatis-plus-design.md`
- Create: `docs/superpowers/plans/2026-04-21-ckqa-back-mybatis-plus-impl.md`
- Modify: `backend/ckqa-back/pom.xml`

- [ ] **Step 1: 明确设计已落档**

确认设计文档存在，并以其中的 21 张表清单作为生成范围真相来源。

- [ ] **Step 2: 更新 Maven 依赖**

在 `backend/ckqa-back/pom.xml` 中加入：

```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot4-starter</artifactId>
    <version>3.5.16</version>
</dependency>
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-generator</artifactId>
    <version>3.5.16</version>
</dependency>
<dependency>
    <groupId>org.freemarker</groupId>
    <artifactId>freemarker</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 3: 编译依赖树**

Run: `mvn -q -DskipTests compile`

Expected: 依赖可以解析，项目至少进入编译阶段。

### Task 2: 用测试锁定 MyBatis-Plus 基础配置

**Files:**
- Create: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/config/MybatisPlusConfigTest.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/config/MybatisPlusConfig.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/CkqaBackApplication.java`

- [ ] **Step 1: 写失败测试**

```java
package org.ysu.ckqaback.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class MybatisPlusConfigTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(MybatisPlusConfig.class);

    @Test
    void shouldRegisterPaginationInterceptor() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(MybatisPlusInterceptor.class);
            MybatisPlusInterceptor interceptor = context.getBean(MybatisPlusInterceptor.class);
            assertThat(interceptor.getInterceptors())
                    .anyMatch(PaginationInnerInterceptor.class::isInstance);
        });
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -Dtest=MybatisPlusConfigTest test`

Expected: 因 `MybatisPlusConfig` 尚不存在或 Bean 未注册而失败。

- [ ] **Step 3: 写最小实现**

```java
package org.ysu.ckqaback.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
```

并在启动类上增加：

```java
@MapperScan("org.ysu.ckqaback.mapper")
```

- [ ] **Step 4: 再跑测试确认通过**

Run: `mvn -q -Dtest=MybatisPlusConfigTest test`

Expected: PASS

### Task 3: 用测试锁定代码生成器参数行为

**Files:**
- Create: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/codegen/MybatisPlusCodeGeneratorTest.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/codegen/MybatisPlusCodeGenerator.java`

- [ ] **Step 1: 写失败测试**

```java
package org.ysu.ckqaback.codegen;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MybatisPlusCodeGeneratorTest {

    @Test
    void shouldUseAllTablesByDefault() {
        MybatisPlusCodeGenerator.GeneratorOptions options =
                MybatisPlusCodeGenerator.GeneratorOptions.parse(new String[0]);

        assertThat(options.tables()).contains("courses", "qa_messages", "authorization_audit_logs");
        assertThat(options.overwrite()).isFalse();
    }

    @Test
    void shouldParseCustomTablesAndOverwriteFlag() {
        MybatisPlusCodeGenerator.GeneratorOptions options =
                MybatisPlusCodeGenerator.GeneratorOptions.parse(
                        new String[]{"--tables=users,roles", "--overwrite=true"});

        assertThat(options.tables()).containsExactly("users", "roles");
        assertThat(options.overwrite()).isTrue();
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -Dtest=MybatisPlusCodeGeneratorTest test`

Expected: 因 `MybatisPlusCodeGenerator` 尚不存在而失败。

- [ ] **Step 3: 写最小实现**

实现一个带 `main` 方法和内部 `GeneratorOptions` 记录类型的生成器，至少支持：

```java
public record GeneratorOptions(List<String> tables, boolean overwrite) {
    static GeneratorOptions parse(String[] args) { ... }
}
```

默认表清单必须覆盖 21 张表，支持 `--tables=` 与 `--overwrite=`。

- [ ] **Step 4: 再跑测试确认通过**

Run: `mvn -q -Dtest=MybatisPlusCodeGeneratorTest test`

Expected: PASS

### Task 4: 配置真实数据库与 MyBatis-Plus 运行参数

**Files:**
- Modify: `backend/ckqa-back/src/main/resources/application.properties`
- Create: `backend/ckqa-back/.env.example`

- [ ] **Step 1: 配置运行时数据源**

将 `application.properties` 更新为：

```properties
spring.application.name=ckqa-back
spring.datasource.url=jdbc:mysql://${MYSQL_HOST:localhost}:${MYSQL_PORT:23306}/${MYSQL_DATABASE:ocqa}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true&tinyInt1isBit=true
spring.datasource.username=${MYSQL_USER:root}
spring.datasource.password=${MYSQL_PASSWORD:}
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

mybatis-plus.mapper-locations=classpath*:mapper/*.xml
mybatis-plus.configuration.map-underscore-to-camel-case=true
mybatis-plus.global-config.db-config.logic-delete-field=is_deleted
mybatis-plus.global-config.db-config.logic-delete-value=1
mybatis-plus.global-config.db-config.logic-not-delete-value=0
```

- [ ] **Step 2: 提供环境变量示例**

在 `.env.example` 中写入：

```properties
MYSQL_HOST=localhost
MYSQL_PORT=23306
MYSQL_DATABASE=ocqa
MYSQL_USER=root
MYSQL_PASSWORD=your-password
```

- [ ] **Step 3: 运行基础测试**

Run: `mvn -q test`

Expected: 当前已有测试全部通过。

### Task 5: 实现真实代码生成器

**Files:**
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/codegen/MybatisPlusCodeGenerator.java`

- [ ] **Step 1: 在生成器中接入 FastAutoGenerator**

核心代码至少包含：

```java
FastAutoGenerator.create(jdbcUrl, username, password)
        .globalConfig(builder -> builder
                .author(author)
                .disableOpenDir()
                .outputDir(outputDir.toString()))
        .packageConfig(builder -> builder
                .parent(basePackage)
                .entity("entity")
                .mapper("mapper")
                .service("service")
                .serviceImpl("service.impl")
                .controller("controller")
                .pathInfo(Map.of(OutputFile.xml, xmlDir)))
        .strategyConfig(builder -> builder
                .addInclude(options.tables())
                .entityBuilder()
                    .enableLombok()
                    .enableTableFieldAnnotation()
                    .logicDeleteColumnName("is_deleted")
                .controllerBuilder()
                    .enableRestStyle()
                .serviceBuilder()
                    .formatServiceFileName("%sService")
                    .formatServiceImplFileName("%sServiceImpl")
                .mapperBuilder()
                    .enableMapperAnnotation()
                    .enableBaseResultMap()
                    .enableBaseColumnList())
        .templateEngine(new FreemarkerTemplateEngine())
        .execute();
```

- [ ] **Step 2: 支持覆盖选项**

当 `--overwrite=true` 时启用覆盖模式；否则保留已有文件。

- [ ] **Step 3: 运行参数测试**

Run: `mvn -q -Dtest=MybatisPlusCodeGeneratorTest test`

Expected: PASS

### Task 6: 生成全部 21 张表的标准代码

**Files:**
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/*.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/mapper/*.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/*.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/*.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/*.java`
- Create: `backend/ckqa-back/src/main/resources/mapper/*.xml`

- [ ] **Step 1: 执行全量生成**

Run:

```bash
export MYSQL_HOST=localhost
export MYSQL_PORT=23306
export MYSQL_DATABASE=ocqa
export MYSQL_USER=root
export MYSQL_PASSWORD=123456
mvn -q -DskipTests compile exec:java -Dexec.mainClass=org.ysu.ckqaback.codegen.MybatisPlusCodeGenerator -Dexec.args="--overwrite=true"
```

Expected: 21 张表对应的实体、Mapper、Service、Controller 与 XML 文件成功落盘。

- [ ] **Step 2: 抽查生成结果**

至少确认这些文件存在：

```text
src/main/java/org/ysu/ckqaback/entity/Courses.java
src/main/java/org/ysu/ckqaback/entity/Users.java
src/main/java/org/ysu/ckqaback/controller/QaMessagesController.java
src/main/resources/mapper/AuthorizationAuditLogsMapper.xml
```

### Task 7: 最终编译、测试与文档更新

**Files:**
- Modify: `backend/ckqa-back/README.md`
- Modify: `backend/ckqa-back/mvnw` (执行权限)

- [ ] **Step 1: 修正文档**

在 `README.md` 中补充：

- MyBatis-Plus 已接入
- 运行前所需环境变量
- 全量生成命令
- 测试命令
- 启动命令

- [ ] **Step 2: 恢复 Maven Wrapper 可执行权限**

Run: `chmod +x backend/ckqa-back/mvnw`

Expected: 后续可以直接使用 `./mvnw`。

- [ ] **Step 3: 完整验证**

Run:

```bash
mvn -q test
mvn -q -DskipTests compile
mvn -q spring-boot:run -Dspring-boot.run.arguments=--spring.main.web-application-type=none
```

Expected:

- 测试通过
- 编译通过
- 应用可以启动到 Spring 上下文完成阶段

### Self-Review

- 规格覆盖：已覆盖依赖接入、数据源配置、生成器、全量生成、验证、文档更新。
- 占位扫描：无 `TODO/TBD/implement later` 占位项。
- 类型一致性：统一使用 `org.ysu.ckqaback`、`MybatisPlusConfig`、`MybatisPlusCodeGenerator` 这些名称。
