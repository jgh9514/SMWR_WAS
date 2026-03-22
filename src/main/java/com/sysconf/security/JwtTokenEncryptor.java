package com.sysconf.security;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import jakarta.annotation.PostConstruct;

@Component
public class JwtTokenEncryptor {
    private static final String ALG = "AES/CBC/PKCS5Padding";

    @Value("${smw.security.jwt-encryption-key}")
    private String key;

    private String iv;

    @PostConstruct
    void init() {
        if (key == null || !(key.length() == 16 || key.length() == 24 || key.length() == 32)) {
            throw new IllegalStateException("smw.security.jwt-encryption-key must be 16, 24, or 32 characters long");
        }
        iv = key.substring(0, 16);
    }

    /**
     * String 으로 들어온 text 를 암호화한다.
     * @param text
     * @return
     * @throws Exception
     */
    public String encrypt(String text) throws Exception {
        Cipher cipher = Cipher.getInstance(ALG);
        SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
        IvParameterSpec ivParameterSpec = new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8));
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivParameterSpec);

        byte[] encrypted = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    /**
     * 암호화된 텍스트를 복호화한다.
     * @param cipherText
     * @return
     * @throws Exception
     */
    public String decrypt(String cipherText) throws Exception {
        Cipher cipher = Cipher.getInstance(ALG);
        SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
        IvParameterSpec ivParamSpec = new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8));
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivParamSpec);

        byte[] decodedBytes = Base64.getDecoder().decode(cipherText);
        byte[] decrypted = cipher.doFinal(decodedBytes);
        return new String(decrypted, StandardCharsets.UTF_8);
    }
}

