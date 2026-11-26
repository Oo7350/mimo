package com.mimo.dto;

import lombok.Data;

import java.util.List;

/**
 * 团队在线情况（KNOWN-013 的真实在线人数 / KNOWN-002 的口径来源）。
 *
 * 只有 onlineCount 是"现在真连着 WebSocket 的人数"，totalMembers 是成员总数——
 * 两个数字分开给，前端才能诚实地写成「3 人在线 / 共 8 位成员」，
 * 而不是拿成员数冒充在线数。
 *
 * v2.21.0 起再补两个字段，回答"这个在线数字跨实例算不算得准"：
 * 多实例部署时如果没接 Redis，每台机器只知道自己那半边的连接，
 * 用户看到"1 人在线"会以为别人都下线了。与其让人猜，不如直接写出来。
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

    /** 在线状态的存储：`REDIS`（跨实例共享）/ `MEMORY`（只算本实例） */
    private String presenceStore;

    /** 这个在线人数是否跨实例准确 */
    private boolean presenceShared;

    /** 一句话解释，直接显示给用户（不要在界面上留"准不准不知道"的数字） */
    private String presenceNote;
}
