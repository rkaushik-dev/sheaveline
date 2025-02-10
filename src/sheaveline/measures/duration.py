"""Durations, held as whole seconds.

A ropeway is timed in three places and they want three different faces on the
same quantity. Ride time and evacuation time are read as minutes and seconds,
hour meters and inspection intervals are read as hours, and a stop is logged
against the wall clock. Whole seconds carries all three, and nothing the
department measures is finer than a second.
"""

from __future__ import annotations

import re
from decimal import Decimal
from typing import Final

from sheaveline.errors import ParseError, RangeError

SECONDS_PER_MINUTE: Final = 60
SECONDS_PER_HOUR: Final = 3600

_CLOCK = re.compile(r"^\s*(?:(?P<h>\d+):)?(?P<m>\d{1,2}):(?P<s>\d{2})\s*$")
_UNIT = re.compile(
    r"""^\s*(?P<value>\d+(?:\.\d+)?)\s*
    (?P<unit>s|sec|secs|second|seconds|m|min|mins|minute|minutes|h|hr|hrs|hour|hours)\s*$""",
    re.VERBOSE | re.IGNORECASE,
)

_UNIT_SECONDS: Final[dict[str, int]] = {
    "s": 1,
    "sec": 1,
    "secs": 1,
    "second": 1,
    "seconds": 1,
    "m": SECONDS_PER_MINUTE,
    "min": SECONDS_PER_MINUTE,
    "mins": SECONDS_PER_MINUTE,
    "minute": SECONDS_PER_MINUTE,
    "minutes": SECONDS_PER_MINUTE,
    "h": SECONDS_PER_HOUR,
    "hr": SECONDS_PER_HOUR,
    "hrs": SECONDS_PER_HOUR,
    "hour": SECONDS_PER_HOUR,
    "hours": SECONDS_PER_HOUR,
}


class Duration:
    """An elapsed time in whole seconds. Never negative."""

    __slots__ = ("_seconds",)

    def __init__(self, seconds: int) -> None:
        if not isinstance(seconds, int) or isinstance(seconds, bool):
            raise RangeError("duration", seconds, "a whole number of seconds", "")
        if seconds < 0:
            raise RangeError("duration", seconds, 0, "no limit")
        self._seconds = seconds

    @property
    def seconds(self) -> int:
        return self._seconds

    @property
    def minutes(self) -> Decimal:
        return Decimal(self._seconds) / SECONDS_PER_MINUTE

    @property
    def hours(self) -> Decimal:
        return Decimal(self._seconds) / SECONDS_PER_HOUR

    @classmethod
    def from_seconds(cls, seconds: int) -> Duration:
        return cls(seconds)

    @classmethod
    def from_minutes(cls, minutes: int | float | str | Decimal) -> Duration:
        return cls(_whole(Decimal(str(minutes)) * SECONDS_PER_MINUTE, "minutes"))

    @classmethod
    def from_hours(cls, hours: int | float | str | Decimal) -> Duration:
        return cls(_whole(Decimal(str(hours)) * SECONDS_PER_HOUR, "hours"))

    @classmethod
    def from_hms(cls, hours: int = 0, minutes: int = 0, seconds: int = 0) -> Duration:
        return cls(hours * SECONDS_PER_HOUR + minutes * SECONDS_PER_MINUTE + seconds)

    @classmethod
    def parse(cls, text: str) -> Duration:
        """Read 8:30, 1:08:30, 90 s, 12 min or 2.5 hours."""
        clock = _CLOCK.match(text)
        if clock is not None:
            hours = int(clock.group("h") or 0)
            minutes = int(clock.group("m"))
            seconds = int(clock.group("s"))
            if seconds > 59 or (clock.group("h") is not None and minutes > 59):
                raise ParseError(text, "a duration")
            return cls.from_hms(hours, minutes, seconds)
        unit = _UNIT.match(text)
        if unit is None:
            raise ParseError(text, "a duration")
        factor = _UNIT_SECONDS[unit.group("unit").lower()]
        return cls(_whole(Decimal(unit.group("value")) * factor, "duration"))

    def split_hms(self) -> tuple[int, int, int]:
        hours, rest = divmod(self._seconds, SECONDS_PER_HOUR)
        minutes, seconds = divmod(rest, SECONDS_PER_MINUTE)
        return hours, minutes, seconds

    def format_clock(self) -> str:
        """Minutes and seconds, which is how a ride time is written on the sign."""
        minutes, seconds = divmod(self._seconds, SECONDS_PER_MINUTE)
        return f"{minutes}:{seconds:02d}"

    def format_hms(self) -> str:
        hours, minutes, seconds = self.split_hms()
        return f"{hours}:{minutes:02d}:{seconds:02d}"

    def __repr__(self) -> str:
        return f"Duration.from_seconds({self._seconds})"

    def __str__(self) -> str:
        return self.format_hms()

    def __eq__(self, other: object) -> bool:
        if not isinstance(other, Duration):
            return NotImplemented
        return self._seconds == other._seconds

    def __hash__(self) -> int:
        return hash(("Duration", self._seconds))

    def __lt__(self, other: Duration) -> bool:
        return self._seconds < other._seconds

    def __le__(self, other: Duration) -> bool:
        return self._seconds <= other._seconds

    def __gt__(self, other: Duration) -> bool:
        return self._seconds > other._seconds

    def __ge__(self, other: Duration) -> bool:
        return self._seconds >= other._seconds

    def __add__(self, other: Duration) -> Duration:
        if not isinstance(other, Duration):
            return NotImplemented
        return Duration(self._seconds + other._seconds)

    def __sub__(self, other: Duration) -> Duration:
        if not isinstance(other, Duration):
            return NotImplemented
        return Duration(self._seconds - other._seconds)

    def scaled(self, factor: int | float | str | Decimal) -> Duration:
        return Duration(_whole_rounded(Decimal(self._seconds) * Decimal(str(factor))))

    def is_zero(self) -> bool:
        return self._seconds == 0


ZERO_DURATION: Final = Duration(0)


def _whole(value: Decimal, what: str) -> int:
    if value != value.to_integral_value():
        raise RangeError(what, value, "a whole number of seconds", "")
    return int(value)


def _whole_rounded(value: Decimal) -> int:
    return int((value + Decimal("0.5")).to_integral_value(rounding="ROUND_FLOOR"))
