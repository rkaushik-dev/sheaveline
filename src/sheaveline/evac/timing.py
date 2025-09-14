"""How long an evacuation takes, which is the number the board actually asks for.

The rule is simple to state and awkward to satisfy. Everybody has to be off the
line before dark, and the clock starts when the drive is declared dead rather
than when the first team reaches the first tower. So the answer has three parts:
the time to get the teams out to where the work is, the time to work each
carrier, and the fact that teams work at the same time as each other rather than
one after the other.

Teams working in parallel is the part that makes this arithmetic rather than
addition. Two teams do not halve the time unless the work splits evenly, and it
never does, because the awkward carriers are all in the same two spans.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from decimal import Decimal
from typing import Final

from sheaveline.evac.plan import EvacuationPlan, Method, PlanStep
from sheaveline.evac.profile import EvacuationError
from sheaveline.measures.duration import Duration
from sheaveline.measures.length import Length
from sheaveline.measures.speed import Speed
from sheaveline.schedule.daylight import Site
from sheaveline.schedule.hours import TimeOfDay

CALL_OUT: Final = Duration.from_minutes(15)
TRAVEL_SPEED: Final = Speed.from_feet_per_minute(180)
SETUP: Final[dict[Method, Duration]] = {
    Method.STEP_OFF: Duration.from_minutes(1),
    Method.LADDER: Duration.from_minutes(3),
    Method.ROPE: Duration.from_minutes(6),
}
PER_PERSON: Final[dict[Method, Duration]] = {
    Method.STEP_OFF: Duration.from_seconds(20),
    Method.LADDER: Duration.from_minutes(1),
    Method.ROPE: Duration.from_seconds(150),
}


@dataclass(frozen=True)
class StepTiming:
    """How long one row of the plan takes, split so it can be argued with."""

    step: PlanStep
    approach: Duration
    setup: Duration
    lowering: Duration

    @property
    def total(self) -> Duration:
        return self.approach + self.setup + self.lowering

    def __str__(self) -> str:
        return f"carrier {self.step.stop.number}: {self.total.format_clock()}"


@dataclass(frozen=True)
class EvacuationTiming:
    """One plan, costed."""

    plan: EvacuationPlan
    seats_filled: int = 3
    call_out: Duration = CALL_OUT
    travel: Speed = TRAVEL_SPEED

    def __post_init__(self) -> None:
        if self.seats_filled < 1:
            raise EvacuationError("an evacuation with nobody on the line is not one")
        if self.travel.is_stopped():
            raise EvacuationError("teams have to be able to walk")

    def approach_for(self, step: PlanStep) -> Duration:
        if step.team is None:
            return Duration(0)
        return self.travel.time_to_cover(step.team.distance_to(step.stop.station))

    def time_for(self, step: PlanStep) -> StepTiming:
        return StepTiming(
            step=step,
            approach=self.approach_for(step),
            setup=SETUP[step.method],
            lowering=PER_PERSON[step.method].scaled(self.seats_filled),
        )

    def timings(self) -> tuple[StepTiming, ...]:
        return tuple(self.time_for(step) for step in self.plan.steps())

    def work_by_team(self) -> dict[str, Duration]:
        """How long each team is busy, which is what sets the finish."""
        totals: dict[str, Duration] = {}
        for timing in self.timings():
            team = timing.step.team
            name = team.name if team is not None else "crew"
            totals[name] = totals.get(name, Duration(0)) + timing.total
        return totals

    def busiest_team(self) -> str:
        work = self.work_by_team()
        if not work:
            raise EvacuationError("nothing in this plan takes any time")
        return max(work, key=lambda name: work[name].seconds)

    def total_time(self) -> Duration:
        """Call out, then the longest single team's share of the work."""
        work = self.work_by_team()
        if not work:
            return self.call_out
        return self.call_out + max(work.values(), key=lambda spent: spent.seconds)

    def serial_time(self) -> Duration:
        """What it would take with one team, which is what the board compares against."""
        total = self.call_out
        for timing in self.timings():
            total = total + timing.total
        return total

    def speed_up(self) -> Decimal:
        """How much the extra teams actually buy, which is never the team count."""
        parallel = self.total_time().seconds
        if parallel == 0:
            raise EvacuationError("an evacuation that takes no time is not one")
        return Decimal(self.serial_time().seconds) / parallel

    def finishes_by(self, started: TimeOfDay) -> TimeOfDay:
        return started.plus(self.total_time())

    def fits_in_daylight(self, site: Site, on: date, started: TimeOfDay) -> bool:
        return site.fits_in_daylight(on, started, self.total_time())

    def latest_start(self, site: Site, on: date) -> TimeOfDay:
        return site.latest_start(on, self.total_time())

    def people(self) -> int:
        return len(self.plan.stops()) * self.seats_filled

    def with_load(self, seats_filled: int) -> EvacuationTiming:
        return EvacuationTiming(self.plan, seats_filled, self.call_out, self.travel)

    def __str__(self) -> str:
        return f"{self.people()} people, {self.total_time().format_hms()}"


def approach_distance(plan: EvacuationPlan) -> Length:
    """The total walking the teams do, which is what argues for a second staging."""
    total = Length(0)
    for step in plan.steps():
        if step.team is not None:
            total = total + step.team.distance_to(step.stop.station)
    return total
