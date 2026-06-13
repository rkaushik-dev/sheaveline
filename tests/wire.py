"""One key ring and one application for the whole suite, both built once.

Two things in the http tests were being paid for over and over. The first was
the key ring: minting four keys in the setup of every route test cost most of a
minute across the suite, and none of those tests cares which four keys they are.

The second was the application. FastAPI does real work when a router is
included, and building it takes about a fifth of a second, which across a
hundred and something route and command tests is most of another minute. The
running copy keeps everything it needs on app.state.settings and every dependency
reads it off the request, so a test can hand the same application a different
database by putting different settings there. That is exactly what the factory
was written for, and it is why nothing in this package reaches for a global.

The two tests that genuinely need a different key ring still build their own.
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


APP = build_app(Settings.in_memory(keys=RING))


def client_for(built: Settings) -> TestClient:
    """A client that hands the error body back rather than raising through it.

    The settings go on the application the same way the factory puts them there.
    Every dependency reads them off the request, so the database this client
    talks to is the one the caller just built and nothing leaks between tests.
    """
    APP.state.settings = built
    return TestClient(APP, raise_server_exceptions=False)


def fresh_client(built: Settings) -> TestClient:
    """A client on its own application, for the tests that are about the factory."""
    return TestClient(build_app(built), raise_server_exceptions=False)
