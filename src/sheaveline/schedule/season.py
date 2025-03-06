"""The operating season, and what kind of day any given date is.

The department does not get to decide when it runs. Marketing publishes a season
with an opening day, a closing day, a handful of holiday periods and a spring
schedule, plus the odd scheduled closure for a race or a wind day the mountain
manager called in advance. Every other rule in here reads the day type, so the
precedence between them is written down once and only once.

Precedence, highest first. A day outside the season or on the closure list is
closed and nothing else matters. Then a preview day, which the machines run for
but the public does not ride. Then a holiday period, which beats a weekend
because the crew size is set by the holiday and not by the calendar. Then
spring, which is a different staffing model again. Then the weekend. Whatever
is left is midweek.
"""

from __future__ import annotations

from collections.abc import Iterator
from dataclasses import dataclass, field
from datetime import date, timedelta
from enum import Enum

from sheaveline.errors import SheavelineError


class SeasonError(SheavelineError):
    """A season was described in a way that cannot hold."""


class DayType(Enum):
    """What kind of operating day a date is."""

    CLOSED = "closed"
    PREVIEW = "preview"
    HOLIDAY = "holiday"
    SPRING = "spring"
    WEEKEND = "weekend"
    MIDWEEK = "midweek"

    @property
    def is_operating(self) -> bool:
        """The machines run and the crew is on the hill."""
        return self is not DayType.CLOSED

    @property
    def is_public(self) -> bool:
        """The public may ride."""
        return self.is_operating and self is not DayType.PREVIEW


@dataclass(frozen=True)
class HolidayPeriod:
    """A named block of days the resort staffs as a holiday."""

    name: str
    first: date
    last: date

    def __post_init__(self) -> None:
        if self.last < self.first:
            raise SeasonError(f"holiday {self.name} ends before it starts")

    def covers(self, day: date) -> bool:
        return self.first <= day <= self.last

    def days(self) -> int:
        return (self.last - self.first).days + 1


@dataclass(frozen=True)
class Season:
    """One winter, from the first preview day to the last day the lifts turn."""

    label: str
    opens: date
    closes: date
    preview_from: date | None = None
    spring_from: date | None = None
    holidays: tuple[HolidayPeriod, ...] = ()
    closures: frozenset[date] = field(default_factory=frozenset)

    def __post_init__(self) -> None:
        if self.closes < self.opens:
            raise SeasonError(f"season {self.label} closes before it opens")
        if self.preview_from is not None and self.preview_from > self.opens:
            raise SeasonError(f"season {self.label} previews after it opens")
        if self.spring_from is not None and not self.opens <= self.spring_from <= self.closes:
            raise SeasonError(f"season {self.label} starts spring outside the season")
        for holiday in self.holidays:
            if holiday.last < self.first_day() or holiday.first > self.closes:
                raise SeasonError(f"holiday {holiday.name} falls outside {self.label}")

    def first_day(self) -> date:
        return self.preview_from or self.opens

    def covers(self, day: date) -> bool:
        return self.first_day() <= day <= self.closes

    def holiday_on(self, day: date) -> HolidayPeriod | None:
        for holiday in self.holidays:
            if holiday.covers(day):
                return holiday
        return None

    def day_type(self, day: date) -> DayType:
        """The one place the precedence between day kinds is decided."""
        if not self.covers(day) or day in self.closures:
            return DayType.CLOSED
        if day < self.opens:
            return DayType.PREVIEW
        if self.holiday_on(day) is not None:
            return DayType.HOLIDAY
        if self.spring_from is not None and day >= self.spring_from:
            return DayType.SPRING
        if day.weekday() >= 5:
            return DayType.WEEKEND
        return DayType.MIDWEEK

    def is_operating(self, day: date) -> bool:
        return self.day_type(day).is_operating

    def is_public(self, day: date) -> bool:
        return self.day_type(day).is_public

    def days(self) -> Iterator[date]:
        day = self.first_day()
        while day <= self.closes:
            yield day
            day += timedelta(days=1)

    def operating_days(self) -> Iterator[date]:
        return (day for day in self.days() if self.is_operating(day))

    def public_days(self) -> Iterator[date]:
        return (day for day in self.days() if self.is_public(day))

    def day_number(self, day: date) -> int:
        """Which operating day of the season this is, counting from one."""
        if not self.is_operating(day):
            raise SeasonError(f"{day.isoformat()} is not an operating day of {self.label}")
        return sum(1 for each in self.operating_days() if each <= day)

    def days_remaining(self, day: date) -> int:
        """Operating days left after this one, which the crew plan reads."""
        return sum(1 for each in self.operating_days() if each > day)

    def length(self) -> int:
        return sum(1 for _ in self.operating_days())

    def __str__(self) -> str:
        return f"{self.label} ({self.opens.isoformat()} to {self.closes.isoformat()})"
