package com.mimo.controller;

import com.mimo.common.BusinessException;
import com.mimo.common.Result;
import com.mimo.common.ResultCode;
import com.mimo.dto.WikiDTO;
import com.mimo.service.WikiService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Wiki 文档系统：树形目录 / 页面 CRUD（版本历史）/ 全文检索 / 附件
 */
@RestController
@RequestMapping("/api/wiki")
@RequiredArgsConstructor
public class WikiController {

    private final WikiService wikiService;

    /** 目录树（轻量，不含正文） */
    @GetMapping("/tree")
    public Result<List<WikiDTO.TreeVO>> tree(@RequestParam Long projectId, Authentication auth) {
        return Result.success(wikiService.getTree(projectId));
    }

    /** 页面详情（含正文，浏览量 +1） */
    @GetMapping("/pages/{id}")
    public Result<WikiDTO.PageVO> get(@PathVariable Long id, Authentication auth) {
        return Result.success(wikiService.getPage(id, getUserId(auth)));
    }

    @PostMapping("/pages")
    public Result<WikiDTO.PageVO> create(@RequestBody WikiDTO.CreateRequest req, Authentication auth) {
        return Result.success(wikiService.create(req, getUserId(auth)));
    }

    @PutMapping("/pages/{id}")
    public Result<WikiDTO.PageVO> update(@PathVariable Long id, @RequestBody WikiDTO.UpdateRequest req,
                                         Authentication auth) {
        return Result.success(wikiService.update(id, req, getUserId(auth)));
    }

    @DeleteMapping("/pages/{id}")
    public Result<Void> delete(@PathVariable Long id, Authentication auth) {
        wikiService.delete(id, getUserId(auth));
        return Result.successMessage("页面已删除");
    }

    /** 全文检索（标题 + 正文 LIKE） */
    @GetMapping("/search")
    public Result<List<WikiDTO.PageVO>> search(@RequestParam Long projectId, @RequestParam String q,
                                               Authentication auth) {
        return Result.success(wikiService.search(projectId, q, getUserId(auth)));
    }

    // ============ 版本历史 ============

    @GetMapping("/pages/{id}/versions")
    public Result<List<WikiDTO.VersionVO>> versions(@PathVariable Long id, Authentication auth) {
        return Result.success(wikiService.listVersions(id, getUserId(auth)));
    }

    @GetMapping("/pages/{id}/versions/{version}")
    public Result<WikiDTO.VersionVO> version(@PathVariable Long id, @PathVariable Integer version,
                                             Authentication auth) {
        return Result.success(wikiService.getVersion(id, version, getUserId(auth)));
    }

    @PostMapping("/pages/{id}/versions/{version}/restore")
    public Result<WikiDTO.PageVO> restore(@PathVariable Long id, @PathVariable Integer version,
                                          Authentication auth) {
        return Result.success(wikiService.restoreVersion(id, version, getUserId(auth)));
    }

    // ============ 附件 ============

    @GetMapping("/pages/{id}/attachments")
    public Result<List<WikiDTO.AttachmentVO>> attachments(@PathVariable Long id, Authentication auth) {
        return Result.success(wikiService.listAttachments(id, getUserId(auth)));
    }

    @PostMapping("/pages/{id}/attachments")
    public Result<WikiDTO.AttachmentVO> upload(@PathVariable Long id, @RequestParam("file") MultipartFile file,
                                               Authentication auth) {
        return Result.success(wikiService.uploadAttachment(id, file, getUserId(auth)));
    }

    @GetMapping("/attachments/{attachmentId}/download")
    public org.springframework.http.ResponseEntity<byte[]> download(@PathVariable Long attachmentId,
                                                                    Authentication auth) {
        java.nio.file.Path f = wikiService.getAttachmentFile(attachmentId, getUserId(auth));
        try {
            byte[] data = java.nio.file.Files.readAllBytes(f);
            String fileName = f.getFileName().toString();
            return org.springframework.http.ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=" + fileName)
                    .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                    .body(data);
        } catch (java.io.IOException e) {
            throw new com.mimo.common.BusinessException(com.mimo.common.ResultCode.INTERNAL_ERROR, "附件读取失败");
        }
    }

    @DeleteMapping("/attachments/{attachmentId}")
    public Result<Void> deleteAttachment(@PathVariable Long attachmentId, Authentication auth) {
        wikiService.deleteAttachment(attachmentId, getUserId(auth));
        return Result.successMessage("附件已删除");
    }

    private Long getUserId(Authentication auth) {
        Object p = auth.getPrincipal();
        if (p instanceof Number) return ((Number) p).longValue();
        throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录或登录状态异常");
    }
}
