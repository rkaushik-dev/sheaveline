package io.github.rkaushikdev.sheaveline.api;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Every key the department has issued, and the one place a presented key is checked.
 *
 * @param keys the keys, no two sharing an id
 */
public record KeyRing(List<ApiKey> keys) {

    /** What a missing id is hashed against, so it costs what a wrong secret costs. */
    private static final byte[] NO_SALT = "0".repeat(Security.SALT_BYTES).getBytes(StandardCharsets.US_ASCII);

    /**
     * A ring.
     *
     * @throws AuthError when two keys share an id
     */
    public KeyRing {
        keys = List.copyOf(keys);
        Set<String> ids = new HashSet<>();
        for (ApiKey key : keys) {
            if (!ids.add(key.keyId())) {
                throw new AuthError("two keys share an id");
            }
        }
    }

    /**
     * A ring with no keys on it, which lets nobody in.
     *
     * @return the empty ring
     */
    public static KeyRing of() {
        return new KeyRing(List.of());
    }

    /**
     * A ring of keys.
     *
     * @param keys the keys
     * @return the ring
     * @throws AuthError when two keys share an id
     */
    public static KeyRing of(List<ApiKey> keys) {
        return new KeyRing(keys);
    }

    /**
     * A key by its id.
     *
     * @param keyId the id
     * @return the key
     * @throws AuthError when no key on the ring has that id
     */
    public ApiKey get(String keyId) {
        for (ApiKey key : keys) {
            if (key.keyId().equals(keyId)) {
                return key;
            }
        }
        throw new AuthError("that key is not on the ring");
    }

    /**
     * Read an id and secret pair and hand back the key, or refuse.
     *
     * <p>A missing id and a wrong secret take the same time, because the secret is hashed either way.
     *
     * @param presented the id and the secret separated by a single dot
     * @param on the day it is
     * @return the key
     * @throws AuthError when the pair does not read, the id is not on the ring, the secret is wrong, or
     *     the key is not current
     */
    public ApiKey authenticate(String presented, LocalDate on) {
        if (presented.chars().filter(one -> one == '.').count() != 1) {
            throw new AuthError("a key reads as an id and a secret separated by a dot");
        }
        int dot = presented.indexOf('.');
        String keyId = presented.substring(0, dot);
        String secret = presented.substring(dot + 1);
        ApiKey key;
        try {
            key = get(keyId);
        } catch (AuthError missing) {
            Security.derive(secret, NO_SALT);
            throw missing;
        }
        if (!key.matches(secret)) {
            throw new AuthError("that secret does not match that key");
        }
        if (!key.isCurrent(on)) {
            throw new AuthError("key " + keyId + " is not current");
        }
        return key;
    }

    /**
     * Authenticate a pair and check its key may do a job.
     *
     * @param presented the id and the secret separated by a single dot
     * @param on the day it is
     * @param wanted the role the job needs
     * @return the key
     * @throws AuthError when the pair does not authenticate, or its role is too low
     */
    public ApiKey require(String presented, LocalDate on, Role wanted) {
        ApiKey key = authenticate(presented, on);
        if (!key.may(wanted)) {
            throw new AuthError("a " + wanted.value() + " key is wanted and that one is a "
                    + key.role().value());
        }
        return key;
    }

    /**
     * A new ring with one more key on it. This ring is unchanged.
     *
     * @param key the key
     * @return the longer ring
     * @throws AuthError when the key's id is already on the ring
     */
    public KeyRing withKey(ApiKey key) {
        List<ApiKey> longer = new ArrayList<>(keys);
        longer.add(key);
        return new KeyRing(longer);
    }

    /**
     * Everybody holding a key, once each.
     *
     * @return the holders, sorted
     */
    public List<String> holders() {
        TreeSet<String> names = new TreeSet<>();
        keys.forEach(key -> names.add(key.holder()));
        return List.copyOf(names);
    }

    /**
     * The keys still working that run out soon.
     *
     * @param on the day it is
     * @param days how soon
     * @return the current keys with that many days or fewer left, in ring order
     */
    public List<ApiKey> expiringWithin(LocalDate on, int days) {
        return keys.stream()
                .filter(key -> key.isCurrent(on) && key.daysLeft(on) <= days)
                .toList();
    }

    /**
     * How many keys are on the ring.
     *
     * @return the count
     */
    public int size() {
        return keys.size();
    }

    /**
     * Whether the ring has no keys on it.
     *
     * @return true when nobody can be let in
     */
    public boolean isEmpty() {
        return keys.isEmpty();
    }
}
