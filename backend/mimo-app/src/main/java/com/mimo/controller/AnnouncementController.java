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

    private void requireAdmin(Authentication auth) {
        if (auth == null || auth.getAuthorities() == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作，仅管理员可发布/编辑公告");
        }
        boolean isAdmin = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> "ROLE_ADMIN".equals(a));
        if (!isAdmin) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作，仅管理员可发布/编辑公告");
        }
    }

    private Long getUserId(Authentication auth) {
        Object p = auth.getPrincipal();
        if (p instanceof Number) return ((Number) p).longValue();
        throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录或登录状态异常");
    }
}
