package com.mimo.service;

import com.mimo.entity.EmailSendLog;
import com.mimo.entity.Notification;
import com.mimo.entity.User;
import com.mimo.entity.UserEmailAccount;
import com.mimo.mapper.EmailSendLogMapper;
import com.mimo.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 邮件通知派发服务（v2.13.3 / v2.13.6 落库日志 + 模板化）。
 *
 * 在 NotificationService.create 完成后调用本服务，按用户偏好决定是否额外发一封邮件。
 * 所有逻辑异步执行，业务流程不阻塞、失败降级，不影响主流程。
 *
 * 触发事件类型约定（与 user_email_accounts.notify_types 取值一致）：
 * - assignment：任务指派 / 重新指派
 * - mention：@提及
 * - approval：审批通知（提交 / 通过 / 拒绝）
 * - comment：评论回复
 * - issue_status：任务状态变更
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailNotificationService {

    private final EmailAccountService emailAccountService;
    private final MailSenderService mailSenderService;
    private final EmailTemplateService emailTemplateService;
    private final EmailSendLogMapper emailSendLogMapper;
    private final UserMapper userMapper;

    /**
     * 异步派发邮件通知。
     * @param recipientUserId 收件人用户 ID
     * @param type            事件类型（assignment / mention / approval / comment / issue_status）
     * @param n               已落库的 Notification（含 title/content/relatedId 等）
     */
    @Async
    public void dispatch(Long recipientUserId, String type, Notification n) {
        if (recipientUserId == null || type == null || n == null) {
            return;
        }

        // 1. 取收件人邮箱地址（取 User.email）
        User user = userMapper.selectById(recipientUserId);
        if (user == null || user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            log.debug("[EmailNotify] 用户 {} 无 email 字段，跳过", recipientUserId);
            return;
        }
        String toEmail = user.getEmail().trim();

        // 2. 取该用户所有"启用了该 type"的邮箱账户
        List<UserEmailAccount> accounts = emailAccountService.listNotifyEnabled(recipientUserId, type);
        if (accounts.isEmpty()) {
            log.debug("[EmailNotify] 用户 {} 未开启 type={} 的邮件通知", recipientUserId, type);
            return;
        }

        // 3. 组 HTML（v2.13.6 模板化）
        String subject = "[Mimo] " + n.getTitle();
        String actionUrl = buildActionUrl(n, type);
        String html = emailTemplateService.render(n, type, actionUrl);

        // 4. 依次发送并落库（避免单封失败拖垮其他账户）
        int success = 0;
        for (UserEmailAccount acc : accounts) {
            MailSenderService.SendResult r = mailSenderService.sendWithLog(acc, toEmail, subject, html);
            writeLog(recipientUserId, toEmail, acc, subject, type, n.getId(), r);
            if (r.success) success++;
        }
        log.info("[EmailNotify] dispatch user={} type={} accounts={} success={}",
                recipientUserId, type, accounts.size(), success);
    }

    private String buildActionUrl(Notification n, String type) {
        if (n == null || n.getRelatedId() == null) return null;
        // 约定：relatedId 指向 issue / approval / comment 等
        switch (type == null ? "" : type) {
            case "approval": return "/admin/approvals?id=" + n.getRelatedId();
            case "comment":
            case "assignment":
            case "mention":
            case "issue_status":
            default: return "/issues/" + n.getRelatedId();
        }
    }

    private void writeLog(Long userId, String toEmail, UserEmailAccount acc,
                          String subject, String type, Long relatedId,
                          MailSenderService.SendResult r) {
        try {
            EmailSendLog entry = new EmailSendLog();
            entry.setUserId(userId);
            entry.setToEmail(toEmail);
            entry.setAccountId(acc == null ? null : acc.getId());
            entry.setFromEmail(acc == null ? null : acc.getEmailAddress());
            entry.setSubject(subject);
            entry.setNotifyType(type);
            entry.setRelatedId(relatedId);
            entry.setSuccess(r.success ? 1 : 0);
            entry.setError(r.success ? null : (r.error == null ? null : truncate(r.error, 500)));
            entry.setDurationMs(r.durationMs);
            entry.setCreatedAt(LocalDateTime.now());
            emailSendLogMapper.insert(entry);
        } catch (Exception ex) {
            log.warn("[EmailNotify] 日志落库失败: {}", ex.getMessage());
        }
    }

    private String truncate(String s, int max) {
        return s == null ? null : (s.length() <= max ? s : s.substring(0, max));
    }
}
