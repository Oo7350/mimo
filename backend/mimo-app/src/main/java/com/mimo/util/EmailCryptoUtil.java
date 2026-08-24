package com.mimo.util;

import com.mimo.common.BusinessException;
import com.mimo.common.ResultCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 邮箱 IMAP 密码加解密工具：AES-GCM。
 * 密文格式：base64(iv || ciphertext+tag)。
 * 密钥取自配置项 email.encryption-key（默认开发密钥，生产环境必须覆盖）。
 */
@Component
public class EmailCryptoUtil {

    private static final String ALGO = "AES/GCM/NoPadding";
    private static final String KEY_ALGO = "AES";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec keySpec;

    public EmailCryptoUtil(@Value("${email.encryption-key:mimo-dev-encryption-key-32bytes-2026}") String rawKey) throws Exception {
        byte[] keyBytes = padOrHash(rawKey);
        this.keySpec = new SecretKeySpec(keyBytes, KEY_ALGO);
    }

    public String encrypt(String plain) {
        if (plain == null) return null;
        try {
            byte[] iv = new byte[IV_LEN];
            new SecureRandom().nextBytes(iv);
            Cipher c = Cipher.getInstance(ALGO);
            c.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "邮箱密码加密失败: " + e.getMessage());
        }
    }

    public String decrypt(String enc) {
        if (enc == null) return null;
        try {
            byte[] all = Base64.getDecoder().decode(enc);
            if (all.length <= IV_LEN) {
                throw new IllegalArgumentException("密文格式非法");
            }
            byte[] iv = new byte[IV_LEN];
            System.arraycopy(all, 0, iv, 0, IV_LEN);
            byte[] ct = new byte[all.length - IV_LEN];
            System.arraycopy(all, IV_LEN, ct, 0, ct.length);
            Cipher c = Cipher.getInstance(ALGO);
            c.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            return new String(c.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "邮箱密码解密失败: " + e.getMessage());
        }
    }

    /** 把任意长字符串规整成 32 字节 AES key */
    private static byte[] padOrHash(String raw) throws Exception {
        byte[] b = raw.getBytes(StandardCharsets.UTF_8);
        if (b.length == 32) return b;
        byte[] out = new byte[32];
        int i = 0;
        while (i < 32) {
            for (byte x : b) {
                if (i >= 32) break;
                out[i++] = x;
            }
        }
        return out;
    }
}
