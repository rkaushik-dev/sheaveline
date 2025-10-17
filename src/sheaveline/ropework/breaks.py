"""Broken wires, counted the way the standard counts them.

A broken wire on its own means nothing. What matters is how many there are
inside one lay length, how many inside six, and whether any of them are down in
the valley between strands rather than on the crown. A valley break usually
means the wire has been working against the wire beside it for a long time, so
one of those weighs about as much as a handful of crown breaks.

Lang lay ropes get half the allowance, because the outer wires run more nearly
along the rope and a break in one carries further.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import date
from typing import Final

from sheaveline.measures.length import Length
from sheaveline.plant.rope import HaulRope, RopeSpec
from sheaveline.ropework.wear import RopeworkError

REGULAR_LAY_LIMITS: Final = (6, 12)
LANG_LAY_LIMITS: Final = (3, 6)
VALLEY_LIMIT: Final = 1
SPLICE_LIMIT: Final = 2


@dataclass(frozen=True)
class BreakLimits:
    """How many breaks a rope of this construction is allowed to carry."""

    one_lay: int
    six_lays: int
    valley: int
    at_splice: int

    @classmethod
    def for_spec(cls, spec: RopeSpec) -> BreakLimits:
        one, six = LANG_LAY_LIMITS if spec.lay.is_lang else REGULAR_LAY_LIMITS
        return cls(one_lay=one, six_lays=six, valley=VALLEY_LIMIT, at_splice=SPLICE_LIMIT)

    def watch_level(self) -> BreakLimits:
        """Half the allowance, which is where the rope goes on the watch list."""
        return BreakLimits(
            one_lay=self.one_lay // 2,
            six_lays=self.six_lays // 2,
            valley=0,
            at_splice=0,
        )


@dataclass(frozen=True)
class BreakObservation:
    """What was counted at one station along the rope."""

    station: Length
    in_one_lay: int
    in_six_lays: int
    valley_breaks: int = 0
    at_splice: bool = False

    def __post_init__(self) -> None:
        if self.station.mils < 0:
            raise RopeworkError("a break station is measured from the splice, forwards")
        for name, count in (
            ("one lay", self.in_one_lay),
            ("six lays", self.in_six_lays),
            ("valley", self.valley_breaks),
        ):
            if count < 0:
                raise RopeworkError(f"a {name} break count cannot be negative")
        if self.in_six_lays < self.in_one_lay:
            raise RopeworkError("six lays cannot hold fewer breaks than one of them")
        if self.valley_breaks > self.in_one_lay:
            raise RopeworkError("valley breaks are counted inside the one lay total")

    def exceeds(self, limits: BreakLimits) -> bool:
        if self.in_one_lay > limits.one_lay:
            return True
        if self.in_six_lays > limits.six_lays:
            return True
        if self.valley_breaks > limits.valley:
            return True
        return self.at_splice and self.in_one_lay > limits.at_splice

    def __str__(self) -> str:
        where = " at the splice" if self.at_splice else ""
        return f"{self.in_one_lay} in one lay at {self.station.feet:.0f} ft{where}"


@dataclass(frozen=True)
class BreakSurvey:
    """One walk of the rope counting broken wires."""

    rope: HaulRope
    taken: date
    inspector: str
    observations: tuple[BreakObservation, ...]

    def __post_init__(self) -> None:
        if not self.inspector.strip():
            raise RopeworkError("a survey is signed by whoever took it")
        if self.taken < self.rope.installed:
            raise RopeworkError("a survey cannot predate the rope it is of")
        for seen in self.observations:
            if seen.station > self.rope.length:
                raise RopeworkError(f"station {seen.station.feet:.0f} is past the end")

    @classmethod
    def of(
        cls,
        rope: HaulRope,
        taken: date,
        inspector: str,
        observations: Sequence[BreakObservation] = (),
    ) -> BreakSurvey:
        return cls(rope, taken, inspector, tuple(observations))

    def limits(self) -> BreakLimits:
        return BreakLimits.for_spec(self.rope.spec)

    def total_breaks(self) -> int:
        return sum(seen.in_one_lay for seen in self.observations)

    def worst(self) -> BreakObservation | None:
        if not self.observations:
            return None
        return max(self.observations, key=lambda seen: (seen.valley_breaks, seen.in_one_lay))

    def exceedances(self) -> tuple[BreakObservation, ...]:
        limits = self.limits()
        return tuple(seen for seen in self.observations if seen.exceeds(limits))

    def near_limit(self) -> tuple[BreakObservation, ...]:
        watch = self.limits().watch_level()
        exceeded = set(self.exceedances())
        return tuple(
            seen for seen in self.observations if seen.exceeds(watch) and seen not in exceeded
        )

    def clean_length(self) -> Length:
        """How much of the rope carried no counted breaks at all."""
        marked = len({seen.station.mils for seen in self.observations})
        if marked == 0:
            return self.rope.length
        lay = self.rope.spec.lay_length()
        return self.rope.length - lay.scaled(marked)

    def __str__(self) -> str:
        return (
            f"{self.rope.serial} breaks on {self.taken.isoformat()}, {self.total_breaks()} counted"
        )
