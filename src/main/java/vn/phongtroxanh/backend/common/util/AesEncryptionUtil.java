package vn.phongtroxanh.backend.common.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Slf4j
@Component
public class AesEncryptionUtil {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BIT = 128;
    private static final int IV_LENGTH_BYTE = 12;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final String DEFAULT_SECRET = "4c6f6e675f616e645f73757065725f7365637265745f6a77745f6b65795f666f725f70686f6e6774726f78616e685f766e5f68733531325f73656375726974795f746f6b656e";
    private static volatile SecretKey staticSecretKey;

    static {
        initKey(DEFAULT_SECRET);
    }

    public AesEncryptionUtil(@Value("${app.jwt.secret:4c6f6e675f616e645f73757065725f7365637265745f6a77745f6b65795f666f725f70686f6e6774726f78616e685f766e5f68733531325f73656375726974795f746f6b656e}") String secretKeyString) {
        initKey(secretKeyString);
    }

    private static void initKey(String secretKeyString) {
        byte[] keyBytes = new byte[32]; // 256-bit key
        byte[] rawBytes = (secretKeyString != null ? secretKeyString : DEFAULT_SECRET).getBytes(StandardCharsets.UTF_8);
        System.arraycopy(rawBytes, 0, keyBytes, 0, Math.min(rawBytes.length, 32));
        staticSecretKey = new SecretKeySpec(keyBytes, "AES");
    }

    public static String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) return plaintext;
        try {
            byte[] iv = new byte[IV_LENGTH_BYTE];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.ENCRYPT_MODE, staticSecretKey, parameterSpec);

            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            log.error("Failed to encrypt data with AES-GCM: {}", e.getMessage());
            throw new RuntimeException("Encryption failure", e);
        }
    }

    public static String decrypt(String cipherTextBase64) {
        if (cipherTextBase64 == null || cipherTextBase64.isBlank()) return cipherTextBase64;
        try {
            byte[] decode = Base64.getDecoder().decode(cipherTextBase64);
            ByteBuffer byteBuffer = ByteBuffer.wrap(decode);

            byte[] iv = new byte[IV_LENGTH_BYTE];
            byteBuffer.get(iv);

            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.DECRYPT_MODE, staticSecretKey, parameterSpec);

            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to decrypt data with AES-GCM: {}", e.getMessage());
            return cipherTextBase64; // Fallback in case raw
        }
    }

    @Converter
    public static class AesAttributeConverter implements AttributeConverter<String, String> {
        @Override
        public String convertToDatabaseColumn(String attribute) {
            return AesEncryptionUtil.encrypt(attribute);
        }

        @Override
        public String convertToEntityAttribute(String dbData) {
            return AesEncryptionUtil.decrypt(dbData);
        }
    }
}
