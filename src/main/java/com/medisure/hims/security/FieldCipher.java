package com.medisure.hims.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Encrypts medical information at rest with AES-256-GCM.
 *
 * Every value gets a fresh random 12-byte nonce, so the same text never encrypts to the same
 * result, and GCM's 128-bit tag means a changed value fails to decrypt instead of showing wrong
 * data. The key comes from configuration (app.encryption.key, or the HIMS_FIELD_KEY environment
 * variable) and never from the code or Git. The app will not start without it, so medical data can
 * never be saved unencrypted by mistake.
 *
 * Text is stored as "enc:v1:" + Base64(nonce + ciphertext); files start with the bytes "HIMSENC1".
 * Values without the marker are treated as not yet encrypted, so a database from before encryption
 * still reads until {@link EncryptionMigration} converts it.
 */
@Component
public class FieldCipher {

    public static final String TEXT_PREFIX = "enc:v1:";
    private static final byte[] FILE_MAGIC = "HIMSENC1".getBytes(StandardCharsets.US_ASCII);
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public FieldCipher(@Value("${app.encryption.key:}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("""
                    No encryption key is configured, so medical information cannot be protected.
                    Generate one with:  openssl rand -base64 32
                    and set it as app.encryption.key in application.yml (or the HIMS_FIELD_KEY environment
                    variable). Keep a copy somewhere safe: without it the medical data cannot be read.""");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("app.encryption.key must be Base64 (openssl rand -base64 32)", ex);
        }
        if (raw.length != 32) {
            throw new IllegalStateException("app.encryption.key must be 32 bytes (256 bits); it is " + raw.length);
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    // ------------------------------------------------------------------ text

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(TEXT_PREFIX);
    }

    public String encrypt(String plain) {
        if (plain == null || isEncrypted(plain)) {
            return plain;
        }
        byte[] sealed = seal(plain.getBytes(StandardCharsets.UTF_8));
        return TEXT_PREFIX + Base64.getEncoder().encodeToString(sealed);
    }

    public String decrypt(String stored) {
        if (!isEncrypted(stored)) {
            return stored;   // null, or saved before encryption was switched on
        }
        byte[] sealed = Base64.getDecoder().decode(stored.substring(TEXT_PREFIX.length()));
        return new String(open(sealed), StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ files

    public boolean isEncrypted(byte[] content) {
        return content != null && content.length >= FILE_MAGIC.length
                && Arrays.equals(Arrays.copyOf(content, FILE_MAGIC.length), FILE_MAGIC);
    }

    public byte[] encryptFile(byte[] plain) {
        if (isEncrypted(plain)) {
            return plain;
        }
        byte[] sealed = seal(plain);
        return ByteBuffer.allocate(FILE_MAGIC.length + sealed.length).put(FILE_MAGIC).put(sealed).array();
    }

    public byte[] decryptFile(byte[] stored) {
        if (!isEncrypted(stored)) {
            return stored;
        }
        return open(Arrays.copyOfRange(stored, FILE_MAGIC.length, stored.length));
    }

    // ------------------------------------------------------------------ AES-GCM

    private byte[] seal(byte[] plain) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plain);
            return ByteBuffer.allocate(nonce.length + ciphertext.length).put(nonce).put(ciphertext).array();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Encryption failed", ex);
        }
    }

    private byte[] open(byte[] sealed) {
        if (sealed.length < NONCE_BYTES + TAG_BITS / 8) {
            throw new IllegalStateException("Encrypted value is too short to be valid");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, sealed, 0, NONCE_BYTES));
            return cipher.doFinal(sealed, NONCE_BYTES, sealed.length - NONCE_BYTES);
        } catch (AEADBadTagException ex) {
            throw new IllegalStateException(
                    "Encrypted medical data could not be read: the key is wrong or the stored value was changed", ex);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Decryption failed", ex);
        }
    }
}
