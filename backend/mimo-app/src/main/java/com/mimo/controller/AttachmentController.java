package com.mimo.controller;

import com.mimo.common.Result;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.AttachmentDTO.AttachmentVO;
import com.mimo.entity.Attachment;
import com.mimo.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@RestController
@RequestMapping("/api/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    @PostMapping("/upload")
    public Result<AttachmentVO> upload(
            @RequestParam Long issueId,
            @RequestParam MultipartFile file,
            Authentication auth) throws IOException {
        Long userId = getLongPrincipal(auth);
        return Result.success(attachmentService.upload(issueId, userId, file));
    }

    @GetMapping("/issue/{issueId}")
    public Result<List<AttachmentVO>> listByIssue(@PathVariable Long issueId) {
        return Result.success(attachmentService.listByIssue(issueId));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        Attachment attachment = attachmentService.getById(id);
        Path path = Paths.get(attachment.getFilePath());
        Resource resource = new FileSystemResource(path);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + attachment.getFileName() + "\"")
                .body(resource);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, Authentication auth) {
        Long userId = getLongPrincipal(auth);
        Attachment attachment = attachmentService.getById(id);
        if (attachment == null) {
            throw new BusinessException(ResultCode.PROJECT_NOT_FOUND, "附件不存在");
        }
        // 仅上传者可删除
        if (!attachment.getUploaderId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能删除自己上传的附件");
        }
        attachmentService.delete(id);
        return Result.successMessage("删除成功");
    }

    private Long getLongPrincipal(Authentication auth) {
        Object p = auth.getPrincipal();
        if (p instanceof Number) return ((Number) p).longValue();
        throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录或登录状态异常");
    }
}
