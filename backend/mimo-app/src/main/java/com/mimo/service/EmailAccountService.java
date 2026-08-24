package com.mimo.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import com.mimo.dto.EmailAccountDTO;
import com.mimo.entity.UserEmailAccount;
import com.mimo.mapper.UserEmailAccountMapper;
import com.mimo.util.EmailCryptoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmailAccountService {

    private final UserEmailAccountMapper accountMapper;
    private final EmailCryptoUtil cryptoUtil;
    private final MailReceiverService mailReceiverService;

    /** 常见邮箱 IMAP 预设 */
    private static final List<EmailAccountDTO.PresetVO> PRESETS = new ArrayList<>();
    static {
        EmailAccountDTO.PresetVO p1 = new EmailAccountDTO.PresetVO();
        p1.setProvider("QQ 邮箱"); p1.setImapHost("imap.qq.com"); p1.setImapPort(993);
        p1.setHint("使用授权码，非登录密码");
        EmailAccountDTO.PresetVO p2 = new EmailAccountDTO.PresetVO();
        p2.setProvider("163 邮箱"); p2.setImapHost("imap.163.com"); p2.setImapPort(993);
        p2.setHint("使用授权码，非登录密码");
        EmailAccountDTO.PresetVO p3 = new EmailAccountDTO.PresetVO();
        p3.setProvider("Gmail"); p3.setImapHost("imap.gmail.com"); p3.setImapPort(993);
        p3.setHint("需启用应用专用密码");
        EmailAccountDTO.PresetVO p4 = new EmailAccountDTO.PresetVO();
        p4.setProvider("Outlook / Hotmail"); p4.setImapHost("outlook.office365.com"); p4.setImapPort(993);
        p4.setHint("使用账号密码");
        EmailAccountDTO.PresetVO p5 = new EmailAccountDTO.PresetVO();
        p5.setProvider("126 邮箱"); p5.setImapHost("imap.126.com"); p5.setImapPort(993);
        p5.setHint("使用授权码，非登录密码");
        PRESETS.add(p1); PRESETS.add(p2); PRESETS.add(p3); PRESETS.add(p4); PRESETS.add(p5);
    }

    public List<EmailAccountDTO.PresetVO> listPresets() {
        return PRESETS;
    }

    public EmailAccountDTO.AccountVO bind(Long userId, EmailAccountDTO.BindRequest req) {
        if (req.getEmailAddress() == null || req.getEmailAddress().trim().isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "邮箱地址不能为空");
        }
        if (req.getImapHost() == null || req.getImapHost().trim().isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "IMAP 服务器不能为空");
        }
        if (req.getImapUsername() == null || req.getImapUsername().trim().isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "IMAP 用户名不能为空");
        }
        if (req.getImapPassword() == null || req.getImapPassword().isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "IMAP 密码不能为空");
        }

        // 检查重复
        UserEmailAccount exist = accountMapper.selectOne(new QueryWrapper<UserEmailAccount>()
                .eq("user_id", userId)
                .eq("email_address", req.getEmailAddress().trim()));
        if (exist != null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该邮箱已绑定，请先解绑");
        }

        UserEmailAccount acc = new UserEmailAccount();
        acc.setUserId(userId);
        acc.setEmailAddress(req.getEmailAddress().trim());
        acc.setImapHost(req.getImapHost().trim());
        acc.setImapPort(req.getImapPort() == null ? 993 : req.getImapPort());
        acc.setImapUsername(req.getImapUsername().trim());
        acc.setImapPasswordEnc(cryptoUtil.encrypt(req.getImapPassword()));
        acc.setIsDefault(req.getIsDefault() != null && req.getIsDefault() == 1 ? 1 : 0);

        // 如果是用户第一个账户，自动设为默认
        long count = accountMapper.selectCount(new QueryWrapper<UserEmailAccount>().eq("user_id", userId));
        if (count == 0) {
            acc.setIsDefault(1);
        } else if (acc.getIsDefault() == 1) {
            // 取消其他默认
            accountMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<UserEmailAccount>()
                    .eq("user_id", userId)
                    .set("is_default", 0));
        }

        accountMapper.insert(acc);
        return toVO(acc);
    }

    public void unbind(Long userId, Long accountId) {
        UserEmailAccount acc = mustGetOwned(userId, accountId);
        accountMapper.deleteById(acc.getId());
    }

    public List<EmailAccountDTO.AccountVO> list(Long userId) {
        List<UserEmailAccount> list = accountMapper.selectList(new QueryWrapper<UserEmailAccount>()
                .eq("user_id", userId)
                .orderByDesc("is_default")
                .orderByDesc("created_at"));
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    public UserEmailAccount getDefault(Long userId) {
        List<UserEmailAccount> list = accountMapper.selectList(new QueryWrapper<UserEmailAccount>()
                .eq("user_id", userId)
                .orderByDesc("is_default")
                .orderByDesc("created_at"));
        if (list.isEmpty()) return null;
        // 优先返回默认账户
        return list.stream().filter(a -> a.getIsDefault() != null && a.getIsDefault() == 1)
                .findFirst().orElse(list.get(0));
    }

    public UserEmailAccount mustGetOwned(Long userId, Long accountId) {
        UserEmailAccount acc = accountMapper.selectById(accountId);
        if (acc == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "邮箱账户不存在");
        }
        if (!acc.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作此账户");
        }
        return acc;
    }

    /** 诊断用：解密密码 */
    public String decryptPassword(UserEmailAccount acc) {
        return cryptoUtil.decrypt(acc.getImapPasswordEnc());
    }

    public void updateLastSynced(Long accountId) {
        UserEmailAccount update = new UserEmailAccount();
        update.setId(accountId);
        update.setLastSyncedAt(LocalDateTime.now());
        accountMapper.updateById(update);
    }

    /**
     * 更新通知偏好（v2.13.3）
     * @param userId    用户 ID
     * @param accountId 邮箱账户 ID
     * @param enabled   是否启用邮件通知
     * @param types     启用的事件类型（逗号分隔），null 表示清空
     * @param smtpHost  SMTP 主机，null 表示按 IMAP 推断
     * @param smtpPort  SMTP 端口，null 表示默认 465
     */
    public EmailAccountDTO.AccountVO updateNotifySettings(Long userId, Long accountId,
                                                          Integer enabled, String types,
                                                          String smtpHost, Integer smtpPort) {
        UserEmailAccount acc = mustGetOwned(userId, accountId);
        UserEmailAccount update = new UserEmailAccount();
        update.setId(accountId);
        if (enabled != null) update.setNotifyEnabled(enabled);
        if (types != null) update.setNotifyTypes(types.trim().isEmpty() ? null : types.trim());
        if (smtpHost != null) update.setSmtpHost(smtpHost.trim().isEmpty() ? null : smtpHost.trim());
        if (smtpPort != null) update.setSmtpPort(smtpPort);
        accountMapper.updateById(update);
        // 回填当前对象用于返回
        if (enabled != null) acc.setNotifyEnabled(enabled);
        if (types != null) acc.setNotifyTypes(types.trim().isEmpty() ? null : types.trim());
        if (smtpHost != null) acc.setSmtpHost(smtpHost.trim().isEmpty() ? null : smtpHost.trim());
        if (smtpPort != null) acc.setSmtpPort(smtpPort);
        return toVO(acc);
    }

    /** 取用户所有"启用了某类通知"的邮箱账户 */
    public List<UserEmailAccount> listNotifyEnabled(Long userId, String type) {
        List<UserEmailAccount> list = accountMapper.selectList(new QueryWrapper<UserEmailAccount>()
                .eq("user_id", userId)
                .eq("notify_enabled", 1));
        if (list.isEmpty() || type == null) return new ArrayList<>();
        List<UserEmailAccount> out = new ArrayList<>();
        for (UserEmailAccount a : list) {
            if (a.getNotifyTypes() == null) continue;
            for (String t : a.getNotifyTypes().split(",")) {
                if (type.equals(t.trim())) {
                    out.add(a);
                    break;
                }
            }
        }
        return out;
    }

    private EmailAccountDTO.AccountVO toVO(UserEmailAccount acc) {
        EmailAccountDTO.AccountVO vo = new EmailAccountDTO.AccountVO();
        vo.setId(acc.getId());
        vo.setEmailAddress(acc.getEmailAddress());
        vo.setImapHost(acc.getImapHost());
        vo.setImapPort(acc.getImapPort());
        vo.setImapUsername(acc.getImapUsername());
        vo.setIsDefault(acc.getIsDefault());
        vo.setLastSyncedAt(acc.getLastSyncedAt() == null ? null : acc.getLastSyncedAt().toString());
        vo.setCreatedAt(acc.getCreatedAt() == null ? null : acc.getCreatedAt().toString());
        // v2.13.3：通知偏好
        vo.setSmtpHost(acc.getSmtpHost());
        vo.setSmtpPort(acc.getSmtpPort());
        vo.setNotifyTypes(acc.getNotifyTypes());
        vo.setNotifyEnabled(acc.getNotifyEnabled() == null ? 0 : acc.getNotifyEnabled());
        return vo;
    }
}
