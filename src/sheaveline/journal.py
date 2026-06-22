"""What the software did, which is a different book from what the machines did.

The word log is taken. On this hill a log is the day book an operator writes in,
and calling the application's own output a log would guarantee that somebody
eventually greps the wrong one during an incident. So this is the journal.

One line a record and the fields in a fixed order, because the thing that reads
it is usually grep on somebody's laptop at nine at night and only occasionally a
collector. Fields go on as key=value with the value quoted if it has a space in
it, which is enough structure to cut with and not so much that it stops being
readable.

Nothing here reaches for a global logger by module name. The journal is built by
whoever is running, handed down, and can be switched off entirely, which is what
the tests want and what a command line program that prints reports to stdout
needs, because a stray log line in the middle of an eighty column report is a
report nobody can parse.
"""

from __future__ import annotations

import sys
import time
import uuid
from collections.abc import Iterator, Mapping
from contextlib import contextmanager
from dataclasses import dataclass
from enum import IntEnum
from typing import TextIO


class Level(IntEnum):
    """How much of it you want. Ordered so a comparison reads the right way."""

    DEBUG = 10
    INFO = 20
    WARN = 30
    ERROR = 40
    OFF = 100

    @classmethod
    def named(cls, name: str) -> Level:
        try:
            return cls[name.strip().upper()]
        except KeyError as wrong:
            wanted = ", ".join(one.name.lower() for one in cls)
            raise ValueError(f"no such level {name}, wanted one of {wanted}") from wrong


def field(name: str, value: object) -> str:
    """One key and its value, quoted only when it needs to be."""
    text = "" if value is None else str(value)
    if text == "" or any(one.isspace() for one in text) or '"' in text:
        return f'{name}="{text.replace(chr(92), chr(92) * 2).replace(chr(34), chr(92) + chr(34))}"'
    return f"{name}={text}"


@dataclass
class Journal:
    """Where the application writes down what it did."""

    stream: TextIO
    level: Level = Level.INFO
    clock: str = ""
    context: Mapping[str, object] | None = None

    @classmethod
    def to_stderr(cls, level: Level = Level.INFO) -> Journal:
        return cls(sys.stderr, level)

    @classmethod
    def silent(cls) -> Journal:
        """A journal that writes nothing, which is what a report command wants."""
        return cls(sys.stderr, Level.OFF)

    def wants(self, level: Level) -> bool:
        return level >= self.level and self.level is not Level.OFF

    def about(self, **context: object) -> Journal:
        """The same journal with more on every line it writes from here on."""
        carried = dict(self.context or {})
        carried.update(context)
        return Journal(self.stream, self.level, self.clock, carried)

    def _stamp(self) -> str:
        return self.clock or time.strftime("%Y-%m-%dT%H:%M:%S")

    def write(self, level: Level, event: str, **fields: object) -> None:
        if not self.wants(level):
            return
        parts = [self._stamp(), level.name.lower(), event]
        for name, value in (self.context or {}).items():
            parts.append(field(name, value))
        for name, value in fields.items():
            parts.append(field(name, value))
        self.stream.write(" ".join(parts) + "\n")
        self.stream.flush()

    def debug(self, event: str, **fields: object) -> None:
        self.write(Level.DEBUG, event, **fields)

    def info(self, event: str, **fields: object) -> None:
        self.write(Level.INFO, event, **fields)

    def warn(self, event: str, **fields: object) -> None:
        self.write(Level.WARN, event, **fields)

    def error(self, event: str, **fields: object) -> None:
        self.write(Level.ERROR, event, **fields)

    @contextmanager
    def timing(self, event: str, **fields: object) -> Iterator[None]:
        """Write the line when the work is done, with how long it took on it."""
        started = time.monotonic()
        try:
            yield
        except Exception as went_wrong:
            self.error(
                event,
                ms=round((time.monotonic() - started) * 1000),
                outcome="failed",
                error=type(went_wrong).__name__,
                **fields,
            )
            raise
        self.info(event, ms=round((time.monotonic() - started) * 1000), outcome="ok", **fields)


def new_request_id() -> str:
    """Short enough to read out over the radio, long enough not to collide."""
    return uuid.uuid4().hex[:12]
