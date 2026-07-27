"""Where the teams park, which is what actually sets the evacuation clock.

The existing plan hands each carrier to whichever able team is nearest. That is
the right answer for one carrier and the wrong answer for a line, because nearest
is measured from wherever the team happens to be standing, and on a quad with
eighty odd carriers it will cheerfully give one team two thirds of the work while
another sits at the bottom terminal.

The clock is not the total work. It is the busiest team's share of it, because
the teams work at the same time. So the job here is to cut the line into as many
runs as there are teams and put the cuts where the worst run is as small as it
can be, then park each team in the middle of its own run.

The cut positions and the staging points decide each other. Move a cut and both
neighbouring teams stage somewhere else, which changes their walking, which
changes where the next cut wants to be. That is why this is a search over the
finish time rather than a rule about distance: pick a finish, ask whether the
line can be cut to meet it, and squeeze.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import date

from sheaveline.evac.plan import STEP_OFF_HEIGHT, Method, PlanStep
from sheaveline.evac.profile import CarrierStop, EvacuationError, EvacuationProfile
from sheaveline.evac.team import RescueTeam, TeamPool
from sheaveline.evac.timing import CALL_OUT, PER_PERSON, SETUP, TRAVEL_SPEED
from sheaveline.measures.duration import Duration
from sheaveline.measures.length import Length
from sheaveline.measures.speed import Speed


@dataclass(frozen=True)
class Beat:
    """One contiguous stretch of the line, and the team that walks it."""

    team: RescueTeam
    stops: tuple[CarrierStop, ...]
    staged_at: Length
    seats_filled: int
    travel: Speed = TRAVEL_SPEED

    def __post_init__(self) -> None:
        if not self.stops:
            raise EvacuationError(f"{self.team.name} has been given nothing to do")
        if self.seats_filled < 1:
            raise EvacuationError("an evacuation with nobody on the line is not one")

    def method_for(self, stop: CarrierStop) -> Method:
        """This team's kit, against this carrier. Not the pool's best kit."""
        if not stop.is_above(STEP_OFF_HEIGHT):
            return Method.STEP_OFF
        return Method.ROPE if self.team.kit.needs_a_rope_for(stop.height) else Method.LADDER

    def work_on(self, stop: CarrierStop) -> Duration:
        method = self.method_for(stop)
        walk = self.travel.time_to_cover(abs(stop.station - self.staged_at))
        return walk + SETUP[method] + PER_PERSON[method].scaled(self.seats_filled)

    def work(self) -> Duration:
        total = Duration(0)
        for stop in self.stops:
            total = total + self.work_on(stop)
        return total

    def reaches_everything(self) -> bool:
        return all(self.team.can_reach(stop) for stop in self.stops)

    def out_of_reach(self) -> tuple[CarrierStop, ...]:
        return tuple(stop for stop in self.stops if not self.team.can_reach(stop))

    def walking(self) -> Length:
        total = Length(0)
        for stop in self.stops:
            total = total + abs(stop.station - self.staged_at)
        return total

    def steps(self, first: int = 1) -> tuple[PlanStep, ...]:
        planned = []
        for offset, stop in enumerate(self.stops):
            method = self.method_for(stop)
            planned.append(
                PlanStep(
                    order=first + offset,
                    stop=stop,
                    method=method,
                    team=self.team if method.needs_a_team else None,
                )
            )
        return tuple(planned)

    def __str__(self) -> str:
        return (
            f"{self.team.name} at {self.staged_at.feet:.0f} ft, "
            f"{len(self.stops)} carriers, {self.work().format_clock()}"
        )


@dataclass(frozen=True)
class StagingPlan:
    """The whole line cut up, with every team parked where its run wants it."""

    profile: EvacuationProfile
    on: date
    beats: tuple[Beat, ...]
    stranded: tuple[CarrierStop, ...] = ()
    call_out: Duration = CALL_OUT

    def teams(self) -> tuple[RescueTeam, ...]:
        return tuple(beat.team for beat in self.beats)

    def stops(self) -> tuple[CarrierStop, ...]:
        return tuple(stop for beat in self.beats for stop in beat.stops)

    def busiest(self) -> Beat:
        if not self.beats:
            raise EvacuationError("nobody is on the hill")
        return max(self.beats, key=lambda beat: beat.work().seconds)

    def quietest(self) -> Beat:
        if not self.beats:
            raise EvacuationError("nobody is on the hill")
        return min(self.beats, key=lambda beat: beat.work().seconds)

    def clears_in(self) -> Duration:
        """Call out, then the busiest team, because the rest finish sooner."""
        if not self.beats:
            raise EvacuationError("nobody is on the hill")
        return self.call_out + self.busiest().work()

    def serial_time(self) -> Duration:
        total = self.call_out
        for beat in self.beats:
            total = total + beat.work()
        return total

    def spread(self) -> Duration:
        """Busiest minus quietest, which is how badly the cutting went."""
        return self.busiest().work() - self.quietest().work()

    def is_workable(self) -> bool:
        return not self.stranded and all(beat.reaches_everything() for beat in self.beats)

    def refusals(self) -> tuple[str, ...]:
        found = [
            f"carrier {stop.number} at {stop.height.feet:.0f} ft, nobody reaches it"
            for stop in self.stranded
        ]
        for beat in self.beats:
            for stop in beat.out_of_reach():
                found.append(f"{beat.team.name} cannot reach carrier {stop.number}")
        return tuple(found)

    def steps(self) -> tuple[PlanStep, ...]:
        planned: list[PlanStep] = []
        for beat in self.beats:
            planned.extend(beat.steps(len(planned) + 1))
        return tuple(planned)

    def walking(self) -> Length:
        total = Length(0)
        for beat in self.beats:
            total = total + beat.walking()
        return total

    def __len__(self) -> int:
        return len(self.beats)

    def __str__(self) -> str:
        if not self.beats:
            return f"nobody can work this line, {len(self.stranded)} carriers stranded"
        note = f", {len(self.stranded)} stranded" if self.stranded else ""
        return (
            f"{len(self.beats)} teams, {len(self.stops())} carriers, "
            f"clear in {self.clears_in().format_clock()}{note}"
        )


def staging_point(stops: Sequence[CarrierStop]) -> Length:
    """Halfway between the ends of the run, which is where the walking is least."""
    if not stops:
        raise EvacuationError("an empty run has no middle")
    stations = [stop.station.mils for stop in stops]
    return Length((min(stations) + max(stations)) // 2)


def _run_work(
    stops: Sequence[CarrierStop], team: RescueTeam, seats_filled: int, travel: Speed
) -> Duration:
    return Beat(team, tuple(stops), staging_point(stops), seats_filled, travel).work()


def _covering_team(run: Sequence[CarrierStop], free: Sequence[RescueTeam]) -> RescueTeam | None:
    """The smallest kit that still covers the run, so the long ropes stay free."""
    able = [team for team in free if all(team.can_reach(stop) for stop in run)]
    if not able:
        return None
    return min(able, key=lambda team: (team.kit.reach().mils, team.name))


def _cut_within(
    ordered: Sequence[CarrierStop],
    teams: Sequence[RescueTeam],
    bound: Duration,
    seats_filled: int,
    travel: Speed,
) -> list[tuple[RescueTeam, list[CarrierStop]]] | None:
    """Cut the line into runs nobody has to work for longer than the bound.

    Greedy on a contiguous cut is exact for the length: a team that could take one
    more carrier and does not only pushes that carrier onto somebody who is
    already at least as busy. The team choice is not free though, so each run
    takes the smallest kit that covers it and the long ropes stay available for
    the steep ground further up.
    """
    free = list(teams)
    runs: list[tuple[RescueTeam, list[CarrierStop]]] = []
    index = 0
    while index < len(ordered):
        if not free:
            return None
        best: tuple[RescueTeam, list[CarrierStop]] | None = None
        taken = 0
        while index + taken < len(ordered):
            trying = list(ordered[index : index + taken + 1])
            team = _covering_team(trying, free)
            if team is None:
                break
            if _run_work(trying, team, seats_filled, travel) > bound:
                break
            best = (team, trying)
            taken += 1
        if best is None:
            return None
        free.remove(best[0])
        runs.append(best)
        index += len(best[1])
    return runs


def stage(
    profile: EvacuationProfile,
    pool: TeamPool,
    offset: Length,
    on: date,
    seats_filled: int = 3,
    travel: Speed = TRAVEL_SPEED,
    call_out: Duration = CALL_OUT,
) -> StagingPlan:
    """Cut the line so the busiest team finishes as early as it can.

    The search is on the finish time rather than on distance. Distance is the
    thing that changes when a cut moves, so asking where the cuts go is asking a
    question with no fixed answer. Asking whether a given finish is reachable has
    one, and squeezing it is a binary search on somewhere under three hundred
    steps.
    """
    teams = [team for team in pool.current(on) if team.is_equipped()]
    if not teams:
        raise EvacuationError(f"no team on the hill is fit to work on {on.isoformat()}")
    stops = sorted(profile.stops_at_offset(offset), key=lambda stop: stop.station.mils)
    stranded = tuple(stop for stop in stops if not any(one.can_reach(stop) for one in teams))
    workable = [stop for stop in stops if stop not in stranded]
    if not workable:
        return StagingPlan(profile, on, (), stranded, call_out)

    biggest = max(teams, key=lambda team: team.kit.reach().mils)
    high = _run_work(workable, biggest, seats_filled, travel)
    low = Duration(0)
    found = _cut_within(workable, teams, high, seats_filled, travel)
    if found is None:
        return StagingPlan(profile, on, (), tuple(stops), call_out)
    while low.seconds < high.seconds:
        middle = Duration((low.seconds + high.seconds) // 2)
        trying = _cut_within(workable, teams, middle, seats_filled, travel)
        if trying is None:
            low = Duration(middle.seconds + 1)
        else:
            found = trying
            high = middle
    return StagingPlan(
        profile=profile,
        on=on,
        beats=tuple(
            Beat(team, tuple(run), staging_point(run), seats_filled, travel) for team, run in found
        ),
        stranded=stranded,
        call_out=call_out,
    )


def restage(plan: StagingPlan, seats_filled: int) -> StagingPlan:
    """The same cut, costed for a different load. A busy Saturday is not a Tuesday."""
    return StagingPlan(
        profile=plan.profile,
        on=plan.on,
        beats=tuple(
            Beat(beat.team, beat.stops, beat.staged_at, seats_filled, beat.travel)
            for beat in plan.beats
        ),
        stranded=plan.stranded,
        call_out=plan.call_out,
    )
