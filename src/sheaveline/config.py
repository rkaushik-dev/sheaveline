"""What the office machine is told, and where it is told it.

The command line takes a database url and a journal level, which is right for
somebody at a keyboard and wrong for the box in the equipment room that starts
this thing on boot. That box wants its settings in the environment where they can
be set once and read by whatever supervises the process.

Everything here has a default that works on a laptop, so the program starts with
nothing set. The one thing with no default is the key file, because a server with
no keys refuses every request and doing that silently is worse than saying so.

The reading is strict on purpose. A journal level nobody has and a port that is
not a number are both configuration mistakes, and finding out at startup with a
sentence naming the variable is enormously better than finding out at three in
the afternoon with a stack trace.
"""

from __future__ import annotations

import os
from collections.abc import Mapping
from dataclasses import dataclass
from pathlib import Path
from typing import Final

from sheaveline.errors import SheavelineError
from sheaveline.journal import Level

PREFIX: Final = "SHEAVELINE_"
DATABASE: Final = f"{PREFIX}DATABASE_URL"
RESORT: Final = f"{PREFIX}RESORT"
JOURNAL: Final = f"{PREFIX}JOURNAL"
KEYS: Final = f"{PREFIX}KEY_FILE"
PORT: Final = f"{PREFIX}PORT"
HOST: Final = f"{PREFIX}HOST"

DEFAULT_DATABASE: Final = "sqlite+pysqlite:///sheaveline.sqlite3"
DEFAULT_RESORT: Final = "Ogden Peak"
DEFAULT_PORT: Final = 8451
DEFAULT_HOST: Final = "127.0.0.1"

EVERY_NAME: Final[tuple[str, ...]] = (DATABASE, RESORT, JOURNAL, KEYS, HOST, PORT)


class ConfigurationError(SheavelineError):
    """Something in the environment does not read as what it has to be."""


def _text(env: Mapping[str, str], name: str, fallback: str) -> str:
    found = env.get(name, "").strip()
    return found or fallback


def _port(env: Mapping[str, str]) -> int:
    found = env.get(PORT, "").strip()
    if not found:
        return DEFAULT_PORT
    try:
        wanted = int(found)
    except ValueError as wrong:
        raise ConfigurationError(f"{PORT} is {found!r}, which is not a port") from wrong
    if not 1 <= wanted <= 65535:
        raise ConfigurationError(f"{PORT} is {wanted}, which is not a port")
    return wanted


def _level(env: Mapping[str, str]) -> Level:
    found = env.get(JOURNAL, "").strip()
    if not found:
        return Level.INFO
    try:
        return Level.named(found)
    except ValueError as wrong:
        raise ConfigurationError(f"{JOURNAL}: {wrong}") from wrong


@dataclass(frozen=True)
class Configuration:
    """One running copy's settings, read once at startup."""

    database: str = DEFAULT_DATABASE
    resort: str = DEFAULT_RESORT
    journal: Level = Level.INFO
    key_file: Path | None = None
    host: str = DEFAULT_HOST
    port: int = DEFAULT_PORT
    # Whether the journal level was actually named, which is not the same
    # question as what it is. The server wants a journal by default and the
    # command line does not, and neither of them can decide that from a Level
    # that has already been defaulted. Without this the settings command reports
    # off whatever the environment says, which is a settings command that lies.
    journal_given: bool = False

    @classmethod
    def from_environment(cls, env: Mapping[str, str] | None = None) -> Configuration:
        """Read it, and say which variable is wrong rather than which line broke."""
        found = os.environ if env is None else env
        keys = found.get(KEYS, "").strip()
        return cls(
            database=_text(found, DATABASE, DEFAULT_DATABASE),
            resort=_text(found, RESORT, DEFAULT_RESORT),
            journal=_level(found),
            key_file=Path(keys) if keys else None,
            host=_text(found, HOST, DEFAULT_HOST),
            port=_port(found),
            journal_given=bool(found.get(JOURNAL, "").strip()),
        )

    def is_in_memory(self) -> bool:
        return ":memory:" in self.database

    def serves_keys(self) -> bool:
        return self.key_file is not None

    def require_keys(self) -> Path:
        if self.key_file is None:
            raise ConfigurationError(
                f"{KEYS} is not set, and a server with no keys refuses every request"
            )
        if not self.key_file.exists():
            raise ConfigurationError(f"{KEYS} names {self.key_file}, which is not there")
        return self.key_file

    def described(self) -> tuple[tuple[str, str], ...]:
        """What was read, for the startup line. Nothing here is a secret."""
        return (
            (DATABASE, self.database),
            (RESORT, self.resort),
            (JOURNAL, self.journal.name.lower()),
            (KEYS, "not set" if self.key_file is None else str(self.key_file)),
            (HOST, self.host),
            (PORT, str(self.port)),
        )

    def __str__(self) -> str:
        return f"{self.resort} on {self.host}:{self.port}, records in {self.database}"
