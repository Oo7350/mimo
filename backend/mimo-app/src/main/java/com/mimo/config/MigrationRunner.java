package com.mimo.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 一次性 SQL 迁移执行器 — 替代 spring.sql.init, 兼容性更好
 *
 * 启动时扫描 classpath:db/migration/V*.sql，按字典序执行。
 * 关键特性：脚本内按 ';' 切分单条语句独立执行，单条失败（如 duplicate column）跳过该条继续，
 * 保证幂等重跑不会因为某条语句重复执行而把整批回滚掉。
 *
 * <p>{@code @Order(HIGHEST_PRECEDENCE)}：建表必须最先跑。其它启动期逻辑（{@link AdminBootstrap}）
 * 会立刻查表；两个 {@code ApplicationRunner} 都不标顺序时，谁先跑是"看扫描顺序"，
 * 空库上就撞过一次——第一个查表的 Runner 抢在建表前面，直接
 * {@code Table 'mimo.users' doesn't exist} 把启动搞崩（v2.22.2，见 bug-log BUG-123）。</p>
 *
 * <p><b>就绪信号</b>：最后一行 {@code [MigrationRunner] 全部迁移完成} 才是"库建完了"的证据。
 * 不要拿 {@code Started MimoApplication} 当信号——那句是 Spring 刷完上下文的日志，
 * 出现在 {@code ApplicationRunner} **之前**，实测比迁移跑完早 8 秒左右（v2.22.4，BUG-134：
 * CI 里"启动后端"这步就是靠它判的，等于没判）。</p>
 */
@Component
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MigrationRunner implements ApplicationRunner {

    private final DataSource dataSource;

    public MigrationRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:db/migration/V*.sql");
        List<String> scripts = Arrays.stream(resources)
                .map(Resource::getFilename)
                .sorted()
                .map(n -> "db/migration/" + n)
                .collect(Collectors.toList());
        if (scripts.isEmpty()) return;

        for (String s : scripts) {
            try (java.sql.Connection conn = dataSource.getConnection()) {
                log.info("[MigrationRunner] 执行迁移: {}", s);
                runScriptPerStatement(conn, s);
                log.info("[MigrationRunner] 完成: {}", s);
            } catch (Exception e) {
                String msg = e.getMessage() == null ? "" : e.getMessage();
                log.error("[MigrationRunner] 失败: {} -- {}", s, msg.split("\n")[0]);
            }
        }
        // 这一行是"库建完了"的唯一证据，CI 的就绪判定就等它（BUG-134）。
        // 别改成按文件名的"完成: V9…"来判：脚本按字典序执行，最后一个可能是 V9，
        // 中间插一个 V26 就又不准了。
        log.info("[MigrationRunner] 全部迁移完成: {} 个脚本", scripts.size());
    }

    /**
     * 按 ';' 切分 SQL 脚本为单条语句，每条独立执行。
     * 单条失败时若属于"已存在"类错误（Duplicate/already exists）跳过，其他错误打 warn 但继续执行下一条。
     * 这样既能处理 ALTER ADD COLUMN 的幂等性，又能保证后续 MODIFY/CREATE INDEX 语句不被整批回滚。
     */
    private void runScriptPerStatement(java.sql.Connection conn, String scriptPath) throws Exception {
        ClassPathResource res = new ClassPathResource(scriptPath);
        StringBuilder content = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                content.append(line).append('\n');
            }
        }
        // 简单切分：按 ';' 切，忽略被单引号包裹的分号（不完美但够用于 ALTER/CREATE 类迁移）
        List<String> stmts = splitStatements(content.toString());
        int ok = 0, skipped = 0, failed = 0;
        try (java.sql.Statement stmt = conn.createStatement()) {
            for (String sql : stmts) {
                // 剥离语句开头的 -- 行注释（每行 --xxx\n）
                String stripped = sql.replaceAll("^(\\s*--[^\\n]*[\\r\\n]*)+", "");
                String trimmed = stripped.trim();
                if (trimmed.isEmpty()) continue;
                try {
                    stmt.execute(trimmed);
                    ok++;
                } catch (Exception e) {
                    String msg = e.getMessage() == null ? "" : e.getMessage();
                    if (msg.contains("Duplicate") || msg.contains("already exists")
                            || msg.contains("check that column/key exists")
                            || msg.contains("Duplicate key name")
                            || msg.contains("Duplicate column")) {
                        skipped++;
                    } else {
                        failed++;
                        log.warn("[MigrationRunner] 单语句失败: {} -- {}", trimmed.substring(0, Math.min(80, trimmed.length())), msg.split("\n")[0]);
                    }
                }
            }
        }
        log.info("[MigrationRunner] {} 语句统计: ok={} skipped={} failed={}", scriptPath, ok, skipped, failed);
    }

    private List<String> splitStatements(String content) {
        List<String> out = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inSingle = false, inDouble = false;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            cur.append(c);
            if (c == '\'' && !inDouble) inSingle = !inSingle;
            else if (c == '"' && !inSingle) inDouble = !inDouble;
            else if (c == ';' && !inSingle && !inDouble) {
                out.add(cur.toString());
                cur.setLength(0);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }
}
