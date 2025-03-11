"""Operating hours, held as minutes since midnight.

Three times matter on a lift and the sign at the bottom only prints two of
them. First load is when the public may get on. Last load is when the attendant
stops loading. Sweep is when the last carrier has to be off the line, and it is
the one the department is actually judged on, because a lift still turning after
sweep is a lift the mountain manager has to explain.

Hours are set per day type, not per date, so the holiday schedule is one row
rather than fourteen.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from datetime import date, time
from typing import Final

from sheaveline.errors import ParseError, RangeError
from sheaveline.measures.duration import Duration
from sheaveline.schedule.season import DayType, Season

MINUTES_PER_DAY: Final = 24 * 60

_CLOCK = re.compile(
    r"^\s*(?P<hour>\d{1,2}):(?P<minute>\d{2})\s*(?P<meridiem>am|pm)?\s*$",
    re.IGNORECASE,
)


class TimeOfDay:
    """A wall clock time in whole minutes since midnight."""

    __slots__ = ("_minutes",)

    def __init__(self, minutes: int) -> None:
        if not isinstance(minutes, int) or isinstance(minutes, bool):
            raise RangeError("time of day", minutes, 0, MINUTES_PER_DAY - 1)
        if not 0 <= minutes < MINUTES_PER_DAY:
            raise RangeError("time of day", minutes, 0, MINUTES_PER_DAY - 1)
        self._minutes = minutes

    @property
    def minutes(self) -> int:
        return self._minutes

    @property
    def hour(self) -> int:
        return self._minutes // 60

    @property
    def minute(self) -> int:
        return self._minutes % 60

    @classmethod
    def at(cls, hour: int, minute: int = 0) -> TimeOfDay:
        return cls(hour * 60 + minute)

    @classmethod
    def from_time(cls, value: time) -> TimeOfDay:
        return cls(value.hour * 60 + value.minute)

    @classmethod
    def parse(cls, text: str) -> TimeOfDay:
        """Read 8:30, 08:30 or 4:15 pm."""
        match = _CLOCK.match(text)
        if match is None:
            raise ParseError(text, "a time of day")
        hour = int(match.group("hour"))
        minute = int(match.group("minute"))
        meridiem = match.group("meridiem")
        if meridiem is not None:
            if not 1 <= hour <= 12:
                raise ParseError(text, "a time of day")
            hour = hour % 12 + (12 if meridiem.lower() == "pm" else 0)
        if hour > 23 or minute > 59:
            raise ParseError(text, "a time of day")
        return cls(hour * 60 + minute)

    def to_time(self) -> time:
        return time(self.hour, self.minute)

    def plus(self, elapsed: Duration) -> TimeOfDay:
        """Later the same day. A window that runs past midnight is not a thing here."""
        total = self._minutes + elapsed.seconds // 60
        if total >= MINUTES_PER_DAY:
            raise RangeError("time of day", total, 0, MINUTES_PER_DAY - 1)
        return TimeOfDay(total)

    def minus(self, elapsed: Duration) -> TimeOfDay:
        total = self._minutes - elapsed.seconds // 60
        if total < 0:
            raise RangeError("time of day", total, 0, MINUTES_PER_DAY - 1)
        return TimeOfDay(total)

    def until(self, later: TimeOfDay) -> Duration:
        if later < self:
            raise RangeError("interval", later, self, "later")
        return Duration.from_minutes(later._minutes - self._minutes)

    def format_clock(self) -> str:
        return f"{self.hour}:{self.minute:02d}"

    def format_meridiem(self) -> str:
        hour = self.hour % 12 or 12
        suffix = "am" if self.hour < 12 else "pm"
        return f"{hour}:{self.minute:02d} {suffix}"

    def __repr__(self) -> str:
        return f"TimeOfDay({self._minutes})"

    def __str__(self) -> str:
        return self.format_clock()

    def __eq__(self, other: object) -> bool:
        if not isinstance(other, TimeOfDay):
            return NotImplemented
        return self._minutes == other._minutes

    def __hash__(self) -> int:
        return hash(("TimeOfDay", self._minutes))

    def __lt__(self, other: TimeOfDay) -> bool:
        return self._minutes < other._minutes

    def __le__(self, other: TimeOfDay) -> bool:
        return self._minutes <= other._minutes

    def __gt__(self, other: TimeOfDay) -> bool:
        return self._minutes > other._minutes

    def __ge__(self, other: TimeOfDay) -> bool:
        return self._minutes >= other._minutes


@dataclass(frozen=True)
class OperatingWindow:
    """When a lift may load, and when it has to be swept."""

    first_load: TimeOfDay
    last_load: TimeOfDay
    sweep_by: TimeOfDay

    def __post_init__(self) -> None:
        if self.last_load < self.first_load:
            raise RangeError("last load", self.last_load, self.first_load, "later")
        if self.sweep_by < self.last_load:
            raise RangeError("sweep", self.sweep_by, self.last_load, "later")

    @classmethod
    def parse(cls, first: str, last: str, sweep: str) -> OperatingWindow:
        return cls(TimeOfDay.parse(first), TimeOfDay.parse(last), TimeOfDay.parse(sweep))

    def is_loading(self, moment: TimeOfDay) -> bool:
        return self.first_load <= moment <= self.last_load

    def is_running(self, moment: TimeOfDay) -> bool:
        return self.first_load <= moment <= self.sweep_by

    def loading_time(self) -> Duration:
        return self.first_load.until(self.last_load)

    def sweep_allowance(self) -> Duration:
        """How long the line has to clear after the last chair is loaded."""
        return self.last_load.until(self.sweep_by)

    def __str__(self) -> str:
        return f"{self.first_load} to {self.last_load}, swept by {self.sweep_by}"


@dataclass(frozen=True)
class HoursTable:
    """Operating windows by day type, with one fallback for anything unlisted."""

    default: OperatingWindow
    by_day_type: dict[DayType, OperatingWindow]

    def window_for_type(self, day_type: DayType) -> OperatingWindow | None:
        if not day_type.is_operating:
            return None
        return self.by_day_type.get(day_type, self.default)

    def window_on(self, season: Season, day: date) -> OperatingWindow | None:
        """The window that applies to a date, or nothing on a closed day."""
        return self.window_for_type(season.day_type(day))
