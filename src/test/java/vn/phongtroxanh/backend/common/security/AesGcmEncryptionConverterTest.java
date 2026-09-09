package vn.phongtroxanh.backend.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.phongtroxanh.backend.common.util.AesEncryptionUtil;

import static org.junit.jupiter.api.Assertions.*;

class AesGcmEncryptionConverterTest {

    private final AesEncryptionUtil.AesAttributeConverter converter = new AesEncryptionUtil.AesAttributeConverter();

    @Test
    @DisplayName("Should encrypt and decrypt CCCD plaintext cleanly with AES-256-GCM")
    void testEncryptionDecryptionRoundtrip() {
        String originalCccd = "079203001234";

        String encrypted = converter.convertToDatabaseColumn(originalCccd);
        assertNotNull(encrypted);
        assertNotEquals(originalCccd, encrypted);

        String decrypted = converter.convertToEntityAttribute(encrypted);
        assertEquals(originalCccd, decrypted);
    }

    @Test
    @DisplayName("Should return null for null input")
    void testNullHandling() {
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
    }
}
