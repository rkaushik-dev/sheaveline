from datetime import date

import pytest

from sheaveline.api.security import (
    ApiKey,
    AuthError,
    KeyRing,
    Role,
    derive,
    new_key_id,
    new_secret,
)

ON = date(2025, 1, 15)


def issue(
    holder="terminal tablet", role=Role.OPERATOR, expires=date(2025, 12, 1), key_id="aa11bb22cc33"
):
    return ApiKey.issue(
        holder=holder,
        role=role,
        issued=date(2024, 11, 1),
        expires=expires,
        secret="a-known-secret-for-the-test",
        key_id=key_id,
    )


def test_a_manager_key_covers_everything_under_it():
    assert Role.MANAGER.covers(Role.OPERATOR)
    assert Role.MANAGER.covers(Role.READER)
    assert Role.MECHANIC.covers(Role.OPERATOR)
    assert not Role.OPERATOR.covers(Role.MECHANIC)
    assert Role.READER.covers(Role.READER)


def test_a_key_is_minted_with_its_secret_handed_back_once():
    key, secret = issue()
    assert secret == "a-known-secret-for-the-test"
    assert key.matches(secret)
    assert not key.matches("something else")


def test_the_secret_is_never_stored():
    key, secret = issue()
    assert secret.encode() not in key.digest
    assert key.digest == derive(secret, key.salt)


def test_two_keys_with_the_same_secret_have_different_digests():
    first, secret = issue(key_id="aa11bb22cc33")
    second, _ = ApiKey.issue(
        holder="office",
        role=Role.MANAGER,
        issued=date(2024, 11, 1),
        expires=date(2025, 12, 1),
        secret=secret,
        key_id="dd44ee55ff66",
    )
    assert first.salt != second.salt
    assert first.digest != second.digest


def test_a_key_checks_its_own_record():
    with pytest.raises(AuthError):
        issue(key_id="  ")
    with pytest.raises(AuthError):
        issue(holder="   ")
    with pytest.raises(AuthError):
        issue(expires=date(2024, 1, 1))


def test_a_key_with_too_little_salt_is_refused():
    key, _ = issue()
    with pytest.raises(AuthError):
        ApiKey(
            key_id=key.key_id,
            holder=key.holder,
            role=key.role,
            salt=b"short",
            digest=key.digest,
            issued=key.issued,
            expires=key.expires,
        )


def test_a_key_is_current_between_its_dates():
    key, _ = issue()
    assert key.is_current(ON)
    assert not key.is_current(date(2024, 1, 1))
    assert not key.is_current(date(2026, 1, 1))
    assert key.days_left(ON) == 320


def test_a_ring_hands_back_a_key_by_id():
    key, _ = issue()
    ring = KeyRing.of([key])
    assert ring.get(key.key_id) is key
    with pytest.raises(AuthError):
        ring.get("nothing")


def test_two_keys_cannot_share_an_id():
    key, _ = issue()
    with pytest.raises(AuthError):
        KeyRing.of([key, key])


def test_a_good_pair_authenticates():
    key, secret = issue()
    ring = KeyRing.of([key])
    assert ring.authenticate(f"{key.key_id}.{secret}", ON) is key


def test_a_pair_that_is_not_a_pair_is_refused():
    key, secret = issue()
    ring = KeyRing.of([key])
    with pytest.raises(AuthError):
        ring.authenticate(secret, ON)
    with pytest.raises(AuthError):
        ring.authenticate(f"{key.key_id}.{secret}.extra", ON)


def test_the_wrong_secret_is_refused():
    key, _ = issue()
    ring = KeyRing.of([key])
    with pytest.raises(AuthError):
        ring.authenticate(f"{key.key_id}.wrong", ON)


def test_an_id_that_is_not_on_the_ring_is_refused():
    key, secret = issue()
    ring = KeyRing.of([key])
    with pytest.raises(AuthError):
        ring.authenticate(f"ffffffffffff.{secret}", ON)


def test_a_lapsed_key_is_refused_however_right_the_secret_is():
    key, secret = issue(expires=date(2024, 12, 1))
    ring = KeyRing.of([key])
    assert key.matches(secret)
    with pytest.raises(AuthError):
        ring.authenticate(f"{key.key_id}.{secret}", ON)


def test_a_role_that_is_too_low_is_refused():
    key, secret = issue(role=Role.OPERATOR)
    ring = KeyRing.of([key])
    presented = f"{key.key_id}.{secret}"
    assert ring.require(presented, ON, Role.READER) is key
    assert ring.require(presented, ON, Role.OPERATOR) is key
    with pytest.raises(AuthError):
        ring.require(presented, ON, Role.MANAGER)


def test_a_ring_lists_its_holders_once_each():
    first, _ = issue(holder="terminal tablet", key_id="aa11bb22cc33")
    second, _ = ApiKey.issue(
        holder="terminal tablet",
        role=Role.OPERATOR,
        issued=date(2024, 11, 1),
        expires=date(2025, 12, 1),
        key_id="dd44ee55ff66",
    )
    ring = KeyRing.of([first, second])
    assert ring.holders() == ("terminal tablet",)
    assert len(ring) == 2


def test_the_keys_about_to_run_out_are_listed():
    soon, _ = issue(expires=date(2025, 2, 1), key_id="aa11bb22cc33")
    later, _ = issue(expires=date(2025, 12, 1), key_id="dd44ee55ff66")
    ring = KeyRing.of([soon, later])
    assert [key.key_id for key in ring.expiring_within(ON, 30)] == ["aa11bb22cc33"]


def test_adding_a_key_makes_a_new_ring():
    key, _ = issue()
    ring = KeyRing.of()
    longer = ring.with_key(key)
    assert len(ring) == 0
    assert len(longer) == 1


def test_the_minted_identifiers_are_not_guessable():
    assert new_secret() != new_secret()
    assert new_key_id() != new_key_id()
    assert len(new_key_id()) == 12


def test_a_key_prints_without_its_secret():
    key, _ = issue()
    printed = str(key)
    assert "a-known-secret" not in printed
    assert printed.startswith("aa11bb22cc33 (")
