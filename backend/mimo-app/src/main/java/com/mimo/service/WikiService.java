package com.mimo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.WikiDTO;
import com.mimo.entity.Project;
import com.mimo.entity.User;
import com.mimo.entity.WikiAttachment;
import com.mimo.entity.WikiPage;
import com.mimo.entity.WikiVersion;
import com.mimo.mapper.ProjectMapper;
import com.mimo.mapper.UserMapper;
import com.mimo.mapper.WikiAttachmentMapper;
import com.mimo.mapper.WikiPageMapper;
import com.mimo.mapper.WikiVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Wiki 文档系统服务：树形目录 / 页面 CRUD（带版本快照）/ 全文检索 / 附件
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WikiService {

    private final WikiPageMapper wikiPageMapper;
    private final WikiVersionMapper wikiVersionMapper;
    private final WikiAttachmentMapper wikiAttachmentMapper;
    private final ProjectMapper projectMapper;
    private final UserMapper userMapper;
    private final TeamService teamService;

    @Value("${mimo.upload.dir:./uploads}")
    private String uploadDir;

    // ============ 树形目录 ============

    public List<WikiDTO.TreeVO> getTree(Long projectId) {
        requireProject(projectId);
        List<WikiPage> pages = wikiPageMapper.listTreeByProject(projectId);
        Map<Long, String> nameMap = loadUserNames(pages.stream()
                .map(WikiPage::getAuthorId).collect(Collectors.toSet()));

        Map<Long, WikiDTO.TreeVO> nodeMap = new HashMap<>();
        List<WikiDTO.TreeVO> roots = new ArrayList<>();
        // 第一遍：全量建节点
        for (WikiPage p : pages) {
            nodeMap.put(p.getId(), toTreeVO(p, nameMap));
        }
        // 第二遍：挂子节点，无父节点或父节点不在本项目的归为根
        for (WikiPage p : pages) {
            WikiDTO.TreeVO vo = nodeMap.get(p.getId());
            WikiDTO.TreeVO parent = nodeMap.get(p.getParentId());
            if (parent != null) {
                parent.getChildren().add(vo);
            } else {
                roots.add(vo);
            }
        }
        return roots;
    }

    // ============ 页面 CRUD ============

    public WikiDTO.PageVO getPage(Long id, Long userId) {
        WikiPage p = mustGet(id);
        if (userId != null) requireMember(p.getProjectId(), userId);
        // 浏览量 +1（延迟更新失败不影响读）
        p.setViewCount((p.getViewCount() == null ? 0 : p.getViewCount()) + 1);
        try {
            wikiPageMapper.updateById(p);
        } catch (Exception e) {
            log.warn("[Wiki] 浏览量更新失败 pageId={}", id, e);
        }
        User author = userMapper.selectById(p.getAuthorId());
        User editor = p.getEditorId() != null && !p.getEditorId().equals(p.getAuthorId())
                ? userMapper.selectById(p.getEditorId()) : author;
        return toPageVO(p, author == null ? null : author.getUsername(),
                editor == null ? null : editor.getUsername());
    }

    @Transactional
    public WikiDTO.PageVO create(WikiDTO.CreateRequest req, Long userId) {
        requireMember(req.getProjectId(), userId);
        if (!StringUtils.hasText(req.getTitle())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "页面标题不能为空");
        }
        WikiPage p = new WikiPage();
        p.setProjectId(req.getProjectId());
        p.setParentId(req.getParentId());
        p.setTitle(req.getTitle().trim());
        p.setContent(req.getContent() == null ? "" : req.getContent());
        p.setAuthorId(userId);
        p.setEditorId(userId);
        p.setVersion(1);
        p.setSortOrder(nextSortOrder(req.getProjectId(), req.getParentId()));
        wikiPageMapper.insert(p);
        User u = userMapper.selectById(userId);
        return toPageVO(p, u == null ? null : u.getUsername(), u == null ? null : u.getUsername());
    }

    @Transactional
    public WikiDTO.PageVO update(Long id, WikiDTO.UpdateRequest req, Long userId) {
        WikiPage p = mustGet(id);
        requireMember(p.getProjectId(), userId);

        // 保存当前版本快照（历史版本 = 编辑前的状态）
        saveVersionSnapshot(p, userId, req.getChangeSummary());

        boolean changed = false;
        if (req.getTitle() != null && !req.getTitle().trim().isEmpty() && !req.getTitle().equals(p.getTitle())) {
            p.setTitle(req.getTitle().trim());
            changed = true;
        }
        if (req.getContent() != null && !req.getContent().equals(p.getContent())) {
            p.setContent(req.getContent());
            changed = true;
        }
        if (req.getParentId() != null && !req.getParentId().equals(p.getParentId())) {
            p.setParentId(req.getParentId());
            changed = true;
        }
        if (changed) {
            p.setVersion((p.getVersion() == null ? 1 : p.getVersion()) + 1);
            p.setEditorId(userId);
            wikiPageMapper.updateById(p);
        }
        User u = userMapper.selectById(userId);
        return toPageVO(p, u == null ? null : u.getUsername(), u == null ? null : u.getUsername());
    }

    @Transactional
    public void delete(Long id, Long userId) {
        WikiPage p = mustGet(id);
        requireMember(p.getProjectId(), userId);
        deleteRecursive(p);
    }

    private void deleteRecursive(WikiPage p) {
        List<WikiPage> children = wikiPageMapper.selectList(new LambdaQueryWrapper<WikiPage>()
                .eq(WikiPage::getParentId, p.getId()));
        for (WikiPage c : children) {
            deleteRecursive(c);
        }
        wikiVersionMapper.delete(new LambdaQueryWrapper<WikiVersion>().eq(WikiVersion::getPageId, p.getId()));
        deleteAttachmentFiles(p.getId());
        wikiAttachmentMapper.delete(new LambdaQueryWrapper<WikiAttachment>().eq(WikiAttachment::getPageId, p.getId()));
        wikiPageMapper.deleteById(p.getId());
    }

    // ============ 版本历史 ============

    public List<WikiDTO.VersionVO> listVersions(Long pageId, Long userId) {
        WikiPage p = mustGet(pageId);
        requireMember(p.getProjectId(), userId);
        List<WikiVersion> versions = wikiVersionMapper.selectList(
                new LambdaQueryWrapper<WikiVersion>().eq(WikiVersion::getPageId, pageId)
                        .orderByDesc(WikiVersion::getVersion));
        Map<Long, String> nameMap = loadUserNames(versions.stream()
                .map(WikiVersion::getEditorId).collect(Collectors.toSet()));
        return versions.stream().map(v -> toVersionVO(v, nameMap.get(v.getEditorId()))).collect(Collectors.toList());
    }

    public WikiDTO.VersionVO getVersion(Long pageId, Integer version, Long userId) {
        WikiPage p = mustGet(pageId);
        requireMember(p.getProjectId(), userId);
        WikiVersion v = wikiVersionMapper.selectOne(new LambdaQueryWrapper<WikiVersion>()
                .eq(WikiVersion::getPageId, pageId).eq(WikiVersion::getVersion, version));
        if (v == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "版本不存在");
        }
        User editor = userMapper.selectById(v.getEditorId());
        return toVersionVO(v, editor == null ? null : editor.getUsername());
    }

    @Transactional
    public WikiDTO.PageVO restoreVersion(Long pageId, Integer version, Long userId) {
        WikiPage p = mustGet(pageId);
        requireMember(p.getProjectId(), userId);
        WikiVersion v = wikiVersionMapper.selectOne(new LambdaQueryWrapper<WikiVersion>()
                .eq(WikiVersion::getPageId, pageId).eq(WikiVersion::getVersion, version));
        if (v == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "版本不存在");
        }
        // 回滚本身也是一次编辑：先快照当前内容
        saveVersionSnapshot(p, userId, "回滚到版本 " + version);
        p.setTitle(v.getTitle());
        p.setContent(v.getContent());
        p.setVersion((p.getVersion() == null ? 1 : p.getVersion()) + 1);
        p.setEditorId(userId);
        wikiPageMapper.updateById(p);
        User u = userMapper.selectById(userId);
        return toPageVO(p, u == null ? null : u.getUsername(), u == null ? null : u.getUsername());
    }

    private void saveVersionSnapshot(WikiPage p, Long userId, String changeSummary) {
        WikiVersion v = new WikiVersion();
        v.setPageId(p.getId());
        v.setVersion(p.getVersion() == null ? 1 : p.getVersion());
        v.setTitle(p.getTitle());
        v.setContent(p.getContent() == null ? "" : p.getContent());
        v.setEditorId(userId);
        v.setChangeSummary(changeSummary);
        wikiVersionMapper.insert(v);
    }

    // ============ 全文检索 ============

    public List<WikiDTO.PageVO> search(Long projectId, String q, Long userId) {
        requireMember(projectId, userId);
        if (!StringUtils.hasText(q)) return List.of();
        List<WikiPage> pages = wikiPageMapper.search(projectId, q.trim());
        Map<Long, String> nameMap = loadUserNames(pages.stream()
                .map(WikiPage::getAuthorId).collect(Collectors.toSet()));
        return pages.stream()
                .map(p -> toPageVO(p, nameMap.get(p.getAuthorId()),
                        nameMap.get(p.getEditorId())))
                .collect(Collectors.toList());
    }

    // ============ 附件 ============

    public List<WikiDTO.AttachmentVO> listAttachments(Long pageId, Long userId) {
        WikiPage p = mustGet(pageId);
        requireMember(p.getProjectId(), userId);
        List<WikiAttachment> list = wikiAttachmentMapper.selectList(
                new LambdaQueryWrapper<WikiAttachment>().eq(WikiAttachment::getPageId, pageId)
                        .orderByDesc(WikiAttachment::getCreatedAt));
        Map<Long, String> nameMap = loadUserNames(list.stream()
                .map(WikiAttachment::getUploaderId).collect(Collectors.toSet()));
        return list.stream().map(a -> toAttachmentVO(a, nameMap.get(a.getUploaderId()))).collect(Collectors.toList());
    }

    @Transactional
    public WikiDTO.AttachmentVO uploadAttachment(Long pageId, MultipartFile file, Long userId) {
        WikiPage p = mustGet(pageId);
        requireMember(p.getProjectId(), userId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文件不能为空");
        }
        String originalName = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());
        // 存储路径: {uploadDir}/wiki/{pageId}/{uuid}-{原始文件名}（绝对路径，避免相对 CWD 漂移）
        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize().resolve("wiki").resolve(String.valueOf(pageId));
        try {
            Files.createDirectories(dir);
            String storedName = UUID.randomUUID().toString().replace("-", "") + "-" + originalName;
            Path target = dir.resolve(storedName);
            file.transferTo(target.toFile());

            WikiAttachment a = new WikiAttachment();
            a.setPageId(pageId);
            a.setFileName(originalName);
            a.setFilePath(Paths.get("wiki").resolve(String.valueOf(pageId)).resolve(storedName).toString());
            a.setFileSize(file.getSize());
            a.setMimeType(file.getContentType());
            a.setUploaderId(userId);
            wikiAttachmentMapper.insert(a);
            return toAttachmentVO(a, null);
        } catch (IOException e) {
            log.error("[Wiki] 附件上传失败 pageId={} name={}", pageId, originalName, e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "附件保存失败: " + e.getMessage());
        }
    }

    /** 附件下载：返回磁盘文件路径（权限校验通过后由 Controller 输出流） */
    public java.nio.file.Path getAttachmentFile(Long attachmentId, Long userId) {
        WikiAttachment a = wikiAttachmentMapper.selectById(attachmentId);
        if (a == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "附件不存在");
        }
        WikiPage p = mustGet(a.getPageId());
        requireMember(p.getProjectId(), userId);
        java.nio.file.Path f = Paths.get(uploadDir).toAbsolutePath().normalize().resolve(a.getFilePath());
        if (!Files.exists(f)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "附件文件不存在");
        }
        return f;
    }

    @Transactional
    public void deleteAttachment(Long attachmentId, Long userId) {
        WikiAttachment a = wikiAttachmentMapper.selectById(attachmentId);
        if (a == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "附件不存在");
        }
        WikiPage p = mustGet(a.getPageId());
        requireMember(p.getProjectId(), userId);
        deleteFileIfExists(a.getFilePath());
        wikiAttachmentMapper.deleteById(attachmentId);
    }

    private void deleteAttachmentFiles(Long pageId) {
        List<WikiAttachment> list = wikiAttachmentMapper.selectList(
                new LambdaQueryWrapper<WikiAttachment>().eq(WikiAttachment::getPageId, pageId));
        for (WikiAttachment a : list) {
            deleteFileIfExists(a.getFilePath());
        }
    }

    private void deleteFileIfExists(String relativePath) {
        try {
            Path f = Paths.get(uploadDir).toAbsolutePath().normalize().resolve(relativePath);
            Files.deleteIfExists(f);
        } catch (Exception e) {
            log.warn("[Wiki] 附件文件删除失败 path={}", relativePath, e);
        }
    }

    // ============ 内部工具 ============

    /** 校验项目存在 + 用户为项目所属团队成员（含系统/团队管理员） */
    private void requireMember(Long projectId, Long userId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "项目不存在");
        }
        User user = userMapper.selectById(userId);
        if (user != null && "ROLE_ADMIN".equals(user.getRole())) return;
        if (!teamService.isTeamMember(project.getTeamId(), userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该项目知识库");
        }
    }

    private void requireProject(Long projectId) {
        if (projectMapper.selectById(projectId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "项目不存在");
        }
    }

    private WikiPage mustGet(Long id) {
        WikiPage p = wikiPageMapper.selectById(id);
        if (p == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "页面不存在");
        }
        return p;
    }

    private int nextSortOrder(Long projectId, Long parentId) {
        List<WikiPage> siblings = wikiPageMapper.selectList(new LambdaQueryWrapper<WikiPage>()
                .eq(WikiPage::getProjectId, projectId)
                .eq(parentId != null, WikiPage::getParentId, parentId)
                .isNull(parentId == null, WikiPage::getParentId));
        return siblings.size();
    }

    private Map<Long, String> loadUserNames(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return Map.of();
        List<User> users = userMapper.selectBatchIds(userIds);
        return users.stream().collect(Collectors.toMap(User::getId, User::getUsername));
    }

    private WikiDTO.PageVO toPageVO(WikiPage p, String authorName, String editorName) {
        WikiDTO.PageVO vo = new WikiDTO.PageVO();
        vo.setId(p.getId());
        vo.setProjectId(p.getProjectId());
        vo.setParentId(p.getParentId());
        vo.setTitle(p.getTitle());
        vo.setContent(p.getContent());
        vo.setVersion(p.getVersion());
        vo.setAuthorId(p.getAuthorId());
        vo.setAuthorName(authorName);
        vo.setEditorId(p.getEditorId());
        vo.setEditorName(editorName);
        vo.setIsPinned(p.getIsPinned());
        vo.setViewCount(p.getViewCount());
        vo.setSortOrder(p.getSortOrder());
        vo.setCreatedAt(p.getCreatedAt() == null ? null : p.getCreatedAt().toString());
        vo.setUpdatedAt(p.getUpdatedAt() == null ? null : p.getUpdatedAt().toString());
        return vo;
    }

    private WikiDTO.TreeVO toTreeVO(WikiPage p, Map<Long, String> nameMap) {
        WikiDTO.TreeVO vo = new WikiDTO.TreeVO();
        vo.setId(p.getId());
        vo.setProjectId(p.getProjectId());
        vo.setParentId(p.getParentId());
        vo.setTitle(p.getTitle());
        vo.setVersion(p.getVersion());
        vo.setAuthorName(nameMap.get(p.getAuthorId()));
        vo.setEditorName(nameMap.get(p.getEditorId()));
        vo.setIsPinned(p.getIsPinned());
        vo.setViewCount(p.getViewCount());
        vo.setSortOrder(p.getSortOrder());
        vo.setUpdatedAt(p.getUpdatedAt() == null ? null : p.getUpdatedAt().toString());
        return vo;
    }

    private WikiDTO.VersionVO toVersionVO(WikiVersion v, String editorName) {
        WikiDTO.VersionVO vo = new WikiDTO.VersionVO();
        vo.setId(v.getId());
        vo.setPageId(v.getPageId());
        vo.setVersion(v.getVersion());
        vo.setTitle(v.getTitle());
        vo.setContent(v.getContent());
        vo.setChangeSummary(v.getChangeSummary());
        vo.setEditorName(editorName);
        vo.setCreatedAt(v.getCreatedAt() == null ? null : v.getCreatedAt().toString());
        return vo;
    }

    private WikiDTO.AttachmentVO toAttachmentVO(WikiAttachment a, String uploaderName) {
        WikiDTO.AttachmentVO vo = new WikiDTO.AttachmentVO();
        vo.setId(a.getId());
        vo.setPageId(a.getPageId());
        vo.setFileName(a.getFileName());
        vo.setFileSize(a.getFileSize());
        vo.setMimeType(a.getMimeType());
        vo.setUploaderName(uploaderName);
        vo.setCreatedAt(a.getCreatedAt() == null ? null : a.getCreatedAt().toString());
        return vo;
    }
}
