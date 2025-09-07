"""Turning a stopped line into a list of jobs with a team against each one.

The plan is deliberately dull. Every carrier gets a row, the row says how the
people are coming down and who is doing it, and the rows are in the order they
get worked. Nothing clever, because it is read at four in the afternoon in a
snowstorm by somebody who has been on their feet since seven.

Order is by exposure rather than by convenience. The chair ninety feet over a
gully is worked before the one eight feet over the cat track, even though the
second one is quicker, because the people in the first one are the ones who are
going to be in trouble.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from enum import Enum
from typing import Final

from sheaveline.evac.profile import CarrierStop, EvacuationError, EvacuationProfile
from sheaveline.evac.team import RescueTeam, TeamPool
from sheaveline.measures.length import Length

STEP_OFF_HEIGHT: Final = Length.from_feet(6)


class Method(Enum):
    """How the people in one carrier are coming down."""

    STEP_OFF = "step off"
    LADDER = "ladder"
    ROPE = "rope"

    @property
    def needs_a_team(self) -> bool:
        return self is not Method.STEP_OFF

    @property
    def is_technical(self) -> bool:
        return self is Method.ROPE


@dataclass(frozen=True)
class PlanStep:
    """One carrier, how it is being cleared and who is clearing it."""

    order: int
    stop: CarrierStop
    method: Method
    team: RescueTeam | None

    def __post_init__(self) -> None:
        if self.order < 1:
            raise EvacuationError("plan steps are numbered from one")
        if self.method.needs_a_team and self.team is None:
            raise EvacuationError(f"carrier {self.stop.number} has no team against it")
        if not self.method.needs_a_team and self.team is not None:
            raise EvacuationError(f"carrier {self.stop.number} does not need a team")

    def __str__(self) -> str:
        who = self.team.name if self.team is not None else "crew"
        return (
            f"{self.order}. carrier {self.stop.number} at {self.stop.height.feet:.0f} ft, "
            f"{self.method.value}, {who}"
        )


@dataclass(frozen=True)
class EvacuationPlan:
    """The whole line, worked out for one stop position on one day."""

    profile: EvacuationProfile
    pool: TeamPool
    offset: Length
    on: date

    def stops(self) -> tuple[CarrierStop, ...]:
        return tuple(self.profile.stops_at_offset(self.offset))

    def method_for(self, stop: CarrierStop) -> Method:
        if stop.height <= STEP_OFF_HEIGHT:
            return Method.STEP_OFF
        ladders = [
            team
            for team in self.pool.current(self.on)
            if not team.kit.needs_a_rope_for(stop.height)
        ]
        return Method.LADDER if ladders else Method.ROPE

    def ordered_stops(self) -> tuple[CarrierStop, ...]:
        return tuple(sorted(self.stops(), key=lambda stop: (-stop.height.mils, stop.station.mils)))

    def steps(self) -> tuple[PlanStep, ...]:
        planned = []
        order = 1
        for stop in self.ordered_stops():
            method = self.method_for(stop)
            if not method.needs_a_team:
                planned.append(PlanStep(order, stop, method, None))
                order += 1
                continue
            able = self.pool.able(stop, self.on)
            if not able:
                continue
            team = self.pool.nearest_able(stop, self.on)
            planned.append(PlanStep(order, stop, method, team))
            order += 1
        return tuple(planned)

    def unassigned(self) -> tuple[CarrierStop, ...]:
        """Carriers nobody in the pool can work, which is what a plan is for finding."""
        return tuple(
            stop
            for stop in self.ordered_stops()
            if self.method_for(stop).needs_a_team and not self.pool.able(stop, self.on)
        )

    def is_workable(self) -> bool:
        return not self.unassigned()

    def by_team(self) -> dict[str, tuple[PlanStep, ...]]:
        grouped: dict[str, list[PlanStep]] = {}
        for step in self.steps():
            if step.team is None:
                continue
            grouped.setdefault(step.team.name, []).append(step)
        return {name: tuple(steps) for name, steps in grouped.items()}

    def technical_steps(self) -> tuple[PlanStep, ...]:
        return tuple(step for step in self.steps() if step.method.is_technical)

    def step_offs(self) -> tuple[PlanStep, ...]:
        return tuple(step for step in self.steps() if step.method is Method.STEP_OFF)

    def people_on_the_line(self, seats_filled: int) -> int:
        if seats_filled < 0:
            raise EvacuationError("a carrier cannot hold fewer than nobody")
        return len(self.stops()) * seats_filled

    def __str__(self) -> str:
        state = "workable" if self.is_workable() else f"{len(self.unassigned())} unreachable"
        return f"{len(self.stops())} carriers, {state}"
