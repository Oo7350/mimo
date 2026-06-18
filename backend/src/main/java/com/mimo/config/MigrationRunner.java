package com.mimo.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

/**
 * 一次性 SQL 迁移执行器 — 替代 spring.sql.init, 兼容性更好
 *
 * 启动时检查并执行 db/migration/ 下的 V8__ / V9__ 脚本。
 * 使用 ADD COLUMN IF NOT EXISTS / CREATE TABLE IF NOT EXISTS 风格的迁移时
 * 不会重复执行报错。
 */
@Component
@Slf4j
public class MigrationRunner implements ApplicationRunner {

    private final DataSource dataSource;

    public MigrationRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<String> scripts = new ArrayList<>();
        for (String name : new String[]{"db/migration/V8__work_logs.sql", "db/migration/V9__issue_gantt.sql"}) {
            Resource r = new ClassPathResource(name);
            if (r.exists()) scripts.add(name);
        }
        if (scripts.isEmpty()) return;

        for (String s : scripts) {
            try {
                log.info("[MigrationRunner] 执行迁移: {}", s);
                ScriptUtils.executeSqlScript(dataSource.getConnection(),
                        new ClassPathResource(s));
                log.info("[MigrationRunner] 完成: {}", s);
            } catch (Exception e) {
                // IF NOT EXISTS 类的语句重跑会报 duplicate, 视为成功
                String msg = e.getMessage() == null ? "" : e.getMessage();
                if (msg.contains("Duplicate") || msg.contains("already exists")
                        || msg.contains("check that column/key exists")) {
                    log.info("[MigrationRunner] 跳过 (已执行过): {} -- {}", s, msg.split("\n")[0]);
                } else {
                    log.error("[MigrationRunner] 失败: {} -- {}", s, msg.split("\n")[0]);
                }
            }
        }
    }
}
