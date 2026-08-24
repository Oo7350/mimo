package com.mimo.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.AnnouncementDTO;
import com.mimo.entity.Announcement;
import com.mimo.entity.User;
import com.mimo.mapper.AnnouncementMapper;
import com.mimo.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final AnnouncementMapper announcementMapper;
    private final UserMapper userMapper;

    public AnnouncementDTO.VO create(AnnouncementDTO.CreateRequest req, Long createdBy) {
        if (req.getTitle() == null || req.getTitle().trim().isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "公告标题不能为空");
        }
        if (req.getContent() == null || req.getContent().trim().isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "公告内容不能为空");
        }
        Announcement a = new Announcement();
        a.setTitle(req.getTitle().trim());
        a.setContent(req.getContent());
        a.setPinned(req.getPinned() != null && req.getPinned() == 1 ? 1 : 0);
        a.setCreatedBy(createdBy);
        announcementMapper.insert(a);
        return toVO(a);
    }

    public AnnouncementDTO.VO update(Long id, AnnouncementDTO.UpdateRequest req, Long operatorId) {
        Announcement a = mustGet(id);
        if (req.getTitle() != null && !req.getTitle().trim().isEmpty()) {
            a.setTitle(req.getTitle().trim());
        }
        if (req.getContent() != null && !req.getContent().trim().isEmpty()) {
            a.setContent(req.getContent());
        }
        if (req.getPinned() != null) {
            a.setPinned(req.getPinned() == 1 ? 1 : 0);
        }
        announcementMapper.updateById(a);
        return toVO(a);
    }

    public void delete(Long id) {
        Announcement a = mustGet(id);
        announcementMapper.deleteById(a.getId());
    }

    public List<AnnouncementDTO.VO> list(int page, int size) {
        Page<Announcement> p = new Page<>(page, size);
        QueryWrapper<Announcement> qw = new QueryWrapper<>();
        qw.orderByDesc("pinned").orderByDesc("created_at");
        Page<Announcement> result = announcementMapper.selectPage(p, qw);

        // 一次性查发布人用户名
        Set<Long> userIds = result.getRecords().stream()
                .map(Announcement::getCreatedBy)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> nameMap = Map.of();
        if (!userIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(userIds);
            nameMap = users.stream().collect(Collectors.toMap(User::getId, User::getUsername));
        }
        final Map<Long, String> finalNameMap = nameMap;
        return result.getRecords().stream()
                .map(a -> toVO(a, finalNameMap.get(a.getCreatedBy())))
                .collect(Collectors.toList());
    }

    public AnnouncementDTO.VO get(Long id) {
        Announcement a = mustGet(id);
        return toVO(a);
    }

    private Announcement mustGet(Long id) {
        Announcement a = announcementMapper.selectById(id);
        if (a == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "公告不存在");
        }
        return a;
    }

    private AnnouncementDTO.VO toVO(Announcement a) {
        return toVO(a, null);
    }

    private AnnouncementDTO.VO toVO(Announcement a, String createdByName) {
        AnnouncementDTO.VO vo = new AnnouncementDTO.VO();
        vo.setId(a.getId());
        vo.setTitle(a.getTitle());
        vo.setContent(a.getContent());
        vo.setPinned(a.getPinned());
        vo.setCreatedBy(a.getCreatedBy());
        vo.setCreatedByName(createdByName);
        vo.setCreatedAt(a.getCreatedAt() == null ? null : a.getCreatedAt().toString());
        vo.setUpdatedAt(a.getUpdatedAt() == null ? null : a.getUpdatedAt().toString());
        return vo;
    }
}
