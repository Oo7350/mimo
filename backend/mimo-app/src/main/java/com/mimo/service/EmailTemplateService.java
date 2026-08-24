package com.mimo.service;

import com.mimo.entity.Notification;
import org.springframework.stereotype.Service;

/**
 * 邮件正文 HTML 模板渲染（v2.13.6）
 * <p>
 * 使用 String.format + escape 实现，不引入 Thymeleaf 等额外依赖。
 * 模板含：品牌色 / 标题 / 类型徽章 / 内容 / 操作链接 / 页脚。
 */
@Service
public class EmailTemplateService {

    /**
     * 渲染邮件正文 HTML。
     *
     * @param n           通知对象（title / content）
     * @param type        事件类型：assignment/mention/comment/approval/issue_status
     * @param actionUrl   可选，跳转链接（如 /issues/123），null 不显示按钮
     */
    public String render(Notification n, String type, String actionUrl) {
        String typeLabel = labelOf(type);
        String contentHtml = escape(n.getContent() == null ? "" : n.getContent()).replace("\n", "<br>");
        String buttonHtml = (actionUrl == null || actionUrl.isEmpty())
                ? ""
                : String.format(
                        "<a href=\"%s\" style=\"display:inline-block;margin-top:16px;padding:10px 22px;background:#409EFF;color:#fff;text-decoration:none;border-radius:4px;font-weight:600;\">查看详情</a>",
                        escape(actionUrl));

        return "<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\"></head><body>"
                + "<div style=\"font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;color:#333;max-width:640px;margin:0 auto;padding:24px 8px;\">"
                + "<div style=\"display:flex;align-items:center;gap:8px;margin-bottom:20px;\">"
                + "<span style=\"display:inline-block;width:28px;height:28px;line-height:28px;text-align:center;background:#409EFF;color:#fff;border-radius:6px;font-weight:700;font-size:16px;\">M</span>"
                + "<span style=\"font-size:18px;font-weight:600;color:#303133;\">Mimo 项目协作</span>"
                + "</div>"
                + String.format("<h2 style=\"margin:0 0 12px;color:#303133;font-size:20px;line-height:1.4;\">%s</h2>", escape(n.getTitle()))
                + String.format("<div style=\"margin-bottom:16px;\"><span style=\"display:inline-block;padding:2px 10px;background:#ecf5ff;color:#409EFF;border-radius:10px;font-size:12px;\">%s</span>"
                                + "<span style=\"margin-left:8px;color:#909399;font-size:12px;\">%s</span></div>",
                        escape(typeLabel), nowText())
                + "<div style=\"background:#f6f8fa;border-radius:6px;padding:14px 18px;line-height:1.7;color:#303133;font-size:14px;\">"
                + contentHtml
                + "</div>"
                + buttonHtml
                + "<hr style=\"border:0;border-top:1px solid #ebeef5;margin:24px 0;\">"
                + "<div style=\"color:#909399;font-size:12px;line-height:1.6;\">"
                + "此邮件由 Mimo 平台自动发送，请勿直接回复。<br>"
                + "如需关闭邮件通知，请到「邮箱账户 → 通知偏好」中调整设置。"
                + "</div>"
                + "</div></body></html>";
    }

    private String labelOf(String type) {
        if (type == null) return "通知";
        switch (type) {
            case "assignment": return "任务指派";
            case "mention": return "@提及";
            case "approval": return "审批通知";
            case "comment": return "评论回复";
            case "issue_status": return "任务状态变更";
            default: return "通知";
        }
    }

    private String nowText() {
        return java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
