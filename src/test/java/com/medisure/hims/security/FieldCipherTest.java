package com.medisure.hims.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class FieldCipherTest {

    /** Fake, test-only keys (Base64 of 32 ASCII bytes). */
    private static final String KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String OTHER_KEY = "YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=";

    private final FieldCipher cipher = new FieldCipher(KEY);

    @Test
    @DisplayName("Medical text encrypts to unreadable text and decrypts back exactly")
    void roundTripsText() {
        String plain = "Penicillin allergy; asthma — Salbutamol inhaler";

        String stored = cipher.encrypt(plain);

        assertTrue(stored.startsWith(FieldCipher.TEXT_PREFIX));
        assertFalse(stored.contains("Penicillin"));
        assertEquals(plain, cipher.decrypt(stored));
    }

    @Test
    @DisplayName("The same text encrypts differently every time, so equal values cannot be spotted")
    void usesAFreshNonceEachTime() {
        assertNotEquals(cipher.encrypt("Diabetes"), cipher.encrypt("Diabetes"));
    }

    @Test
    @DisplayName("A changed ciphertext is rejected instead of showing wrong data")
    void detectsTampering() {
        String stored = cipher.encrypt("Hypertension");
        byte[] raw = Base64.getDecoder().decode(stored.substring(FieldCipher.TEXT_PREFIX.length()));
        raw[raw.length - 1] ^= 1;
        String tampered = FieldCipher.TEXT_PREFIX + Base64.getEncoder().encodeToString(raw);

        assertThrows(IllegalStateException.class, () -> cipher.decrypt(tampered));
    }

    @Test
    @DisplayName("Data encrypted with one key cannot be read with another")
    void wrongKeyCannotRead() {
        String stored = cipher.encrypt("Hypothyroidism");

        assertThrows(IllegalStateException.class, () -> new FieldCipher(OTHER_KEY).decrypt(stored));
    }

    @Test
    @DisplayName("Values saved before encryption still read, and nothing is encrypted twice")
    void readsOldPlainValuesAndIsIdempotent() {
        assertEquals("Pollen", cipher.decrypt("Pollen"));
        assertNull(cipher.encrypt(null));
        assertNull(cipher.decrypt(null));

        String once = cipher.encrypt("Pollen");
        assertEquals(once, cipher.encrypt(once));
    }

    @Test
    @DisplayName("Claim documents are encrypted on disk and read back byte for byte")
    void roundTripsFiles() {
        byte[] pdf = "%PDF-1.7 discharge summary".getBytes(StandardCharsets.US_ASCII);

        byte[] stored = cipher.encryptFile(pdf);

        assertTrue(cipher.isEncrypted(stored));
        assertFalse(new String(stored, StandardCharsets.ISO_8859_1).contains("discharge"));
        assertArrayEquals(pdf, cipher.decryptFile(stored));
        assertArrayEquals(pdf, cipher.decryptFile(pdf));      // an old, unencrypted file still opens
    }

    @Test
    @DisplayName("The app refuses to start without a valid 256-bit key")
    void requiresAValidKey() {
        assertThrows(IllegalStateException.class, () -> new FieldCipher(""));
        assertThrows(IllegalStateException.class, () -> new FieldCipher("not base64!"));
        assertThrows(IllegalStateException.class,
                () -> new FieldCipher(Base64.getEncoder().encodeToString(new byte[16])));
    }
}
