"""One key ring for the whole suite, minted once.

Deriving a key is meant to be slow. Two hundred and forty thousand rounds is the
point of it, and it is the reason a key is worth having. What it is not worth
doing is four of them in the setup of every route test, which is where this
suite was spending most of a minute.

So the ring is built at import and shared. Nothing in the suite mutates it, and
the two tests that genuinely need a different ring build their own.
"""

from datetime import date

from fastapi.testclient import TestClient

from sheaveline.api.app import Settings, build_app
from sheaveline.api.security import ApiKey, KeyRing, Role

TODAY = date(2025, 1, 15)
ISSUED = date(2024, 11, 1)
EXPIRES = date(2025, 12, 1)


def _mint() -> tuple[KeyRing, dict[Role, str]]:
    keys = []
    secrets: dict[Role, str] = {}
    for index, role in enumerate(Role):
        key, secret = ApiKey.issue(
            holder=f"{role.value} client",
            role=role,
            issued=ISSUED,
            expires=EXPIRES,
            key_id=f"{index:012d}",
        )
        secrets[role] = f"{key.key_id}.{secret}"
        keys.append(key)
    return KeyRing.of(keys), secrets


RING, SECRETS = _mint()


def headers(role: Role = Role.READER) -> dict[str, str]:
    """What a client puts on the request to be allowed to make it."""
    return {"x-api-key": SECRETS[role]}


def settings(today: date = TODAY, resort: str = "Ogden Peak") -> Settings:
    return Settings.in_memory(keys=RING, today=lambda: today, resort=resort)


def client_for(built: Settings) -> TestClient:
    """A client that hands the error body back rather than raising through it."""
    return TestClient(build_app(built), raise_server_exceptions=False)
