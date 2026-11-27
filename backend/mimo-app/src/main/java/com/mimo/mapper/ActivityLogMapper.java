package com.mimo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mimo.entity.ActivityLog;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface ActivityLogMapper extends BaseMapper<ActivityLog> {

    /**
     * 趋势聚合结果：某天 + 条数。
     *
     * <p>只在小结果集上用（一天一行），不要拿它当明细查询的返回类型。
     */
    @Data
    class TrendCountRow {
        /** 日期（DATE(created_at) 的结果） */
        private LocalDate day;
        /** 当天条数 */
        private long cnt;
    }

    /**
     * 近 N 天按天汇总的"新建"事件数（v2.22.4）。
     *
     * <p>为什么要下沉到 SQL：原来是把近 7 天的 CREATE/MOVE 明细整批查进 JVM 再在内存里数。
     * 开发库 3 个人时一天几十条，看不出来；造了 5000 条活动日志之后，
     * 一个 `/workbench/team` 光这一步就要把几千行（含 detail 文本）搬进内存——
     * 线程栈采样里 4/6 次都停在这个查询上。聚合进库后返回最多 7 行。
     */
    @Select("""
        <script>
        SELECT DATE(al.created_at) AS day, COUNT(*) AS cnt
        FROM activity_logs al
        WHERE al.action = 'CREATE'
          AND al.created_at &gt;= #{since}
          AND al.project_id IN
          <foreach collection="projectIds" item="pid" open="(" separator="," close=")">#{pid}</foreach>
        GROUP BY DATE(al.created_at)
        </script>
        """)
    List<TrendCountRow> countCreatedByDay(@Param("projectIds") Collection<Long> projectIds,
                                          @Param("since") LocalDateTime since);

    /**
     * 近 N 天按天汇总的"移动到终态列"事件数（v2.22.4）。
     *
     * <p>判定口径与 {@code BoardColumnRules.isTerminalName} 保持同源：终态列名单由 Java 侧算好再传进来，
     * 并要求"这个项目当前确实存在这个列"（JOIN board_columns），所以列被改名之后的历史事件仍会被跳过
     * ——少算不多算，和改造前的内存版一致。
     *
     * <p>目标列名从事件描述里取：`TRIM(SUBSTRING_INDEX(detail, '移动到', -1))` 等价于 Java 里
     * "取最后一次『移动到』之后的文本并 trim"。用 COUNT(DISTINCT al.id) 是因为一条日志可能
     * 同时命中同项目的多个列名（列名互为子串时），按日志去重才不会多算。
     */
    @Select("""
        <script>
        SELECT DATE(al.created_at) AS day, COUNT(DISTINCT al.id) AS cnt
        FROM activity_logs al
        JOIN board_columns bc ON bc.project_id = al.project_id
        WHERE al.action = 'MOVE'
          AND al.created_at &gt;= #{since}
          AND al.project_id IN
          <foreach collection="projectIds" item="pid" open="(" separator="," close=")">#{pid}</foreach>
          AND bc.name IN
          <foreach collection="terminalNames" item="name" open="(" separator="," close=")">#{name}</foreach>
          AND TRIM(SUBSTRING_INDEX(al.detail, '移动到', -1)) = bc.name
        GROUP BY DATE(al.created_at)
        </script>
        """)
    List<TrendCountRow> countMovedToTerminalByDay(@Param("projectIds") Collection<Long> projectIds,
                                                  @Param("since") LocalDateTime since,
                                                  @Param("terminalNames") Collection<String> terminalNames);
}
