package com.mimo.dto;

import lombok.Data;

import java.util.List;

/**
 * 团队在线情况（KNOWN-013 的真实在线人数）。
 *
 * 只有 onlineCount 是"现在真连着 WebSocket 的人数"，totalMembers 是成员总数——
 * 两个数字分开给，前端才能诚实地写成「3 人在线 / 共 8 位成员」，
 * 而不是拿成员数冒充在线数。
 */
@Data
public class TeamPresenceVO {

    private Long teamId;

    /** 当前在线人数（按 userId 去重，多标签页只算一个人） */
    private long onlineCount;

    /** 团队总人数 */
    private long totalMembers;

    /** 在线成员 id，方便前端给头像点绿点 */
    private List<Long> onlineUserIds;
}
