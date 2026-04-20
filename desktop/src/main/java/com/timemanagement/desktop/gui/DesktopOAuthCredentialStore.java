package com.timemanagement.desktop.gui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.timemanagement.core.dataclass.GoogleOAuthSession;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

public class DesktopOAuthCredentialStore {
    private static final int SALT_LENGTH = 16;
    private static final int IV_LENGTH = 12;
    private static final int KEY_LENGTH = 256;
    private static final int PBKDF2_ITERATIONS = 210_000;

    private final Path sessionPath;
    private final ObjectMapper mapper;
    private final SecureRandom secureRandom;

    public DesktopOAuthCredentialStore(Path sessionPath) {
        this(sessionPath, new SecureRandom());
    }

    DesktopOAuthCredentialStore(Path sessionPath, SecureRandom secureRandom) {
        this.sessionPath = sessionPath;
        this.secureRandom = secureRandom;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public Optional<GoogleOAuthSession> load(char[] passphrase) {
        requirePassphrase(passphrase);
        if (!Files.exists(sessionPath)) {
            return Optional.empty();
        }
        try {
            EncryptedEnvelope envelope = mapper.readValue(sessionPath.toFile(), EncryptedEnvelope.class);
            byte[] salt = Base64.getDecoder().decode(envelope.getSalt());
            byte[] iv = Base64.getDecoder().decode(envelope.getIv());
            byte[] ciphertext = Base64.getDecoder().decode(envelope.getCiphertext());

            SecretKey key = deriveKey(passphrase, salt);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return Optional.of(mapper.readValue(plaintext, GoogleOAuthSession.class));
        } catch (AEADBadTagException e) {
            throw new IllegalArgumentException("Could not unlock the saved Google session with the provided passphrase.", e);
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Could not load the saved Google session.", e);
        }
    }

    public void save(GoogleOAuthSession session, char[] passphrase) {
        requirePassphrase(passphrase);
        if (session == null) {
            throw new IllegalArgumentException("OAuth session is required.");
        }
        try {
            Files.createDirectories(sessionPath.getParent());

            byte[] salt = new byte[SALT_LENGTH];
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(salt);
            secureRandom.nextBytes(iv);

            SecretKey key = deriveKey(passphrase, salt);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] plaintext = mapper.writeValueAsBytes(session);
            byte[] ciphertext = cipher.doFinal(plaintext);

            EncryptedEnvelope envelope = new EncryptedEnvelope(
                    Base64.getEncoder().encodeToString(salt),
                    Base64.getEncoder().encodeToString(iv),
                    Base64.getEncoder().encodeToString(ciphertext)
            );
            mapper.writerWithDefaultPrettyPrinter().writeValue(sessionPath.toFile(), envelope);
            tightenPermissions();
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Could not save the Google session securely.", e);
        }
    }

    public void clear() {
        try {
            Files.deleteIfExists(sessionPath);
        } catch (IOException e) {
            throw new IllegalStateException("Could not remove the saved Google session.", e);
        }
    }

    private void tightenPermissions() {
        try {
            Set<PosixFilePermission> permissions = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE
            );
            Files.setPosixFilePermissions(sessionPath, permissions);
        } catch (UnsupportedOperationException | IOException ignored) {
        }
    }

    private SecretKey deriveKey(char[] passphrase, byte[] salt) throws GeneralSecurityException {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_LENGTH);
        byte[] encoded = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(encoded, "AES");
    }

    private void requirePassphrase(char[] passphrase) {
        if (passphrase == null || passphrase.length == 0) {
            throw new IllegalArgumentException("A credential passphrase is required.");
        }
        String value = new String(passphrase);
        if (value.isBlank()) {
            throw new IllegalArgumentException("A credential passphrase is required.");
        }
    }

    static class EncryptedEnvelope {
        private String salt;
        private String iv;
        private String ciphertext;

        public EncryptedEnvelope() {
        }

        public EncryptedEnvelope(String salt, String iv, String ciphertext) {
            this.salt = salt;
            this.iv = iv;
            this.ciphertext = ciphertext;
        }

        public String getSalt() {
            return salt;
        }

        public String getIv() {
            return iv;
        }

        public String getCiphertext() {
            return ciphertext;
        }
    }
}
