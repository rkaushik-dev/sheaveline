"""Drills, and the two different kinds of currency they buy.

A drill proves two things and they are not the same thing. It proves the machine
has a plan that works, which is a property of the lift and lasts a year. And it
proves a particular team can do the work, which is a property of the people and
lapses at the end of the season whoever else was there.

So a resort that ran one splendid full line drill in November and then hired
four new patrollers in January is not current, and the register has to be able
to say that without anybody having to notice it.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import date, timedelta
from enum import Enum
from typing import Final

from sheaveline.evac.profile import EvacuationError
from sheaveline.measures.duration import Duration
from sheaveline.plant.identity import LiftCode
from sheaveline.schedule.season import Season

FULL_DRILL_VALID_DAYS: Final = 365
MINIMUM_FULL_CARRIERS: Final = 4


class DrillKind(Enum):
    """How much of the plan a drill actually exercised."""

    TABLETOP = "tabletop"
    PARTIAL = "partial"
    FULL_LINE = "full line"

    @property
    def puts_people_on_the_line(self) -> bool:
        return self is not DrillKind.TABLETOP

    @property
    def satisfies_the_annual(self) -> bool:
        return self is DrillKind.FULL_LINE


@dataclass(frozen=True)
class Drill:
    """One drill on one machine."""

    lift: LiftCode
    on: date
    kind: DrillKind
    teams: tuple[str, ...]
    carriers_worked: int
    elapsed: Duration
    observer: str

    def __post_init__(self) -> None:
        if not self.teams:
            raise EvacuationError("a drill has teams in it")
        if len(set(self.teams)) != len(self.teams):
            raise EvacuationError("a team is counted once in a drill")
        if not self.observer.strip():
            raise EvacuationError("a drill is signed off by whoever watched it")
        if self.carriers_worked < 0:
            raise EvacuationError("a drill cannot work a negative number of carriers")
        if self.kind.puts_people_on_the_line and self.carriers_worked == 0:
            raise EvacuationError(f"a {self.kind.value} drill works at least one carrier")
        if not self.kind.puts_people_on_the_line and self.carriers_worked > 0:
            raise EvacuationError("a tabletop drill does not touch a carrier")

    @classmethod
    def of(
        cls,
        lift: LiftCode,
        on: date,
        kind: DrillKind,
        teams: Sequence[str],
        carriers_worked: int,
        elapsed: Duration,
        observer: str,
    ) -> Drill:
        return cls(lift, on, kind, tuple(teams), carriers_worked, elapsed, observer)

    def counts_for_the_annual(self) -> bool:
        """A full line drill that worked enough carriers to mean anything."""
        return self.kind.satisfies_the_annual and self.carriers_worked >= MINIMUM_FULL_CARRIERS

    def minutes_per_carrier(self) -> Duration:
        if self.carriers_worked == 0:
            raise EvacuationError("nothing was worked, so there is no rate")
        return Duration(self.elapsed.seconds // self.carriers_worked)

    def included(self, team: str) -> bool:
        return team in self.teams

    def expires(self) -> date:
        return self.on + timedelta(days=FULL_DRILL_VALID_DAYS)

    def __str__(self) -> str:
        return (
            f"{self.lift} {self.kind.value} on {self.on.isoformat()}, "
            f"{self.carriers_worked} carriers"
        )


@dataclass(frozen=True)
class DrillRegister:
    """Every drill run on the hill."""

    drills: tuple[Drill, ...] = ()

    @classmethod
    def of(cls, drills: Sequence[Drill] = ()) -> DrillRegister:
        ordered = tuple(sorted(drills, key=lambda drill: drill.on))
        return cls(ordered)

    def on_lift(self, lift: LiftCode) -> tuple[Drill, ...]:
        return tuple(drill for drill in self.drills if drill.lift == lift)

    def last_full(self, lift: LiftCode) -> Drill | None:
        counting = [drill for drill in self.on_lift(lift) if drill.counts_for_the_annual()]
        return counting[-1] if counting else None

    def lift_is_current(self, lift: LiftCode, on: date) -> bool:
        last = self.last_full(lift)
        return last is not None and on <= last.expires()

    def lift_expires(self, lift: LiftCode) -> date | None:
        last = self.last_full(lift)
        return None if last is None else last.expires()

    def team_took_part(self, team: str, lift: LiftCode, season: Season) -> bool:
        return any(drill.included(team) and season.covers(drill.on) for drill in self.on_lift(lift))

    def teams_short(
        self,
        lift: LiftCode,
        season: Season,
        expected: Sequence[str],
    ) -> tuple[str, ...]:
        """Teams on the machine that have not been in a drill this season."""
        return tuple(name for name in expected if not self.team_took_part(name, lift, season))

    def is_current(
        self,
        lift: LiftCode,
        on: date,
        season: Season,
        expected: Sequence[str],
    ) -> bool:
        """Both kinds of currency at once, which is the question actually asked."""
        return self.lift_is_current(lift, on) and not self.teams_short(lift, season, expected)

    def rate_for(self, lift: LiftCode) -> Duration:
        """The best rate the hill has actually achieved, which is what a plan is checked against."""
        worked = [drill for drill in self.on_lift(lift) if drill.carriers_worked > 0]
        if not worked:
            raise EvacuationError(f"nothing has been worked on {lift}")
        return min((drill.minutes_per_carrier() for drill in worked), key=lambda one: one.seconds)

    def with_drill(self, drill: Drill) -> DrillRegister:
        return DrillRegister.of([*self.drills, drill])

    def __len__(self) -> int:
        return len(self.drills)
