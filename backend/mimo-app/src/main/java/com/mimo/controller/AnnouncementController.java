package com.mimo.controller;

import com.mimo.common.BusinessException;
import com.mimo.common.Result;
import com.mimo.common.ResultCode;
import com.mimo.dto.AnnouncementDTO;
import com.mimo.service.AnnouncementService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;
    private final com.mimo.service.AccessControlService accessControlService;

    @GetMapping
    public Result<List<AnnouncementDTO.VO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(announcementService.list(page, size));
    }

    @GetMapping("/{id}")
    public Result<AnnouncementDTO.VO> get(@PathVariable Long id) {
        return Result.success(announcementService.get(id));
    }

    @PostMapping
    public Result<AnnouncementDTO.VO> create(@RequestBody AnnouncementDTO.CreateRequest req,
                                             Authentication auth) {
        requireAdmin(auth);
        Long userId = getUserId(auth);
        return Result.success(announcementService.create(req, userId));
    }

    @PutMapping("/{id}")
    public Result<AnnouncementDTO.VO> update(@PathVariable Long id,
                                             @RequestBody AnnouncementDTO.UpdateRequest req,
                                             Authentication auth) {
        requireAdmin(auth);
        return Result.success(announcementService.update(id, req, getUserId(auth)));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, Authentication auth) {
        requireAdmin(auth);
        announcementService.delete(id);
        return Result.successMessage("公告已删除");
    }

    /**
     * 公告的发布/编辑/删除只允许系统管理员。
     *
     * <p>以前这里是读 JWT 里的 {@code ROLE_ADMIN} 授权项（和别处查 users.role 是两套口径），
     * 现在统一走 {@link com.mimo.service.AccessControlService}，与 RBAC 完全一致。
     */
    private void requireAdmin(Authentication auth) {
        String denied = "无权操作，仅管理员可发布/编辑公告";
        if (auth == null || !(auth.getPrincipal() instanceof Number)) {
            throw new BusinessException(ResultCode.FORBIDDEN, denied);
        }
        Long userId = ((Number) auth.getPrincipal()).longValue();
        if (!accessControlService.isSystemAdmin(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, denied);
        }
    }

    private Long getUserId(Authentication auth) {
        Object p = auth.getPrincipal();
        if (p instanceof Number) return ((Number) p).longValue();
        throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录或登录状态异常");
    }
}
