"""The season summary, which is the page the board reads in May.

It is the same handful of numbers asked of every operating day: how many days
did each machine run, how much of that was it stopped, and whose fault was the
stopping. The board compares this year against last, so the arithmetic has to be
the same arithmetic every year, which is why it is here rather than in a
spreadsheet on somebody's laptop.

Every one of those questions used to walk the whole record list from the start,
and the page asks eight of them a machine plus one a stop kind. On a season of
a hundred and fifty days across six machines that is the same nine hundred rows
read something over sixty times, and it showed. The records are tallied once now,
on the way in, and every question reads the tally.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass, field
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
class LiftTally:
    """One machine's season, added up once."""

    lift: LiftCode
    days: tuple[DayRecord, ...]
    days_open: int
    days_shut: int
    scheduled: Duration
    running: Duration
    downtime: Duration
    chargeable: Duration
    stops: dict[StopKind, int]

    @classmethod
    def of(cls, lift: LiftCode, days: Sequence[DayRecord]) -> LiftTally:
        scheduled = running = downtime = chargeable = Duration(0)
        open_days = shut_days = 0
        stops: dict[StopKind, int] = {}
        for record in days:
            scheduled = scheduled + record.scheduled
            running = running + record.running
            downtime = downtime + record.downtime
            chargeable = chargeable + record.chargeable
            if record.opened:
                open_days += 1
            else:
                shut_days += 1
            for kind in record.stops:
                stops[kind] = stops.get(kind, 0) + 1
        return cls(
            lift=lift,
            days=tuple(days),
            days_open=open_days,
            days_shut=shut_days,
            scheduled=scheduled,
            running=running,
            downtime=downtime,
            chargeable=chargeable,
            stops=stops,
        )

    def availability(self) -> Decimal:
        if self.scheduled.is_zero():
            return Decimal(0)
        return Decimal(self.scheduled.seconds - self.chargeable.seconds) / self.scheduled.seconds

    def worst_day(self) -> DayRecord | None:
        if not self.days:
            return None
        return max(self.days, key=lambda one: one.chargeable.seconds)


@dataclass(frozen=True)
class SeasonSummary:
    """A season, its machines and every day they were meant to run."""

    season: Season
    records: tuple[DayRecord, ...]
    tallies: dict[LiftCode, LiftTally] = field(default_factory=dict)

    @classmethod
    def of(cls, season: Season, records: Sequence[DayRecord]) -> SeasonSummary:
        ordered = tuple(sorted(records, key=lambda one: (one.day, one.lift.value)))
        grouped: dict[LiftCode, list[DayRecord]] = {}
        for record in ordered:
            grouped.setdefault(record.lift, []).append(record)
        tallies = {
            lift: LiftTally.of(lift, days)
            for lift, days in sorted(grouped.items(), key=lambda pair: pair[0].value)
        }
        return cls(season, ordered, tallies)

    def lifts(self) -> tuple[LiftCode, ...]:
        return tuple(self.tallies)

    def tally(self, lift: LiftCode) -> LiftTally:
        return self.tallies.get(lift, LiftTally.of(lift, ()))

    def for_lift(self, lift: LiftCode) -> tuple[DayRecord, ...]:
        return self.tally(lift).days

    def days_open(self, lift: LiftCode) -> int:
        return self.tally(lift).days_open

    def days_shut(self, lift: LiftCode) -> int:
        return self.tally(lift).days_shut

    def scheduled(self, lift: LiftCode) -> Duration:
        return self.tally(lift).scheduled

    def running(self, lift: LiftCode) -> Duration:
        return self.tally(lift).running

    def downtime(self, lift: LiftCode) -> Duration:
        return self.tally(lift).downtime

    def chargeable(self, lift: LiftCode) -> Duration:
        return self.tally(lift).chargeable

    def availability(self, lift: LiftCode) -> Decimal:
        return self.tally(lift).availability()

    def worst_day(self, lift: LiftCode) -> DayRecord | None:
        return self.tally(lift).worst_day()

    def stops_by_kind(self, lift: LiftCode) -> dict[StopKind, int]:
        return dict(self.tally(lift).stops)

    def hill_availability(self) -> Decimal:
        scheduled = sum(one.scheduled.seconds for one in self.tallies.values())
        if scheduled == 0:
            return Decimal(0)
        lost = sum(one.chargeable.seconds for one in self.tallies.values())
        return Decimal(scheduled - lost) / scheduled

    def render(self) -> Report:
        tallies = [self.tally(lift) for lift in self.lifts()]
        page = Report()
        page.heading(f"season summary {self.season.label}")
        page.field("opened", self.season.opens.isoformat())
        page.field("closed", self.season.closes.isoformat())
        page.field("operating days", self.season.length())
        page.field("machines", len(tallies))
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
                        str(one.lift),
                        one.days_open,
                        one.days_shut,
                        f"{one.scheduled.hours:.0f} h",
                        f"{one.running.hours:.0f} h",
                        f"{one.chargeable.hours:.1f} h",
                        f"{one.availability() * 100:.1f}%",
                    )
                    for one in tallies
                ],
            )
        )

        page.section("worst day a machine")
        rows = []
        for one in tallies:
            worst = one.worst_day()
            if worst is None:
                continue
            rows.append(
                (
                    str(one.lift),
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
            {kind for one in tallies for kind in one.stops},
            key=lambda one: one.value,
        )
        page.table(
            Table.of(
                [Column("kind", 18), *[Column(str(one.lift), 5, Align.RIGHT) for one in tallies]],
                [(kind.value, *[one.stops.get(kind, 0) for one in tallies]) for kind in kinds],
            )
        )
        return page

    def text(self) -> str:
        return self.render().text()
