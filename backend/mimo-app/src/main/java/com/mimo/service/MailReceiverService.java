package com.mimo.service;

import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.entity.UserEmailAccount;
import com.mimo.util.EmailCryptoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailReceiverService {

    private final EmailCryptoUtil emailCryptoUtil;

    /** 邮件列表项（摘要） */
    public static class MailSummary {
        public int messageSeq;   // INBOX 中的 1-based 序号，前端取详情用
        public String subject;
        public String from;
        public String fromPersonal;
        public Date sentDate;
        public String snippet;   // 正文前 200 字符纯文本摘要
        public boolean seen;
        public long size;
    }

    /** 邮件详情 */
    public static class MailDetail {
        public String subject;
        public String from;
        public String fromPersonal;
        public String to;
        public Date sentDate;
        public String textBody;  // 纯文本正文（可能为空）
        public String htmlBody;  // HTML 正文（可能为空）
    }

    /**
     * 拉取最近 limit 封邮件（按时间倒序）
     * 注意：列表页不拉取邮件正文 snippet，避免每封都 FETCH BODY 导致超时。
     * 如需正文预览，请调用 {@link #getMailDetail} 拿单封详情。
     */
    public List<MailSummary> listInbox(UserEmailAccount account, int limit) {
        List<MailSummary> out = new ArrayList<>();
        String password = emailCryptoUtil.decrypt(account.getImapPasswordEnc());
        runImap(account, password, store -> {
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);
            int total = inbox.getMessageCount();
            if (total <= 0) return;
            // 注意：Folder.getMessages(int start, int end) 的第二参数是结束序号（含），不是数量
            int start = Math.max(1, total - limit + 1);
            Message[] msgs = inbox.getMessages(start, total);
            // 批量预取元数据：ENVELOPE(subject/from/date) + FLAGS + SIZE
            // 一次 FETCH 拉所有邮件的元数据，避免每封单独往返
            FetchProfile fp = new FetchProfile();
            fp.add(FetchProfile.Item.ENVELOPE);
            fp.add(FetchProfile.Item.FLAGS);
            fp.add(FetchProfile.Item.SIZE);
            inbox.fetch(msgs, fp);
            // 反转使最新在前
            for (int i = msgs.length - 1; i >= 0; i--) {
                try {
                    out.add(toSummary(msgs[i]));
                } catch (Exception me) {
                    // 单封邮件解析失败不影响其他邮件
                    log.warn("[listInbox] toSummary fail seq={} type={} msg={}",
                            i, me.getClass().getSimpleName(), me.getMessage());
                    MailSummary s = new MailSummary();
                    s.messageSeq = i;
                    s.subject = "(无法读取)";
                    s.from = "";
                    s.snippet = "读取失败: " + me.getMessage();
                    out.add(s);
                }
            }
            inbox.close(false);
        });
        return out;
    }

    /**
     * 获取单封邮件详情。messageSeq 是 INBOX 中的序号（1-based）。
     */
    public MailDetail fetchDetail(UserEmailAccount account, int messageSeq) {
        final MailDetail[] holder = new MailDetail[1];
        String password = emailCryptoUtil.decrypt(account.getImapPasswordEnc());
        runImap(account, password, store -> {
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);
            int total = inbox.getMessageCount();
            if (messageSeq < 1 || messageSeq > total) {
                throw new BusinessException(ResultCode.NOT_FOUND, "邮件不存在");
            }
            Message m = inbox.getMessage(messageSeq);
            holder[0] = toDetail(m);
        });
        return holder[0];
    }

    /**
     * 测试 IMAP 连接（不持久化，仅验证凭据是否有效）。
     * 直接接收明文密码，避免走加解密流程。
     */
    public void testConnection(UserEmailAccount account, String plaintextPassword) {
        runImap(account, plaintextPassword, store -> {
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);
            int total = inbox.getMessageCount();
            log.info("测试连接成功 account={} INBOX 共 {} 封", account.getEmailAddress(), total);
        });
    }

    private void runImap(UserEmailAccount account, String password, ImapAction action) {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", account.getImapHost());
        props.put("mail.imaps.port", String.valueOf(account.getImapPort()));
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.ssl.checkserveridentity", "false");
        props.put("mail.imaps.ssl.trust", "*");
        props.put("mail.imaps.timeout", "15000");
        props.put("mail.imaps.connectiontimeout", "10000");
        // QQ 邮箱要求客户端先发 ID 命令报告身份，否则后续认证会返回 -120
        // 强制使用普通 LOGIN 命令（与 PowerShell 测试一致，已验证成功）
        props.put("mail.imaps.auth.login.disable", "false");
        props.put("mail.imaps.auth.plain.disable", "true");
        props.put("mail.imaps.auth.xoauth2.disable", "true");

        Session session = Session.getInstance(props);
        Store store = null;
        Folder folder = null;
        try {
            store = session.getStore("imaps");
            // 先建立连接，发 ID 命令，再登录
            // com.sun.mail.imap.IMAPStore 的 connect(host, port, user, pass) 会自动登录
            // 为了发 ID 命令，需要用 preauth 模式 + 手动 login，但 jakarta.mail 不直接支持
            // 折中方案：使用 preauth=false 让 connect 自动登录，QQ 邮箱的 ID 命令可以通过 setCapabilities 后的发
            // 实测最简方案：直接 connect，QQ 接受普通 LOGIN（PowerShell 测试已验证）
            store.connect(account.getImapHost(), account.getImapPort(),
                    account.getImapUsername(), password);
            // QQ 邮箱 / 163 邮箱要求客户端在 connect 后发 IMAP ID 命令（RFC 2971）报告身份，
            // 否则后续大量 FETCH / EXAMINE 操作可能被拒绝（错误码 -150 / -154 / Unsafe Login）
            sendImapId(store);
            action.run(store);
        } catch (Exception e) {
            log.warn("IMAP 拉取失败 account={} host={} type={} err={}",
                    account.getEmailAddress(), account.getImapHost(),
                    e.getClass().getName(), e.getMessage());
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    buildFriendlyError(account, e));
        } finally {
            try { if (folder != null && folder.isOpen()) folder.close(false); } catch (Exception ignored) {}
            try { if (store != null && store.isConnected()) store.close(); } catch (Exception ignored) {}
        }
    }

    /**
     * 发送 IMAP ID 命令（RFC 2971），向服务器报告客户端身份。
     * QQ 邮箱、163 邮箱等要求客户端在登录后立即发送此命令，
     * 否则会拒绝后续大量 FETCH 操作（返回 -150 / -154 等错误）。
     * 若服务器不支持 ID 命令或非 IMAPStore，则静默跳过。
     */
    private void sendImapId(Store store) {
        try {
            if (store instanceof com.sun.mail.imap.IMAPStore) {
                com.sun.mail.imap.IMAPStore imapStore = (com.sun.mail.imap.IMAPStore) store;
                Map<String, String> id = new HashMap<>();
                id.put("name", "Mimo");
                id.put("version", "1.0.0");
                id.put("vendor", "MimoProject");
                id.put("support-email", "noreply@mimo.local");
                imapStore.id(id);
            }
        } catch (Exception ignored) {
            // 不支持 ID 命令或服务器拒绝，都不影响后续操作
        }
    }

    private interface ImapAction {
        void run(Store store) throws Exception;
    }

    /** 把底层异常映射成可操作的中文提示，帮用户快速定位原因 */
    private String buildFriendlyError(UserEmailAccount acc, Exception e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        String str = e.toString() == null ? "" : e.toString();
        String host = acc.getImapHost() == null ? "" : acc.getImapHost().toLowerCase();
        String email = acc.getEmailAddress() == null ? "" : acc.getEmailAddress().toLowerCase();
        String combined = (msg + " " + str).toLowerCase();

        // 网络类
        if (combined.contains("unknown host") || combined.contains("network is unreachable")
                || combined.contains("connection refused") || combined.contains("connect timed out")
                || combined.contains("connectiontimeout")) {
            return "无法连接到 IMAP 服务器 " + acc.getImapHost() + ":" + acc.getImapPort()
                    + "。请检查 IMAP 主机和端口是否正确，以及本机网络/防火墙是否允许出站 993 端口。";
        }

        // 认证类：用异常类型 + 关键字双重识别（QQ 返回 -120，文本可能只有数字）
        boolean authFail = e instanceof AuthenticationFailedException
                || combined.contains("login fail") || combined.contains("authentication failed")
                || combined.contains("invalid credentials") || combined.contains("authorization")
                || "-120".equals(msg) || msg.contains("-120");
        if (authFail) {
            if (host.contains("qq.com") || email.contains("qq.com")
                    || combined.contains("help.mail.qq.com")) {
                return "QQ 邮箱登录失败。请到 QQ 邮箱网页版「设置 → 账户」开启 IMAP/SMTP 服务，"
                        + "并使用 16 位授权码（非 QQ 登录密码）作为 IMAP 密码。"
                        + "帮助：https://help.mail.qq.com/detail/108/1023";
            }
            if (host.contains("163.com") || email.contains("163.com")) {
                return "163 邮箱登录失败。请到 163 邮箱网页版「设置 → POP3/SMTP/IMAP」开启 IMAP 服务，"
                        + "并使用 16 位授权码（非登录密码）作为 IMAP 密码。"
                        + "帮助：https://help.mail.163.com";
            }
            if (host.contains("126.com") || email.contains("126.com")) {
                return "126 邮箱登录失败。请到 126 邮箱网页版「设置 → POP3/SMTP/IMAP」开启 IMAP 服务，"
                        + "并使用 16 位授权码（非登录密码）作为 IMAP 密码。";
            }
            if (host.contains("gmail.com") || email.contains("gmail.com")) {
                return "Gmail 登录失败。请确认已为该账户生成「应用专用密码」（8 位），"
                        + "不能用 Gmail 登录密码，且账户需开启两步验证。"
                        + "帮助：https://support.google.com/accounts/answer/185833";
            }
            if (host.contains("outlook.office365.com") || email.contains("outlook.com")
                    || email.contains("hotmail.com") || email.contains("live.com")) {
                return "Outlook/Hotmail 登录失败。微软已要求对所有 IMAP 连接使用 OAuth2，"
                        + "建议改用其他邮箱或在 Azure AD 注册应用，临时可启用「应用密码」"
                        + "（账户安全页 → 应用密码）。";
            }
            return "IMAP 登录失败：用户名或密码/授权码错误。"
                    + "国内邮箱（QQ/163/126）必须用授权码而非登录密码，请到邮箱网页版开启 IMAP 服务并重新生成授权码。";
        }

        // SSL 类
        if (combined.contains("ssl") || combined.contains("trust") || combined.contains("certificate")
                || combined.contains("handshake")) {
            return "SSL/TLS 握手失败：" + msg + "。请确认 IMAP 端口为 993 且勾选使用 SSL。";
        }

        // 文件夹不存在
        if (combined.contains("folder not found") || combined.contains("not found")) {
            return "收件箱文件夹不存在：" + msg;
        }

        // 兜底
        return "邮箱连接失败：" + msg;
    }

    private MailSummary toSummary(Message m) throws Exception {
        MailSummary s = new MailSummary();
        try { s.messageSeq = m.getMessageNumber(); } catch (Exception ignored) {}
        try { s.subject = m.getSubject(); } catch (Exception ignored) { s.subject = "(无主题)"; }
        Address[] froms = m.getFrom();
        if (froms != null && froms.length > 0) {
            Address a = froms[0];
            if (a instanceof InternetAddress) {
                InternetAddress ia = (InternetAddress) a;
                s.from = ia.getAddress();
                s.fromPersonal = ia.getPersonal();
            } else {
                s.from = a.toString();
            }
        }
        try { s.sentDate = m.getSentDate(); } catch (Exception ignored) {}
        try { s.seen = m.isSet(Flags.Flag.SEEN); } catch (Exception ignored) {}
        try { s.size = m.getSize(); } catch (Exception ignored) {}
        // 列表页不拉取正文 snippet，避免每封都 FETCH BODY 导致超时
        // 如需正文，调用 getMailDetail 拿单封详情
        s.snippet = "";
        return s;
    }

    private MailDetail toDetail(Message m) throws Exception {
        MailDetail d = new MailDetail();
        try { d.subject = m.getSubject(); } catch (Exception ignored) { d.subject = "(无主题)"; }
        Address[] froms = m.getFrom();
        if (froms != null && froms.length > 0) {
            Address a = froms[0];
            if (a instanceof InternetAddress) {
                InternetAddress ia = (InternetAddress) a;
                d.from = ia.getAddress();
                d.fromPersonal = ia.getPersonal();
            } else {
                d.from = a.toString();
            }
        }
        Address[] tos = m.getRecipients(Message.RecipientType.TO);
        if (tos != null) {
            StringBuilder sb = new StringBuilder();
            for (Address a : tos) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(a.toString());
            }
            d.to = sb.toString();
        }
        try { d.sentDate = m.getSentDate(); } catch (Exception ignored) {}
        extractBodies(m, d);
        return d;
    }

    private String extractSnippet(Message m, int max) {
        try {
            ByteArrayOutputStream textBuf = new ByteArrayOutputStream();
            String[] textHolder = new String[1];
            walkParts(m, part -> {
                if (textHolder[0] != null) return;
                String type = part.getContentType();
                if (type != null && type.toLowerCase().startsWith("text/plain")) {
                    Object c = part.getContent();
                    String s = c == null ? "" : c.toString();
                    if (s.length() > max) s = s.substring(0, max);
                    textHolder[0] = s;
                }
            });
            String s = textHolder[0];
            if (s == null) s = "";
            // 去掉多余空白
            s = s.replaceAll("\\s+", " ").trim();
            if (s.length() > max) s = s.substring(0, max);
            return s;
        } catch (Exception e) {
            return "";
        }
    }

    private void extractBodies(Message m, MailDetail d) {
        try {
            walkParts(m, part -> {
                String type = part.getContentType();
                if (type == null) return;
                type = type.toLowerCase();
                Object c;
                try {
                    c = part.getContent();
                } catch (Exception ignored) {
                    return;
                }
                if (c == null) return;
                String s = c.toString();
                if (type.startsWith("text/plain") && d.textBody == null) {
                    d.textBody = s;
                } else if (type.startsWith("text/html") && d.htmlBody == null) {
                    d.htmlBody = s;
                }
            });
        } catch (Exception ignored) {
        }
    }

    private void walkParts(Part p, PartVisitor visitor) throws Exception {
        visitor.visit(p);
        Object content = p.getContent();
        if (content instanceof Multipart) {
            Multipart mp = (Multipart) content;
            for (int i = 0; i < mp.getCount(); i++) {
                walkParts(mp.getBodyPart(i), visitor);
            }
        } else if (content instanceof Message) {
            // nested message (rare)
            walkParts((Part) content, visitor);
        }
    }

    private interface PartVisitor {
        void visit(Part part) throws Exception;
    }
}
