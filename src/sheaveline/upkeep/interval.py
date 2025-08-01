"""Intervals, and the awkward fact that a job can be due on two clocks at once.

Some jobs come round on the calendar. The annual inspection is annual whether
the lift ran three hundred days or thirty. Some come round on the hour meter,
because a gearbox does not care what month it is. And a few carry both, in which
case whichever arrives first is the one that matters, and the other one resets
with it.

Grace is real and it is not slack. A monthly job with seven days of grace is not
a job due every thirty seven days: the next one is still counted from when the
last one was due, not from when it was finally done, or a machine drifts a week
later every month until the annual lands in July.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, timedelta
from decimal import Decimal
from enum import Enum
from typing import Final

from sheaveline.errors import SheavelineError
from sheaveline.plant.classes import LiftClass


class UpkeepError(SheavelineError):
    """An interval or a record was described in a way that cannot hold."""


class Basis(Enum):
    """Which clock a job runs on."""

    CALENDAR = "calendar"
    HOURS = "running hours"
    CYCLES = "cycles"


@dataclass(frozen=True)
class Interval:
    """How often a job comes round, on whichever clocks apply to it."""

    days: int | None = None
    hours: int | None = None
    cycles: int | None = None
    grace_days: int = 0

    def __post_init__(self) -> None:
        if self.days is None and self.hours is None and self.cycles is None:
            raise UpkeepError("an interval runs on at least one clock")
        for name, value in (("days", self.days), ("hours", self.hours), ("cycles", self.cycles)):
            if value is not None and value < 1:
                raise UpkeepError(f"an interval of {value} {name} is not an interval")
        if self.grace_days < 0:
            raise UpkeepError("grace is not negative")

    @classmethod
    def daily(cls) -> Interval:
        return cls(days=1)

    @classmethod
    def weekly(cls, grace_days: int = 2) -> Interval:
        return cls(days=7, grace_days=grace_days)

    @classmethod
    def monthly(cls, grace_days: int = 7) -> Interval:
        return cls(days=30, grace_days=grace_days)

    @classmethod
    def annual(cls, grace_days: int = 30) -> Interval:
        return cls(days=365, grace_days=grace_days)

    @classmethod
    def five_yearly(cls, grace_days: int = 60) -> Interval:
        return cls(days=1826, grace_days=grace_days)

    def bases(self) -> frozenset[Basis]:
        found = set()
        if self.days is not None:
            found.add(Basis.CALENDAR)
        if self.hours is not None:
            found.add(Basis.HOURS)
        if self.cycles is not None:
            found.add(Basis.CYCLES)
        return frozenset(found)

    def runs_on(self, basis: Basis) -> bool:
        return basis in self.bases()

    def due_date(self, last_due: date) -> date | None:
        """Counted from when the last one was due, not from when it was done."""
        if self.days is None:
            return None
        return last_due + timedelta(days=self.days)

    def last_day_of_grace(self, last_due: date) -> date | None:
        due = self.due_date(last_due)
        if due is None:
            return None
        return due + timedelta(days=self.grace_days)

    def due_hours(self, hours_at_last: Decimal) -> Decimal | None:
        if self.hours is None:
            return None
        return hours_at_last + self.hours

    def due_cycles(self, cycles_at_last: int) -> int | None:
        if self.cycles is None:
            return None
        return cycles_at_last + self.cycles

    def __str__(self) -> str:
        parts = []
        if self.days is not None:
            parts.append(f"every {self.days} days")
        if self.hours is not None:
            parts.append(f"every {self.hours} hours")
        if self.cycles is not None:
            parts.append(f"every {self.cycles:,} cycles")
        return " or ".join(parts)


@dataclass(frozen=True)
class Requirement:
    """One job that has to be done to a machine on an interval."""

    code: str
    description: str
    interval: Interval
    critical: bool = True
    classes: frozenset[LiftClass] = frozenset(LiftClass)

    def __post_init__(self) -> None:
        if not self.code.strip():
            raise UpkeepError("a requirement has a code")
        if not self.description.strip():
            raise UpkeepError(f"{self.code} has no description")

    def applies_to(self, lift_class: LiftClass) -> bool:
        return lift_class in self.classes

    def __str__(self) -> str:
        return f"{self.code} {self.description}, {self.interval}"


AERIAL: Final = frozenset(cls for cls in LiftClass if cls.is_aerial)
DETACHABLES: Final = frozenset(cls for cls in LiftClass if cls.is_detachable)
