"""Carrier spacing along the rope, and the interval that falls out of it.

There is one number the loading crew feels and it is the interval, the seconds
between one carrier and the next arriving at the gate. Everything else about
capacity is arithmetic on top of it. The interval is the spacing divided by the
rope speed, the spacing is the loop divided by the carriers, and the loop is
fixed by the machine.

That means there are only two dials. Take carriers off and the interval opens
up. Slow the rope down and it opens up as well, but slowing the rope costs
capacity twice over because the ride gets longer too.

A detachable is different in one respect worth stating. Its carriers slow to a
walk in the terminal, so the interval the crew loads at is not the interval the
carriers run at on the line, and the minimum the class will tolerate is set by
the terminal and not by the rope.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from decimal import Decimal
from typing import Final

from sheaveline.errors import SheavelineError
from sheaveline.measures.duration import Duration
from sheaveline.measures.length import Length
from sheaveline.measures.speed import Speed
from sheaveline.plant.classes import LiftClass
from sheaveline.plant.lift import Lift


class CapacityError(SheavelineError):
    """A capacity question was asked in a way that has no answer."""


MINIMUM_INTERVAL: Final[dict[LiftClass, Duration]] = {
    LiftClass.FIXED_CHAIR: Duration.from_seconds(9),
    LiftClass.DETACHABLE_CHAIR: Duration.from_seconds(6),
    LiftClass.PULSE_GONDOLA: Duration.from_seconds(30),
    LiftClass.DETACHABLE_GONDOLA: Duration.from_seconds(6),
    LiftClass.AERIAL_TRAM: Duration.from_seconds(300),
    LiftClass.SURFACE_TOW: Duration.from_seconds(5),
}


@dataclass(frozen=True)
class LineSpacing:
    """Carriers spread evenly round a loop, running at a speed."""

    loop: Length
    carriers: int
    speed: Speed

    def __post_init__(self) -> None:
        if self.loop.mils <= 0:
            raise CapacityError("a loop has a length")
        if self.carriers < 1:
            raise CapacityError("a line carries at least one carrier")
        if self.speed.is_stopped():
            raise CapacityError("a stopped rope has no interval")

    @classmethod
    def of(cls, lift: Lift, speed: Speed | None = None) -> LineSpacing:
        return cls(
            loop=lift.expected_rope_length(),
            carriers=lift.carriers_on_line(),
            speed=speed or lift.design_speed,
        )

    def spacing(self) -> Length:
        return Length(self.loop.mils // self.carriers)

    def interval(self) -> Duration:
        return self.speed.time_to_cover(self.spacing())

    def carriers_for(self, spacing: Length) -> int:
        """How many carriers that spacing would put on the loop."""
        if spacing.mils <= 0:
            raise CapacityError("a spacing is a positive length")
        return int(self.loop.ratio(spacing))

    def spacing_for(self, interval: Duration) -> Length:
        if interval.is_zero():
            raise CapacityError("an interval of nothing has no spacing")
        return self.speed.travel(interval)

    def at_speed(self, speed: Speed) -> LineSpacing:
        return LineSpacing(self.loop, self.carriers, speed)

    def with_carriers(self, carriers: int) -> LineSpacing:
        return LineSpacing(self.loop, carriers, self.speed)

    def meets(self, lift_class: LiftClass) -> bool:
        return self.interval() >= MINIMUM_INTERVAL[lift_class]

    def shortfall(self, lift_class: LiftClass) -> Duration:
        floor = MINIMUM_INTERVAL[lift_class]
        if self.interval() >= floor:
            return Duration(0)
        return floor - self.interval()

    def most_carriers_for(self, lift_class: LiftClass) -> int:
        """The fullest the line may be run without dropping under the class floor."""
        room = self.spacing_for(MINIMUM_INTERVAL[lift_class])
        return self.carriers_for(room)

    def __str__(self) -> str:
        return (
            f"{self.carriers} carriers at {self.spacing().feet:.0f} ft, "
            f"{self.interval().format_clock()} apart"
        )


@dataclass(frozen=True)
class SpacingSurvey:
    """Gaps measured between carriers on the line, which are never quite even."""

    nominal: Length
    gaps: tuple[Length, ...]

    def __post_init__(self) -> None:
        if self.nominal.mils <= 0:
            raise CapacityError("a spacing survey is read against a nominal spacing")
        if not self.gaps:
            raise CapacityError("a spacing survey has gaps in it")
        for gap in self.gaps:
            if gap.mils <= 0:
                raise CapacityError("a measured gap is a positive length")

    @classmethod
    def of(cls, nominal: Length, gaps: Sequence[Length]) -> SpacingSurvey:
        return cls(nominal, tuple(gaps))

    def widest(self) -> Length:
        return max(self.gaps)

    def tightest(self) -> Length:
        return min(self.gaps)

    def spread(self) -> Length:
        return self.widest() - self.tightest()

    def worst_error(self) -> Decimal:
        """The largest departure from nominal, either way, as a fraction."""
        return max(abs((gap - self.nominal).ratio(self.nominal)) for gap in self.gaps)

    def is_even(self, tolerance: str | Decimal = "0.05") -> bool:
        return self.worst_error() <= Decimal(str(tolerance))

    def out_of_tolerance(self, tolerance: str | Decimal = "0.05") -> tuple[Length, ...]:
        limit = Decimal(str(tolerance))
        return tuple(
            gap for gap in self.gaps if abs((gap - self.nominal).ratio(self.nominal)) > limit
        )
