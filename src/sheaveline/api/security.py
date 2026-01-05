"""API keys and what each one is allowed to do.

The clients here are a tablet at each terminal, the office machine and, once a
season, the board's auditor. They are not people typing passwords, so the token
is a key with a role on it, and the role decides what the key may do rather than
who holds it.

Two things are worth stating because they are easy to get wrong. The secret is
never stored, only a derived hash with its own salt, so a copy of the database
does not hand somebody the keys. And the comparison is constant time, because a
comparison that returns early tells an attacker how much of the secret was
right.
"""

from __future__ import annotations

import hashlib
import hmac
import secrets
from collections.abc import Sequence
from dataclasses import dataclass
from datetime import date
from enum import Enum
from typing import Final

from sheaveline.errors import SheavelineError

ITERATIONS: Final = 240_000
SALT_BYTES: Final = 16
SECRET_BYTES: Final = 32
KEY_ID_LENGTH: Final = 12


class AuthError(SheavelineError):
    """A key was presented that will not do."""


class Role(Enum):
    """What a key is for."""

    READER = "reader"
    OPERATOR = "operator"
    MECHANIC = "mechanic"
    MANAGER = "manager"

    @property
    def rank(self) -> int:
        return _RANK[self]

    def covers(self, wanted: Role) -> bool:
        """A manager key does anything an operator key does, and so on down."""
        return self.rank >= wanted.rank


_RANK: Final[dict[Role, int]] = {
    Role.READER: 0,
    Role.OPERATOR: 1,
    Role.MECHANIC: 2,
    Role.MANAGER: 3,
}


def new_secret() -> str:
    return secrets.token_urlsafe(SECRET_BYTES)


def new_key_id() -> str:
    return secrets.token_hex(KEY_ID_LENGTH // 2)


def derive(secret: str, salt: bytes) -> bytes:
    return hashlib.pbkdf2_hmac("sha256", secret.encode("utf-8"), salt, ITERATIONS)


@dataclass(frozen=True)
class ApiKey:
    """One key, its role and the hash of the secret that goes with it."""

    key_id: str
    holder: str
    role: Role
    salt: bytes
    digest: bytes
    issued: date
    expires: date

    def __post_init__(self) -> None:
        if not self.key_id.strip():
            raise AuthError("a key has an id")
        if not self.holder.strip():
            raise AuthError(f"key {self.key_id} has nobody holding it")
        if self.expires <= self.issued:
            raise AuthError(f"key {self.key_id} expires before it was issued")
        if len(self.salt) < SALT_BYTES:
            raise AuthError(f"key {self.key_id} has too little salt")

    @classmethod
    def issue(
        cls,
        holder: str,
        role: Role,
        issued: date,
        expires: date,
        secret: str | None = None,
        key_id: str | None = None,
    ) -> tuple[ApiKey, str]:
        """Mint a key. The secret is handed back once and never stored."""
        raw = secret or new_secret()
        salt = secrets.token_bytes(SALT_BYTES)
        key = cls(
            key_id=key_id or new_key_id(),
            holder=holder,
            role=role,
            salt=salt,
            digest=derive(raw, salt),
            issued=issued,
            expires=expires,
        )
        return key, raw

    def matches(self, secret: str) -> bool:
        return hmac.compare_digest(self.digest, derive(secret, self.salt))

    def is_current(self, on: date) -> bool:
        return self.issued <= on <= self.expires

    def may(self, wanted: Role) -> bool:
        return self.role.covers(wanted)

    def days_left(self, on: date) -> int:
        return (self.expires - on).days

    def __str__(self) -> str:
        return f"{self.key_id} ({self.holder}, {self.role.value})"


@dataclass(frozen=True)
class KeyRing:
    """Every key the department has issued."""

    keys: tuple[ApiKey, ...] = ()

    def __post_init__(self) -> None:
        ids = [key.key_id for key in self.keys]
        if len(set(ids)) != len(ids):
            raise AuthError("two keys share an id")

    @classmethod
    def of(cls, keys: Sequence[ApiKey] = ()) -> KeyRing:
        return cls(tuple(keys))

    def get(self, key_id: str) -> ApiKey:
        for key in self.keys:
            if key.key_id == key_id:
                return key
        raise AuthError("that key is not on the ring")

    def authenticate(self, presented: str, on: date) -> ApiKey:
        """Read an id.secret pair and hand back the key, or refuse."""
        if presented.count(".") != 1:
            raise AuthError("a key reads as an id and a secret separated by a dot")
        key_id, secret = presented.split(".", 1)
        try:
            key = self.get(key_id)
        except AuthError:
            # Do the work anyway so a missing id and a wrong secret take the same time.
            derive(secret, b"0" * SALT_BYTES)
            raise
        if not key.matches(secret):
            raise AuthError("that secret does not match that key")
        if not key.is_current(on):
            raise AuthError(f"key {key_id} is not current")
        return key

    def require(self, presented: str, on: date, wanted: Role) -> ApiKey:
        key = self.authenticate(presented, on)
        if not key.may(wanted):
            raise AuthError(f"a {wanted.value} key is wanted and that one is a {key.role.value}")
        return key

    def with_key(self, key: ApiKey) -> KeyRing:
        return KeyRing.of([*self.keys, key])

    def holders(self) -> tuple[str, ...]:
        return tuple(sorted({key.holder for key in self.keys}))

    def expiring_within(self, on: date, days: int) -> tuple[ApiKey, ...]:
        return tuple(key for key in self.keys if key.is_current(on) and key.days_left(on) <= days)

    def __len__(self) -> int:
        return len(self.keys)
