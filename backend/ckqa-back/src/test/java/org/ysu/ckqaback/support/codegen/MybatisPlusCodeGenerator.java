package org.ysu.ckqaback.support.codegen;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MybatisPlusCodeGenerator {

    private static final List<String> ALL_TABLES = List.of(
            "courses",
            "parse_logs",
            "parse_results",
            "material_objects",
            "course_materials",
            "users",
            "roles",
            "permissions",
            "user_roles",
            "role_permissions",
            "auth_identities",
            "course_memberships",
            "course_membership_events",
            "knowledge_bases",
            "kb_documents",
            "index_runs",
            "index_artifacts",
            "qa_sessions",
            "qa_messages",
            "qa_retrieval_logs",
            "qa_retrieval_hits",
            "authorization_audit_logs"
    );

    private MybatisPlusCodeGenerator() {
    }

    public static void main(String[] args) {
        GeneratorOptions options = GeneratorOptions.parse(args);
        ensureDirectories(options);

        FastAutoGenerator generator = FastAutoGenerator.create(options.jdbcUrl(), options.username(), options.password())
                .globalConfig(builder -> builder
                        .author(options.author())
                        .disableOpenDir()
                        .commentDate("yyyy-MM-dd")
                        .outputDir(options.outputDir().toString()))
                .packageConfig(builder -> builder
                        .parent(options.basePackage())
                        .entity("entity")
                        .mapper("mapper")
                        .service("service")
                        .serviceImpl("service.impl")
                        .controller("controller")
                        .pathInfo(Map.of(OutputFile.xml, options.xmlDir().toString())))
                .strategyConfig(builder -> {
                    builder.addInclude(options.tables());

                    var entityBuilder = builder.entityBuilder()
                            .enableLombok()
                            .enableTableFieldAnnotation()
                            .logicDeleteColumnName("is_deleted");
                    var mapperBuilder = builder.mapperBuilder()
                            .enableMapperAnnotation()
                            .enableBaseResultMap()
                            .enableBaseColumnList();
                    var serviceBuilder = builder.serviceBuilder()
                            .formatServiceFileName("%sService")
                            .formatServiceImplFileName("%sServiceImpl");
                    var controllerBuilder = builder.controllerBuilder()
                            .enableRestStyle();

                    if (options.overwrite()) {
                        entityBuilder.enableFileOverride();
                        mapperBuilder.enableFileOverride();
                        serviceBuilder.enableFileOverride();
                        controllerBuilder.enableFileOverride();
                    }
                })
                .templateEngine(new FreemarkerTemplateEngine());

        generator.execute();
    }

    private static void ensureDirectories(GeneratorOptions options) {
        try {
            Files.createDirectories(options.outputDir());
            Files.createDirectories(options.xmlDir());
        } catch (Exception exception) {
            throw new IllegalStateException("创建代码输出目录失败", exception);
        }
    }

    public record GeneratorOptions(
            List<String> tables,
            boolean overwrite,
            String author,
            String basePackage,
            Path outputDir,
            Path xmlDir,
            String host,
            int port,
            String database,
            String username,
            String password
    ) {

        public static GeneratorOptions parse(String[] args) {
            Map<String, String> argumentMap = parseArguments(args);
            Path projectDir = Path.of(System.getProperty("user.dir"));

            return new GeneratorOptions(
                    parseTables(argumentMap.get("tables")),
                    parseBoolean(argumentMap.getOrDefault("overwrite", "false"), "overwrite"),
                    argumentMap.getOrDefault("author", "codex"),
                    argumentMap.getOrDefault("base-package", "org.ysu.ckqaback"),
                    Path.of(argumentMap.getOrDefault("output-dir", projectDir.resolve("src/main/java").toString())),
                    Path.of(argumentMap.getOrDefault("xml-dir", projectDir.resolve("src/main/resources/mapper").toString())),
                    argumentMap.getOrDefault("host", envOrDefault("MYSQL_HOST", "localhost")),
                    parseInteger(argumentMap.getOrDefault("port", envOrDefault("MYSQL_PORT", "23306")), "port"),
                    argumentMap.getOrDefault("database", envOrDefault("MYSQL_DATABASE", "ocqa")),
                    argumentMap.getOrDefault("username", envOrDefault("MYSQL_USER", "root")),
                    argumentMap.getOrDefault("password", envOrDefault("MYSQL_PASSWORD", ""))
            );
        }

        public String jdbcUrl() {
            return String.format(
                    "jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true&tinyInt1isBit=true",
                    host,
                    port,
                    database
            );
        }

        private static Map<String, String> parseArguments(String[] args) {
            Map<String, String> arguments = new LinkedHashMap<>();
            for (String arg : args) {
                if (!arg.startsWith("--") || !arg.contains("=")) {
                    throw new IllegalArgumentException("参数格式必须为 --key=value: " + arg);
                }
                String raw = arg.substring(2);
                int separatorIndex = raw.indexOf('=');
                String key = raw.substring(0, separatorIndex);
                String value = raw.substring(separatorIndex + 1);
                arguments.put(key, value);
            }
            return arguments;
        }

        private static List<String> parseTables(String tablesArgument) {
            if (tablesArgument == null || tablesArgument.isBlank() || "all".equalsIgnoreCase(tablesArgument.trim())) {
                return ALL_TABLES;
            }
            return Arrays.stream(tablesArgument.split(","))
                    .map(String::trim)
                    .filter(table -> !table.isEmpty())
                    .distinct()
                    .toList();
        }

        private static boolean parseBoolean(String rawValue, String fieldName) {
            if ("true".equalsIgnoreCase(rawValue)) {
                return true;
            }
            if ("false".equalsIgnoreCase(rawValue)) {
                return false;
            }
            throw new IllegalArgumentException("参数 " + fieldName + " 必须为 true 或 false");
        }

        private static int parseInteger(String rawValue, String fieldName) {
            try {
                return Integer.parseInt(rawValue);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("参数 " + fieldName + " 必须为整数", exception);
            }
        }

        private static String envOrDefault(String envName, String defaultValue) {
            String value = System.getenv(envName);
            return value == null || value.isBlank() ? defaultValue : value;
        }
    }
}
