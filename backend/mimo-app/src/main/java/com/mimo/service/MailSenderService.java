package com.mimo.service;

import com.mimo.entity.UserEmailAccount;
import com.mimo.util.EmailCryptoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

/**
 * SMTP 邮件发送服务（v2.13.3 / v2.13.6 增加结构化结果）。
 * 复用 UserEmailAccount 表中的 IMAP 凭据（QQ/163/Gmail 同一个授权码同时支持 IMAP 和 SMTP），
 * 若用户未单独配置 smtp_host / smtp_port，则按 imap_host 推断：imap.xxx → smtp.xxx，默认端口 465 SSL。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailSenderService {

    private final EmailCryptoUtil cryptoUtil;

    /**
     * 结构化发送结果（v2.13.6）
     */
    public static class SendResult {
        public final boolean success;
        public final String error;
        public final int durationMs;

        public SendResult(boolean success, String error, int durationMs) {
            this.success = success;
            this.error = error;
            this.durationMs = durationMs;
        }
    }

    /**
     * 用用户绑定的邮箱账户发送邮件（v2.13.6 保留兼容入口）。
     */
    public boolean send(UserEmailAccount account, String toEmail, String subject, String htmlBody) {
        return sendWithLog(account, toEmail, subject, htmlBody).success;
    }

    /**
     * 发送邮件并返回结构化结果（v2.13.6）。
     */
    public SendResult sendWithLog(UserEmailAccount account, String toEmail, String subject, String htmlBody) {
        long start = System.currentTimeMillis();
        if (account == null || toEmail == null || toEmail.isEmpty()) {
            return new SendResult(false, "收件人或账户为空", 0);
        }

        String smtpHost = resolveSmtpHost(account);
        int smtpPort = resolveSmtpPort(account);
        String username = account.getImapUsername();
        String password;
        try {
            password = cryptoUtil.decrypt(account.getImapPasswordEnc());
        } catch (Exception e) {
            log.warn("[MailSender] 解密密码失败 account={} err={}", account.getId(), e.getMessage());
            return new SendResult(false, "密码解密失败", (int) (System.currentTimeMillis() - start));
        }

        Properties props = new Properties();
        props.put("mail.transport.protocol", "smtps");
        props.put("mail.smtps.host", smtpHost);
        props.put("mail.smtps.port", String.valueOf(smtpPort));
        props.put("mail.smtps.ssl.enable", "true");
        props.put("mail.smtps.auth", "true");
        props.put("mail.smtps.starttls.enable", "true");
        // QQ/163 都要求先发 ID 命令报告身份，否则可能返回 Unsafe Login
        props.put("mail.smtps.localhost", "Mimo");

        Session session = Session.getInstance(props);
        session.setDebug(false);

        try {
            MimeMessage msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(account.getEmailAddress(), "Mimo 项目协作"));
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
            msg.setSubject(subject, "UTF-8");
            msg.setContent(htmlBody, "text/html; charset=UTF-8");
            msg.setSentDate(new java.util.Date());

            Transport transport = session.getTransport("smtps");
            try {
                transport.connect(smtpHost, smtpPort, username, password);
                transport.sendMessage(msg, msg.getAllRecipients());
            } finally {
                try { transport.close(); } catch (Exception ignored) {}
            }
            int dur = (int) (System.currentTimeMillis() - start);
            log.info("[MailSender] 发送成功 from={} to={} subject={} dur={}ms", account.getEmailAddress(), toEmail, subject, dur);
            return new SendResult(true, null, dur);
        } catch (Exception e) {
            int dur = (int) (System.currentTimeMillis() - start);
            String err = e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "" : e.getMessage());
            log.warn("[MailSender] 发送失败 from={} to={} type={} err={} dur={}ms",
                    account.getEmailAddress(), toEmail, e.getClass().getSimpleName(), e.getMessage(), dur);
            return new SendResult(false, err, dur);
        }
    }

    /**
     * 推断 SMTP 主机：
     * 1) 显式配置的 smtp_host 优先
     * 2) imap.qq.com → smtp.qq.com；imap.163.com → smtp.163.com；其余 imap.X.Y → smtp.X.Y
     */
    private String resolveSmtpHost(UserEmailAccount acc) {
        if (acc.getSmtpHost() != null && !acc.getSmtpHost().trim().isEmpty()) {
            return acc.getSmtpHost().trim();
        }
        String h = acc.getImapHost();
        if (h == null) return null;
        if (h.startsWith("imap.")) return "smtp." + h.substring(5);
        return h;
    }

    private int resolveSmtpPort(UserEmailAccount acc) {
        if (acc.getSmtpPort() != null && acc.getSmtpPort() > 0) {
            return acc.getSmtpPort();
        }
        return 465;
    }
}
