package io.github.rkaushikdev.sheaveline.api;

import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * One key, its role and the hash of the secret that goes with it. The secret itself is never held.
 *
 * @param keyId the public half of what a client presents
 * @param holder who the key was issued to
 * @param role what the key may do
 * @param salt the key's own salt, at least {@link Security#SALT_BYTES} bytes
 * @param digest the salted hash of the secret
 * @param issued the first day the key works
 * @param expires the last day the key works
 */
public record ApiKey(
        String keyId, String holder, Role role, byte[] salt, byte[] digest, LocalDate issued, LocalDate expires) {

    /**
     * A key whose record holds together.
     *
     * @throws AuthError when it has no id or nobody holding it, expires before it was issued, or has
     *     too little salt
     */
    public ApiKey {
        Objects.requireNonNull(keyId, "keyId");
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(issued, "issued");
        Objects.requireNonNull(expires, "expires");
        salt = Objects.requireNonNull(salt, "salt").clone();
        digest = Objects.requireNonNull(digest, "digest").clone();
        if (keyId.strip().isEmpty()) {
            throw new AuthError("a key has an id");
        }
        if (holder.strip().isEmpty()) {
            throw new AuthError("key " + keyId + " has nobody holding it");
        }
        if (!expires.isAfter(issued)) {
            throw new AuthError("key " + keyId + " expires before it was issued");
        }
        if (salt.length < Security.SALT_BYTES) {
            throw new AuthError("key " + keyId + " has too little salt");
        }
    }

    /**
     * A key minted with a fresh secret and a fresh id.
     *
     * @param holder who it is for
     * @param role what it may do
     * @param issued the first day it works
     * @param expires the last day it works
     * @return the key and its secret
     */
    public static Issued issue(String holder, Role role, LocalDate issued, LocalDate expires) {
        return issue(holder, role, issued, expires, Optional.empty(), Optional.empty());
    }

    /**
     * Mint a key. The secret is handed back once and never stored.
     *
     * @param holder who it is for
     * @param role what it may do
     * @param issued the first day it works
     * @param expires the last day it works
     * @param secret the secret to use; a fresh one when empty or blank text
     * @param keyId the id to use; a fresh one when empty or blank text
     * @return the key and its secret
     * @throws AuthError when the secret is too short to lean on, or the key does not hold together
     */
    public static Issued issue(
            String holder,
            Role role,
            LocalDate issued,
            LocalDate expires,
            Optional<String> secret,
            Optional<String> keyId) {
        String raw = secret.filter(given -> !given.isEmpty()).orElseGet(Security::newSecret);
        if (raw.codePointCount(0, raw.length()) < Security.MINIMUM_SECRET_LENGTH) {
            throw new AuthError("a key secret is at least " + Security.MINIMUM_SECRET_LENGTH
                    + " characters, because the derivation leans on its entropy");
        }
        byte[] salt = Security.newSalt();
        ApiKey key = new ApiKey(
                keyId.filter(given -> !given.isEmpty()).orElseGet(Security::newKeyId),
                holder,
                role,
                salt,
                Security.derive(raw, salt),
                issued,
                expires);
        return new Issued(key, raw);
    }

    /**
     * The salt, as a copy.
     *
     * @return the salt
     */
    @Override
    public byte[] salt() {
        return salt.clone();
    }

    /**
     * The digest, as a copy.
     *
     * @return the digest
     */
    @Override
    public byte[] digest() {
        return digest.clone();
    }

    /**
     * Whether a secret is the one this key was issued with, compared in constant time.
     *
     * @param secret what was presented
     * @return true when it matches
     */
    public boolean matches(String secret) {
        return MessageDigest.isEqual(digest, Security.derive(secret, salt));
    }

    /**
     * Whether the key works on a day: from the day it was issued to the day it expires, both
     * included.
     *
     * @param on the day
     * @return true between its dates
     */
    public boolean isCurrent(LocalDate on) {
        return !on.isBefore(issued) && !on.isAfter(expires);
    }

    /**
     * Whether the key may do a job that needs a role.
     *
     * @param wanted the role the job needs
     * @return true when the key's role covers it
     */
    public boolean may(Role wanted) {
        return role.covers(wanted);
    }

    /**
     * How many days the key has left.
     *
     * @param on the day
     * @return the days from then to its expiry, negative once it has lapsed
     */
    public long daysLeft(LocalDate on) {
        return ChronoUnit.DAYS.between(on, expires);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ApiKey key
                && keyId.equals(key.keyId)
                && holder.equals(key.holder)
                && role == key.role
                && Arrays.equals(salt, key.salt)
                && Arrays.equals(digest, key.digest)
                && issued.equals(key.issued)
                && expires.equals(key.expires);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyId, holder, role, Arrays.hashCode(salt), Arrays.hashCode(digest), issued, expires);
    }

    /** The id, the holder and the role, and nothing of the secret. */
    @Override
    public String toString() {
        return keyId + " (" + holder + ", " + role.value() + ")";
    }

    /**
     * A key just minted, with the only copy of its secret there will ever be.
     *
     * @param key the key
     * @param secret its secret
     */
    public record Issued(ApiKey key, String secret) {

        /** A minted key. */
        public Issued {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(secret, "secret");
        }

        /**
         * What a client sends to be let in.
         *
         * @return the key id and the secret separated by a dot
         */
        public String presented() {
            return key.keyId() + "." + secret;
        }

        /** The key, printed without its secret. */
        @Override
        public String toString() {
            return key.toString();
        }
    }
}
