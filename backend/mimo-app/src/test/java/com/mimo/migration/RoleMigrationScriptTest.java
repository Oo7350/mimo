package com.mimo.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 迁移脚本卫生检查（BUG-098 的回归闸门）。
 *
 * 背景：{@code MigrationRunner} 每次启动都会重跑 db/migration/V*.sql，所以每个脚本都必须自己幂等。
 * {@code user_roles} 的 GLOBAL 行 scope_id 是 NULL，而 MySQL 唯一键认为 NULL 互不相等，
 * 于是 {@code INSERT IGNORE} 对这类行等于没有约束——以前就是靠它"幂等"，结果每次重启都给
 * 每个用户多插一条授权，堆到十几份。这里把这条规则钉住：写入 user_roles 的语句必须是
 * 显式 NOT EXISTS 判重，且脚本里不能再用 INSERT IGNORE 往 user_roles 插数据。
 */
class RoleMigrationScriptTest {

    private static final List<String> SCRIPTS = List.of(
            "db/migration/V11__rbac.sql",
            "db/migration/V12__fix_user_role_migration.sql"
    );

    @Test
    @DisplayName("迁移脚本写 user_roles 时必须显式判重，不能依赖 INSERT IGNORE")
    void userRoleInsertsMustBeExplicitlyIdempotent() throws Exception {
        List<String> problems = new ArrayList<>();
        for (String script : SCRIPTS) {
            String sql = read(script);
            String normalized = sql.replaceAll("--[^\\n]*", ""); // 去掉行注释再判断
            if (normalized.toUpperCase().contains("INSERT IGNORE INTO USER_ROLES")) {
                problems.add(script + "：仍在用 INSERT IGNORE 写 user_roles（NULL 唯一键不生效）");
            }
            for (String stmt : normalized.split(";")) {
                if (!stmt.toUpperCase().contains("INSERT INTO USER_ROLES")) continue;
                if (!stmt.toUpperCase().contains("NOT EXISTS")) {
                    problems.add(script + "：写入 user_roles 的语句缺少 NOT EXISTS 判重");
                }
            }
        }
        assertThat(problems).as("迁移脚本幂等性检查").isEmpty();
    }

    @Test
    @DisplayName("V12 必须清理历史重复授权")
    void v12MustCleanHistoricalDuplicates() throws Exception {
        String normalized = read("db/migration/V12__fix_user_role_migration.sql")
                .replaceAll("--[^\\n]*", "");
        assertThat(normalized.toUpperCase()).contains("DELETE");
        assertThat(normalized.toUpperCase()).contains("IS NULL"); // NULL 安全的重复判定
    }

    private String read(String classpath) throws Exception {
        ClassPathResource resource = new ClassPathResource(classpath);
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }
}
