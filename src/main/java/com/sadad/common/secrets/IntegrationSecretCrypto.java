package com.sadad.common.secrets;

import com.sadad.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM for the vendor credentials held on the integration registry.
 *
 * <p><b>Why a third one of these.</b> Two copies of this algorithm already exist -
 * saddad-admin's and saddad-onboarding's - and they are deliberately keyed differently, so
 * merging them would break every secret already at rest. This one is not a merge. It exists
 * because a registry credential is written by saddad-admin and read by whichever service
 * makes the call, which means it is the first secret on this platform that genuinely has to
 * cross a service boundary, and therefore the first that needs one agreed key.
 *
 * <p>That key is {@code sadad.secrets.encryption-key-base64}. Every service that reads a
 * registry credential must be given the same value; a service that is given none simply
 * does not get this bean, which is what the condition below is for - the alternative is a
 * service failing to start over a capability it never uses.
 *
 * <p>The ciphertext layout is the same as the two existing utilities: a random 12-byte IV
 * prepended to the GCM output, the whole thing Base64. Same layout, different key, no
 * migration of anything already encrypted.
 */
@Component
@ConditionalOnProperty(name = "sadad.secrets.encryption-key-base64")
public class IntegrationSecretCrypto {

    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final SecretKeySpec secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public IntegrationSecretCrypto(@Value("${sadad.secrets.encryption-key-base64}") String encryptionKeyBase64) {
        byte[] keyBytes = Base64.getDecoder().decode(encryptionKeyBase64);
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "sadad.secrets.encryption-key-base64 must decode to exactly 32 bytes (AES-256), got "
                            + keyBytes.length);
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array());
        } catch (GeneralSecurityException e) {
            // The message deliberately says nothing about the value being encrypted.
            throw new BusinessException("Could not store the credential securely.");
        }
    }

    public String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) return null;
        try {
            ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(encrypted));
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), java.nio.charset.StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | RuntimeException e) {
            // Most often a credential encrypted under a different key - a service configured
            // with the wrong one. Says so without echoing either the ciphertext or the key.
            throw new BusinessException("Could not read the stored credential. Check that this service "
                    + "is configured with the same secrets encryption key as saddad-admin.");
        }
    }
}
