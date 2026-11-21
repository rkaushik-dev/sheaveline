package io.github.rkaushikdev.sheaveline.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SecurityTest {

    static final LocalDate ON = LocalDate.of(2025, 1, 15);
    static final String KNOWN_SECRET = "a-known-secret-for-the-test-long-enough";

    static ApiKey.Issued issue() {
        return issue("terminal tablet", Role.OPERATOR, LocalDate.of(2025, 12, 1), "aa11bb22cc33");
    }

    static ApiKey.Issued issue(String holder, Role role, LocalDate expires, String keyId) {
        return ApiKey.issue(
                holder, role, LocalDate.of(2024, 11, 1), expires, Optional.of(KNOWN_SECRET), Optional.of(keyId));
    }

    static ApiKey.Issued expiring(LocalDate expires, String keyId) {
        return issue("terminal tablet", Role.OPERATOR, expires, keyId);
    }

    /** Whether {@code needle} appears anywhere in {@code haystack} as a run of bytes. */
    static boolean contains(byte[] haystack, byte[] needle) {
        for (int start = 0; start + needle.length <= haystack.length; start++) {
            int matched = 0;
            while (matched < needle.length && haystack[start + matched] == needle[matched]) {
                matched++;
            }
            if (matched == needle.length) {
                return true;
            }
        }
        return false;
    }

    @Test
    void aManagerKeyCoversEverythingUnderIt() {
        assertThat(Role.MANAGER.covers(Role.OPERATOR)).isTrue();
        assertThat(Role.MANAGER.covers(Role.READER)).isTrue();
        assertThat(Role.MECHANIC.covers(Role.OPERATOR)).isTrue();
        assertThat(Role.OPERATOR.covers(Role.MECHANIC)).isFalse();
        assertThat(Role.READER.covers(Role.READER)).isTrue();
    }

    @Test
    void aKeyIsMintedWithItsSecretHandedBackOnce() {
        ApiKey.Issued minted = issue();
        assertThat(minted.secret()).isEqualTo(KNOWN_SECRET);
        assertThat(minted.key().matches(minted.secret())).isTrue();
        assertThat(minted.key().matches("something else")).isFalse();
    }

    @Test
    void theSecretIsNeverStored() {
        ApiKey.Issued minted = issue();
        ApiKey key = minted.key();
        assertThat(contains(key.digest(), minted.secret().getBytes(StandardCharsets.UTF_8)))
                .isFalse();
        assertThat(contains("xx".getBytes(StandardCharsets.UTF_8), "x".getBytes(StandardCharsets.UTF_8)))
                .isTrue();
        assertThat(key.digest()).isEqualTo(Security.derive(minted.secret(), key.salt()));
    }

    @Test
    void twoKeysWithTheSameSecretHaveDifferentDigests() {
        ApiKey.Issued first = issue("terminal tablet", Role.OPERATOR, LocalDate.of(2025, 12, 1), "aa11bb22cc33");
        ApiKey second = ApiKey.issue(
                        "office",
                        Role.MANAGER,
                        LocalDate.of(2024, 11, 1),
                        LocalDate.of(2025, 12, 1),
                        Optional.of(first.secret()),
                        Optional.of("dd44ee55ff66"))
                .key();
        assertThat(first.key().salt()).isNotEqualTo(second.salt());
        assertThat(first.key().digest()).isNotEqualTo(second.digest());
    }

    @Test
    void aKeyChecksItsOwnRecord() {
        assertThatThrownBy(() -> issue("terminal tablet", Role.OPERATOR, LocalDate.of(2025, 12, 1), "  "))
                .isInstanceOf(AuthError.class);
        assertThatThrownBy(() -> issue("   ", Role.OPERATOR, LocalDate.of(2025, 12, 1), "aa11bb22cc33"))
                .isInstanceOf(AuthError.class);
        assertThatThrownBy(() -> expiring(LocalDate.of(2024, 1, 1), "aa11bb22cc33"))
                .isInstanceOf(AuthError.class);
    }

    @Test
    void aKeyWithTooLittleSaltIsRefused() {
        ApiKey key = issue().key();
        assertThatThrownBy(() -> new ApiKey(
                        key.keyId(),
                        key.holder(),
                        key.role(),
                        "short".getBytes(StandardCharsets.US_ASCII),
                        key.digest(),
                        key.issued(),
                        key.expires()))
                .isInstanceOf(AuthError.class);
    }

    @Test
    void aKeyIsCurrentBetweenItsDates() {
        ApiKey key = issue().key();
        assertThat(key.isCurrent(ON)).isTrue();
        assertThat(key.isCurrent(LocalDate.of(2024, 1, 1))).isFalse();
        assertThat(key.isCurrent(LocalDate.of(2026, 1, 1))).isFalse();
        assertThat(key.daysLeft(ON)).isEqualTo(320);
    }

    @Test
    void aRingHandsBackAKeyById() {
        ApiKey key = issue().key();
        KeyRing ring = KeyRing.of(List.of(key));
        assertThat(ring.get(key.keyId())).isSameAs(key);
        assertThatThrownBy(() -> ring.get("nothing")).isInstanceOf(AuthError.class);
    }

    @Test
    void twoKeysCannotShareAnId() {
        ApiKey key = issue().key();
        assertThatThrownBy(() -> KeyRing.of(List.of(key, key))).isInstanceOf(AuthError.class);
    }

    @Test
    void aGoodPairAuthenticates() {
        ApiKey.Issued minted = issue();
        KeyRing ring = KeyRing.of(List.of(minted.key()));
        assertThat(ring.authenticate(minted.key().keyId() + "." + minted.secret(), ON))
                .isSameAs(minted.key());
    }

    @Test
    void aPairThatIsNotAPairIsRefused() {
        ApiKey.Issued minted = issue();
        KeyRing ring = KeyRing.of(List.of(minted.key()));
        assertThatThrownBy(() -> ring.authenticate(minted.secret(), ON)).isInstanceOf(AuthError.class);
        assertThatThrownBy(() -> ring.authenticate(minted.key().keyId() + "." + minted.secret() + ".extra", ON))
                .isInstanceOf(AuthError.class);
    }

    @Test
    void theWrongSecretIsRefused() {
        ApiKey key = issue().key();
        KeyRing ring = KeyRing.of(List.of(key));
        assertThatThrownBy(() -> ring.authenticate(key.keyId() + ".wrong", ON)).isInstanceOf(AuthError.class);
    }

    @Test
    void anIdThatIsNotOnTheRingIsRefused() {
        ApiKey.Issued minted = issue();
        KeyRing ring = KeyRing.of(List.of(minted.key()));
        assertThatThrownBy(() -> ring.authenticate("ffffffffffff." + minted.secret(), ON))
                .isInstanceOf(AuthError.class);
    }

    @Test
    void aLapsedKeyIsRefusedHoweverRightTheSecretIs() {
        ApiKey.Issued minted = expiring(LocalDate.of(2024, 12, 1), "aa11bb22cc33");
        KeyRing ring = KeyRing.of(List.of(minted.key()));
        assertThat(minted.key().matches(minted.secret())).isTrue();
        assertThatThrownBy(() -> ring.authenticate(minted.presented(), ON)).isInstanceOf(AuthError.class);
    }

    @Test
    void aRoleThatIsTooLowIsRefused() {
        ApiKey.Issued minted = issue("terminal tablet", Role.OPERATOR, LocalDate.of(2025, 12, 1), "aa11bb22cc33");
        KeyRing ring = KeyRing.of(List.of(minted.key()));
        String presented = minted.key().keyId() + "." + minted.secret();
        assertThat(ring.require(presented, ON, Role.READER)).isSameAs(minted.key());
        assertThat(ring.require(presented, ON, Role.OPERATOR)).isSameAs(minted.key());
        assertThatThrownBy(() -> ring.require(presented, ON, Role.MANAGER)).isInstanceOf(AuthError.class);
    }

    @Test
    void aRingListsItsHoldersOnceEach() {
        ApiKey first = issue("terminal tablet", Role.OPERATOR, LocalDate.of(2025, 12, 1), "aa11bb22cc33")
                .key();
        ApiKey second = ApiKey.issue(
                        "terminal tablet",
                        Role.OPERATOR,
                        LocalDate.of(2024, 11, 1),
                        LocalDate.of(2025, 12, 1),
                        Optional.empty(),
                        Optional.of("dd44ee55ff66"))
                .key();
        KeyRing ring = KeyRing.of(List.of(first, second));
        assertThat(ring.holders()).containsExactly("terminal tablet");
        assertThat(ring.size()).isEqualTo(2);
    }

    @Test
    void theKeysAboutToRunOutAreListed() {
        ApiKey soon = expiring(LocalDate.of(2025, 2, 1), "aa11bb22cc33").key();
        ApiKey later = expiring(LocalDate.of(2025, 12, 1), "dd44ee55ff66").key();
        KeyRing ring = KeyRing.of(List.of(soon, later));
        assertThat(ring.expiringWithin(ON, 30).stream().map(ApiKey::keyId).toList())
                .containsExactly("aa11bb22cc33");
    }

    @Test
    void addingAKeyMakesANewRing() {
        ApiKey key = issue().key();
        KeyRing ring = KeyRing.of();
        KeyRing longer = ring.withKey(key);
        assertThat(ring.size()).isZero();
        assertThat(longer.size()).isEqualTo(1);
    }

    @Test
    void aSecretShortEnoughToGuessIsRefusedAtIssue() {
        assertThatThrownBy(() -> ApiKey.issue(
                        "office",
                        Role.READER,
                        LocalDate.of(2024, 11, 1),
                        LocalDate.of(2025, 12, 1),
                        Optional.of("hunter2"),
                        Optional.empty()))
                .isInstanceOf(AuthError.class);
    }

    @Test
    void aMintedSecretClearsTheMinimumOnItsOwn() {
        assertThat(Security.newSecret().length()).isGreaterThanOrEqualTo(Security.MINIMUM_SECRET_LENGTH);
    }

    @Test
    void theMintedIdentifiersAreNotGuessable() {
        assertThat(Security.newSecret()).isNotEqualTo(Security.newSecret());
        assertThat(Security.newKeyId()).isNotEqualTo(Security.newKeyId());
        assertThat(Security.newKeyId()).hasSize(12);
    }

    @Test
    void aKeyPrintsWithoutItsSecret() {
        ApiKey key = issue().key();
        String printed = key.toString();
        assertThat(printed).doesNotContain(KNOWN_SECRET);
        assertThat(printed).startsWith("aa11bb22cc33 (");
    }
}
