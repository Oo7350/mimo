package com.mimo.controller;

import com.mimo.common.BusinessException;
import com.mimo.common.Result;
import com.mimo.common.ResultCode;
import com.mimo.dto.EmailAccountDTO;
import com.mimo.dto.MailDTO;
import com.mimo.entity.UserEmailAccount;
import com.mimo.mapper.UserMapper;
import com.mimo.service.EmailAccountService;
import com.mimo.service.MailReceiverService;
import com.mimo.service.MailSenderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/email")
@RequiredArgsConstructor
public class EmailController {

    private final EmailAccountService accountService;
    private final MailReceiverService mailReceiverService;
    private final MailSenderService mailSenderService;
    private final UserMapper userMapper;

    // ==================== 账户管理 ====================

    @GetMapping("/presets")
    public Result<List<EmailAccountDTO.PresetVO>> presets() {
        return Result.success(accountService.listPresets());
    }

    @GetMapping("/accounts")
    public Result<List<EmailAccountDTO.AccountVO>> listAccounts(Authentication auth) {
        return Result.success(accountService.list(getUserId(auth)));
    }

    /**
     * 诊断端点：返回某账户解密后的 IMAP 密码长度和前 2 位，
     * 用来对比绑定时输入的密码和解密后是否一致（仅用于排查 -120 错误）。
     */
    @GetMapping("/accounts/{id}/debug")
    public Result<String> debugPassword(@PathVariable Long id, Authentication auth) {
        com.mimo.entity.UserEmailAccount acc = accountService.mustGetOwned(getUserId(auth), id);
        String plain = accountService.decryptPassword(acc);
        String masked = plain == null ? "null" :
                (plain.length() + " chars, 前2位=" + plain.substring(0, Math.min(2, plain.length()))
                        + " 末2位=" + plain.substring(Math.max(0, plain.length() - 2)));
        return Result.success("account=" + acc.getEmailAddress()
                + " username=" + acc.getImapUsername()
                + " password=" + masked);
    }

    /**
     * 深度诊断端点：实际执行一次 IMAP 连接，把关键异常信息返回给前端。
     * 用于排查"测试通过、但拉取失败"或"-120 持续报错"等疑难问题。
     * 全程 try-catch，确保任何异常都返回结构化字符串（避免返回 500 + 完整堆栈卡死前端）。
     */
    @GetMapping("/accounts/{id}/deepdebug")
    public Result<String> deepDebug(@PathVariable Long id, Authentication auth) {
        StringBuilder sb = new StringBuilder();
        com.mimo.entity.UserEmailAccount acc = null;
        String plain = null;

        try {
            acc = accountService.mustGetOwned(getUserId(auth), id);
        } catch (Exception e) {
            sb.append("[FAIL] 账户查询失败: ").append(e.getMessage());
            return Result.success(truncate(sb.toString(), 1500));
        }

        sb.append("=== 账户信息 ===\n");
        sb.append("email=").append(acc.getEmailAddress()).append("\n");
        sb.append("imap=").append(acc.getImapHost()).append(":").append(acc.getImapPort()).append("\n");
        sb.append("user=").append(acc.getImapUsername()).append("\n");

        try {
            plain = accountService.decryptPassword(acc);
            sb.append("pwd.len=").append(plain == null ? "null" : plain.length());
            if (plain != null && plain.length() >= 4) {
                sb.append(", head=").append(plain.substring(0, 2))
                  .append(", tail=").append(plain.substring(plain.length() - 2));
            }
            sb.append("\n");
        } catch (Exception e) {
            sb.append("[FAIL] 密码解密失败: ").append(e.getMessage()).append("\n");
            return Result.success(truncate(sb.toString(), 1500));
        }

        sb.append("\n=== IMAP 登录测试 ===\n");
        java.util.Properties props = new java.util.Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", acc.getImapHost());
        props.put("mail.imaps.port", String.valueOf(acc.getImapPort()));
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.ssl.checkserveridentity", "false");
        props.put("mail.imaps.ssl.trust", "*");
        props.put("mail.imaps.timeout", "12000");
        props.put("mail.imaps.connectiontimeout", "8000");
        // 强制普通 LOGIN（与 PowerShell 测试一致）
        props.put("mail.imaps.auth.login.disable", "false");
        props.put("mail.imaps.auth.plain.disable", "true");
        props.put("mail.imaps.auth.xoauth2.disable", "true");

        javax.mail.Session session = javax.mail.Session.getInstance(props);

        try (javax.mail.Store store = session.getStore("imaps")) {
            sb.append("connect... ");
            store.connect(acc.getImapHost(), acc.getImapPort(), acc.getImapUsername(), plain);
            sb.append("[OK]\n");
            // 发送 IMAP ID 命令（RFC 2971），QQ/163 等邮箱要求
            try {
                if (store instanceof com.sun.mail.imap.IMAPStore) {
                    java.util.Map<String, String> clientId = new java.util.HashMap<>();
                    clientId.put("name", "Mimo");
                    clientId.put("version", "1.0.0");
                    clientId.put("vendor", "MimoProject");
                    clientId.put("support-email", "noreply@mimo.local");
                    ((com.sun.mail.imap.IMAPStore) store).id(clientId);
                    sb.append("ID: sent\n");
                } else {
                    sb.append("ID: skipped (not IMAPStore)\n");
                }
            } catch (Exception ide) {
                sb.append("ID: failed ").append(ide.getMessage()).append("\n");
            }
            javax.mail.Folder inbox = store.getFolder("INBOX");
            inbox.open(javax.mail.Folder.READ_ONLY);
            sb.append("INBOX.count=").append(inbox.getMessageCount()).append("\n");
            inbox.close(false);
        } catch (Exception e) {
            sb.append("[FAIL]\n");
            sb.append("type=").append(e.getClass().getSimpleName()).append("\n");
            String msg = e.getMessage();
            if (msg != null && msg.length() > 200) msg = msg.substring(0, 200) + "...";
            sb.append("msg=").append(msg).append("\n");
            // 只取堆栈首行（足以定位关键调用点），避免返回过长
            java.io.StringWriter sw = new java.io.StringWriter();
            java.io.PrintWriter pw = new java.io.PrintWriter(sw);
            e.printStackTrace(pw);
            String fullStack = sw.toString();
            String[] lines = fullStack.split("\n");
            StringBuilder shortStack = new StringBuilder();
            int limit = Math.min(3, lines.length);
            for (int i = 0; i < limit; i++) {
                String line = lines[i].length() > 160 ? lines[i].substring(0, 160) + "..." : lines[i];
                shortStack.append(line).append("\n");
            }
            sb.append("stack.top3:\n").append(shortStack);
        }
        return Result.success(truncate(sb.toString(), 1500));
    }

    /** 限制返回字符串总长度，防止前端渲染卡死 */
    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "\n...(已截断)";
    }

    @PostMapping("/accounts")
    public Result<EmailAccountDTO.AccountVO> bind(@RequestBody EmailAccountDTO.BindRequest req,
                                                  Authentication auth) {
        return Result.success(accountService.bind(getUserId(auth), req));
    }

    @DeleteMapping("/accounts/{id}")
    public Result<Void> unbind(@PathVariable Long id, Authentication auth) {
        accountService.unbind(getUserId(auth), id);
        return Result.successMessage("已解绑");
    }

    // ==================== 邮件通知偏好（v2.13.3） ====================

    /**
     * 更新某账户的邮件通知偏好。
     * 请求体字段都是可选，传哪个就改哪个。
     */
    @PutMapping("/accounts/{id}/notification-settings")
    public Result<EmailAccountDTO.AccountVO> updateNotifySettings(
            @PathVariable Long id,
            @RequestBody EmailAccountDTO.NotifySettingsRequest req,
            Authentication auth) {
        return Result.success(accountService.updateNotifySettings(
                getUserId(auth), id,
                req.getEnabled(), req.getTypes(), req.getSmtpHost(), req.getSmtpPort()));
    }

    /**
     * 测试邮件发送：用该账户 SMTP 给本人发一封测试邮件。
     * 不依赖用户偏好开关，用于验证 SMTP 配置是否正确。
     */
    @PostMapping("/accounts/{id}/test-notification")
    public Result<String> testSend(@PathVariable Long id, Authentication auth) {
        Long userId = getUserId(auth);
        UserEmailAccount acc = accountService.mustGetOwned(userId, id);
        com.mimo.entity.User user = userMapper.selectById(userId);
        String toEmail = (user == null || user.getEmail() == null || user.getEmail().trim().isEmpty())
                ? acc.getEmailAddress() : user.getEmail().trim();
        boolean ok = mailSenderService.send(acc, toEmail,
                "[Mimo] 测试邮件",
                "<div style='font-family:sans-serif;color:#333;padding:24px;'>"
                        + "<h2 style='color:#409EFF;'>Mimo 邮件通知测试</h2>"
                        + "<p>这是一封来自 Mimo 项目协作平台的测试邮件。</p>"
                        + "<p>如果你收到了这封邮件，说明 SMTP 配置正确，可以接收以下事件的邮件通知：</p>"
                        + "<ul><li>任务指派</li><li>@提及</li><li>审批结果</li><li>任务评论</li><li>任务状态变更</li></ul>"
                        + "<p style='color:#999;font-size:12px;margin-top:24px;'>此邮件由系统自动发送，请勿回复</p>"
                        + "</div>");
        return ok
                ? Result.successMessage("测试邮件已发送到 " + toEmail + "，请查收（注意检查垃圾邮件箱）")
                : Result.<String>error(ResultCode.INTERNAL_ERROR, "发送失败，请检查 SMTP 配置或授权码（IMAP/SMTP 通常用同一个授权码）");
    }

    /**
     * 测试连接（不保存，仅验证凭据是否有效）。
     * 用于绑定前先确认 IMAP 主机/端口/用户名/授权码是否正确。
     */
    @PostMapping("/accounts/test")
    public Result<String> testConnect(@RequestBody EmailAccountDTO.BindRequest req,
                                      Authentication auth) {
        // 校验必填，避免空请求
        if (req.getImapHost() == null || req.getImapHost().trim().isEmpty()) {
            throw new com.mimo.common.BusinessException(
                    com.mimo.common.ResultCode.BAD_REQUEST, "IMAP 服务器不能为空");
        }
        if (req.getImapUsername() == null || req.getImapUsername().trim().isEmpty()) {
            throw new com.mimo.common.BusinessException(
                    com.mimo.common.ResultCode.BAD_REQUEST, "IMAP 用户名不能为空");
        }
        if (req.getImapPassword() == null || req.getImapPassword().isEmpty()) {
            throw new com.mimo.common.BusinessException(
                    com.mimo.common.ResultCode.BAD_REQUEST, "IMAP 密码不能为空");
        }
        // 临时构造一个 account 对象用于测试
        com.mimo.entity.UserEmailAccount tmp = new com.mimo.entity.UserEmailAccount();
        tmp.setUserId(getUserId(auth));
        tmp.setEmailAddress(req.getEmailAddress() == null ? req.getImapUsername() : req.getEmailAddress());
        tmp.setImapHost(req.getImapHost().trim());
        tmp.setImapPort(req.getImapPort() == null ? 993 : req.getImapPort());
        tmp.setImapUsername(req.getImapUsername().trim());
        // 测试连接直接传明文密码，不走加解密
        mailReceiverService.testConnection(tmp, req.getImapPassword());
        return Result.successMessage("连接成功：IMAP 凭据有效");
    }

    // ==================== 收件箱 ====================

    @GetMapping("/inbox")
    public Result<MailDTO.InboxVO> inbox(@RequestParam(required = false) Long accountId,
                                         @RequestParam(defaultValue = "20") int limit,
                                         Authentication auth) {
        Long userId = getUserId(auth);
        UserEmailAccount acc = accountId == null
                ? accountService.getDefault(userId)
                : accountService.mustGetOwned(userId, accountId);
        if (acc == null) {
            // 无账户，返回空
            MailDTO.InboxVO empty = new MailDTO.InboxVO();
            empty.setMessages(new ArrayList<>());
            return Result.success(empty);
        }
        List<MailReceiverService.MailSummary> summaries = mailReceiverService.listInbox(acc, limit);
        accountService.updateLastSynced(acc.getId());

        MailDTO.InboxVO vo = new MailDTO.InboxVO();
        EmailAccountDTO.AccountVO accVO = new EmailAccountDTO.AccountVO();
        accVO.setId(acc.getId());
        accVO.setEmailAddress(acc.getEmailAddress());
        accVO.setImapHost(acc.getImapHost());
        accVO.setImapPort(acc.getImapPort());
        accVO.setImapUsername(acc.getImapUsername());
        accVO.setIsDefault(acc.getIsDefault());
        accVO.setLastSyncedAt(acc.getLastSyncedAt() == null ? null : acc.getLastSyncedAt().toString());
        accVO.setCreatedAt(acc.getCreatedAt() == null ? null : acc.getCreatedAt().toString());
        vo.setAccount(accVO);

        // MailReceiverService.MailSummary → MailDTO.MailSummaryVO
        List<MailDTO.MailSummaryVO> msgs = new ArrayList<>(summaries.size());
        for (MailReceiverService.MailSummary s : summaries) {
            MailDTO.MailSummaryVO m = new MailDTO.MailSummaryVO();
            m.setMessageSeq(s.messageSeq);
            m.setSubject(s.subject);
            m.setFrom(s.from);
            m.setFromPersonal(s.fromPersonal);
            m.setSentDate(s.sentDate);
            m.setSnippet(s.snippet);
            m.setSeen(s.seen);
            m.setSize(s.size);
            msgs.add(m);
        }
        vo.setMessages(msgs);
        return Result.success(vo);
    }

    @GetMapping("/accounts/{accountId}/messages/{seq}")
    public Result<MailDTO.MailDetailVO> detail(@PathVariable Long accountId,
                                                @PathVariable int seq,
                                                Authentication auth) {
        Long userId = getUserId(auth);
        UserEmailAccount acc = accountService.mustGetOwned(userId, accountId);
        MailReceiverService.MailDetail d = mailReceiverService.fetchDetail(acc, seq);
        MailDTO.MailDetailVO vo = new MailDTO.MailDetailVO();
        vo.setSubject(d.subject);
        vo.setFrom(d.from);
        vo.setFromPersonal(d.fromPersonal);
        vo.setTo(d.to);
        vo.setSentDate(d.sentDate);
        vo.setTextBody(d.textBody);
        vo.setHtmlBody(d.htmlBody);
        return Result.success(vo);
    }

    private Long getUserId(Authentication auth) {
        Object p = auth.getPrincipal();
        if (p instanceof Number) return ((Number) p).longValue();
        throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录或登录状态异常");
    }
}
