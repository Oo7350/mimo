package com.mimo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.AttachmentDTO.AttachmentVO;
import com.mimo.entity.Attachment;
import com.mimo.entity.User;
import com.mimo.mapper.AttachmentMapper;
import com.mimo.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttachmentService {

    private final AttachmentMapper attachmentMapper;
    private final UserMapper userMapper;

    @Value("${mimo.upload.dir:./uploads}")
    private String uploadDir;

    public AttachmentVO upload(Long issueId, Long uploaderId, MultipartFile file) throws IOException {
        Path dir = Paths.get(uploadDir);
        if (!Files.exists(dir)) Files.createDirectories(dir);

        String ext = "";
        String originalName = file.getOriginalFilename();
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf("."));
        }
        String storedName = UUID.randomUUID().toString() + ext;
        Path target = dir.resolve(storedName);
        file.transferTo(target);

        Attachment attachment = new Attachment();
        attachment.setIssueId(issueId);
        attachment.setFileName(originalName != null ? originalName : "unknown");
        attachment.setFilePath(target.toString());
        attachment.setFileSize(file.getSize());
        attachment.setFileType(file.getContentType());
        attachment.setUploaderId(uploaderId);
        attachmentMapper.insert(attachment);

        return toVO(attachment);
    }

    public List<AttachmentVO> listByIssue(Long issueId) {
        return attachmentMapper.selectList(
                new LambdaQueryWrapper<Attachment>().eq(Attachment::getIssueId, issueId)
                        .orderByDesc(Attachment::getCreatedAt))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    public Attachment getById(Long id) {
        Attachment attachment = attachmentMapper.selectById(id);
        if (attachment == null) throw new BusinessException(ResultCode.NOT_FOUND);
        return attachment;
    }

    public void delete(Long id) {
        Attachment attachment = attachmentMapper.selectById(id);
        if (attachment == null) throw new BusinessException(ResultCode.NOT_FOUND);
        try {
            Files.deleteIfExists(Paths.get(attachment.getFilePath()));
        } catch (IOException ignored) {}
        attachmentMapper.deleteById(id);
    }

    private AttachmentVO toVO(Attachment a) {
        User u = userMapper.selectById(a.getUploaderId());
        AttachmentVO vo = new AttachmentVO();
        vo.setId(a.getId());
        vo.setIssueId(a.getIssueId());
        vo.setFileName(a.getFileName());
        vo.setFileSize(a.getFileSize());
        vo.setFileType(a.getFileType());
        vo.setUploaderName(u != null ? u.getUsername() : "");
        vo.setCreatedAt(a.getCreatedAt());
        return vo;
    }
}
