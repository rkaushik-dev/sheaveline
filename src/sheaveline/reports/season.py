"""The season summary, which is the page the board reads in May.

It is the same handful of numbers asked of every operating day: how many days
did each machine run, how much of that was it stopped, and whose fault was the
stopping. The board compares this year against last, so the arithmetic has to be
the same arithmetic every year, which is why it is here rather than in a
spreadsheet on somebody's laptop.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import date
from decimal import Decimal

from sheaveline.measures.duration import Duration
from sheaveline.ops.stop import StopKind
from sheaveline.plant.identity import LiftCode
from sheaveline.reports.forms import Align, Column, Report, Table
from sheaveline.schedule.season import Season


@dataclass(frozen=True)
class DayRecord:
    """One machine on one day, as the day log left it."""

    lift: LiftCode
    day: date
    opened: bool
    scheduled: Duration
    running: Duration
    downtime: Duration
    chargeable: Duration
    stops: tuple[StopKind, ...] = ()

    def availability(self) -> Decimal:
        if self.scheduled.is_zero():
            return Decimal(0)
        lost = self.chargeable.seconds
        return Decimal(self.scheduled.seconds - lost) / self.scheduled.seconds


@dataclass(frozen=True)
class SeasonSummary:
    """A season, its machines and every day they were meant to run."""

    season: Season
    records: tuple[DayRecord, ...]

    @classmethod
    def of(cls, season: Season, records: Sequence[DayRecord]) -> SeasonSummary:
        ordered = tuple(sorted(records, key=lambda one: (one.day, one.lift.value)))
        return cls(season, ordered)

    def lifts(self) -> tuple[LiftCode, ...]:
        seen: list[LiftCode] = []
        for record in self.records:
            if record.lift not in seen:
                seen.append(record.lift)
        return tuple(sorted(seen, key=lambda one: one.value))

    def for_lift(self, lift: LiftCode) -> tuple[DayRecord, ...]:
        return tuple(one for one in self.records if one.lift == lift)

    def days_open(self, lift: LiftCode) -> int:
        return sum(1 for one in self.for_lift(lift) if one.opened)

    def days_shut(self, lift: LiftCode) -> int:
        return sum(1 for one in self.for_lift(lift) if not one.opened)

    def scheduled(self, lift: LiftCode) -> Duration:
        total = Duration(0)
        for one in self.for_lift(lift):
            total = total + one.scheduled
        return total

    def running(self, lift: LiftCode) -> Duration:
        total = Duration(0)
        for one in self.for_lift(lift):
            total = total + one.running
        return total

    def downtime(self, lift: LiftCode) -> Duration:
        total = Duration(0)
        for one in self.for_lift(lift):
            total = total + one.downtime
        return total

    def chargeable(self, lift: LiftCode) -> Duration:
        total = Duration(0)
        for one in self.for_lift(lift):
            total = total + one.chargeable
        return total

    def availability(self, lift: LiftCode) -> Decimal:
        scheduled = self.scheduled(lift)
        if scheduled.is_zero():
            return Decimal(0)
        lost = self.chargeable(lift).seconds
        return Decimal(scheduled.seconds - lost) / scheduled.seconds

    def worst_day(self, lift: LiftCode) -> DayRecord | None:
        days = self.for_lift(lift)
        if not days:
            return None
        return max(days, key=lambda one: one.chargeable.seconds)

    def stops_by_kind(self, lift: LiftCode) -> dict[StopKind, int]:
        counted: dict[StopKind, int] = {}
        for record in self.for_lift(lift):
            for kind in record.stops:
                counted[kind] = counted.get(kind, 0) + 1
        return counted

    def hill_availability(self) -> Decimal:
        scheduled = sum(self.scheduled(one).seconds for one in self.lifts())
        if scheduled == 0:
            return Decimal(0)
        lost = sum(self.chargeable(one).seconds for one in self.lifts())
        return Decimal(scheduled - lost) / scheduled

    def render(self) -> Report:
        page = Report()
        page.heading(f"season summary {self.season.label}")
        page.field("opened", self.season.opens.isoformat())
        page.field("closed", self.season.closes.isoformat())
        page.field("operating days", self.season.length())
        page.field("machines", len(self.lifts()))
        page.field("availability", f"{self.hill_availability() * 100:.1f}%")

        page.section("by machine")
        page.table(
            Table.of(
                [
                    Column("lift", 5),
                    Column("open", 5, Align.RIGHT),
                    Column("shut", 5, Align.RIGHT),
                    Column("scheduled", 10, Align.RIGHT),
                    Column("running", 10, Align.RIGHT),
                    Column("charged", 10, Align.RIGHT),
                    Column("available", 10, Align.RIGHT),
                ],
                [
                    (
                        str(lift),
                        self.days_open(lift),
                        self.days_shut(lift),
                        f"{self.scheduled(lift).hours:.0f} h",
                        f"{self.running(lift).hours:.0f} h",
                        f"{self.chargeable(lift).hours:.1f} h",
                        f"{self.availability(lift) * 100:.1f}%",
                    )
                    for lift in self.lifts()
                ],
            )
        )

        page.section("worst day a machine")
        rows = []
        for lift in self.lifts():
            worst = self.worst_day(lift)
            if worst is None:
                continue
            rows.append(
                (
                    str(lift),
                    worst.day.isoformat(),
                    f"{worst.chargeable.hours:.1f} h",
                    f"{worst.availability() * 100:.1f}%",
                )
            )
        page.table(
            Table.of(
                [
                    Column("lift", 5),
                    Column("day", 11),
                    Column("charged", 10, Align.RIGHT),
                    Column("available", 10, Align.RIGHT),
                ],
                rows,
            )
        )

        page.section("stops by kind")
        kinds = sorted(
            {kind for lift in self.lifts() for kind in self.stops_by_kind(lift)},
            key=lambda one: one.value,
        )
        page.table(
            Table.of(
                [Column("kind", 18), *[Column(str(one), 5, Align.RIGHT) for one in self.lifts()]],
                [
                    (kind.value, *[self.stops_by_kind(one).get(kind, 0) for one in self.lifts()])
                    for kind in kinds
                ],
            )
        )
        return page

    def text(self) -> str:
        return self.render().text()
