package io.github.rkaushikdev.sheaveline.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Minting and checking the secrets behind the API keys.
 *
 * <p>The clients here are a tablet at each terminal, the office machine and, once a season, the
 * board's auditor. They are not people typing passwords, so the token is a key with a role on it.
 *
 * <p>Three things are worth stating because they are easy to get wrong. The secret is never
 * stored, only a salted hash of it, so a copy of the database does not hand somebody the keys. The
 * comparison is constant time, because a comparison that returns early tells an attacker how much
 * of the secret was right. And the derivation is one round rather than a quarter of a million.
 *
 * <p>That last one looks like a corner cut and is not. Stretching exists to make guessing a low
 * entropy secret expensive, and there is nothing to guess here: the secret is thirty two random
 * bytes, so an attacker who cannot break SHA-256 cannot get anywhere however few rounds there are.
 * What a quarter of a million rounds does buy is a fifth of a second of the department's own time on
 * every single request, which is a denial of service the department is doing to itself. So the
 * minimum length is enforced at issue instead, and the entropy assumption the single round rests on
 * is a rule rather than a hope.
 */
public final class Security {

    /** How much salt each key carries, in bytes. */
    public static final int SALT_BYTES = 16;

    /** How many random bytes a minted secret is made from. */
    public static final int SECRET_BYTES = 32;

    /** The shortest secret a key may be issued with, in characters. */
    public static final int MINIMUM_SECRET_LENGTH = 32;

    /** How long a key id is, in hex digits. */
    public static final int KEY_ID_LENGTH = 12;

    private static final SecureRandom RANDOM = new SecureRandom();

    private Security() {}

    /**
     * A fresh secret: thirty two random bytes, written URL safe without padding.
     *
     * @return the secret, forty three characters long
     */
    public static String newSecret() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(SECRET_BYTES));
    }

    /**
     * A fresh key id.
     *
     * @return twelve lower case hex digits
     */
    public static String newKeyId() {
        return HexFormat.of().formatHex(randomBytes(KEY_ID_LENGTH / 2));
    }

    /**
     * Fresh salt for a key.
     *
     * @return {@link #SALT_BYTES} random bytes
     */
    public static byte[] newSalt() {
        return randomBytes(SALT_BYTES);
    }

    /**
     * Salted SHA-256 of a secret. One round, because the secret carries its own entropy.
     *
     * @param secret the secret
     * @param salt the key's salt
     * @return the digest of the salt followed by the secret's UTF-8 bytes
     */
    public static byte[] derive(String secret, byte[] salt) {
        MessageDigest digest = sha256();
        digest.update(salt);
        digest.update(secret.getBytes(StandardCharsets.UTF_8));
        return digest.digest();
    }

    private static byte[] randomBytes(int count) {
        byte[] bytes = new byte[count];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("every Java runtime carries SHA-256", missing);
        }
    }
}
