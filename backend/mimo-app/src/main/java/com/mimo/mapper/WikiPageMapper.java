package com.mimo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mimo.entity.WikiPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface WikiPageMapper extends BaseMapper<WikiPage> {

    /**
     * 一次性拉取项目所有页面（轻量字段，供前端构建目录树）。
     * 用原生 SQL 排除 LONGTEXT content / content_html 字段，避免传输超大字段。
     */
    @Select("SELECT id, project_id, parent_id, title, slug, author_id, editor_id, " +
            "version, sort_order, is_pinned, view_count, created_at, updated_at " +
            "FROM wiki_pages WHERE project_id = #{projectId} " +
            "ORDER BY is_pinned DESC, sort_order ASC, id ASC")
    List<WikiPage> listTreeByProject(@Param("projectId") Long projectId);

    /**
     * 全文检索（MySQL 8 走 FULLTEXT，但为了兼容性先用 LIKE）。
     */
    @Select("SELECT id, project_id, parent_id, title, author_id, editor_id, version, " +
            "is_pinned, view_count, created_at, updated_at, " +
            "LEFT(content, 300) AS content " +
            "FROM wiki_pages WHERE project_id = #{projectId} " +
            "AND (title LIKE CONCAT('%', #{q}, '%') OR content LIKE CONCAT('%', #{q}, '%')) " +
            "ORDER BY updated_at DESC LIMIT 50")
    List<WikiPage> search(@Param("projectId") Long projectId, @Param("q") String q);
}
