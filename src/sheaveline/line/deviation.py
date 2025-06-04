"""How hard the rope presses on a tower, and whether it presses at all.

The tower schedule gives a designed deviation angle. What the rope actually does
is the difference between the slope it arrives on and the slope it leaves on,
and neither of those is the chord slope, because the sag tips the rope up at
both ends of every span. On a long span that correction is bigger than the
deviation itself.

The number this produces is the force into the assembly, tension times the
deviation. On a support that force presses the rope onto the sheaves and more of
it is better. On a hold down it presses upward against them and there is a floor
under it, because a hold down with almost no force on it is a rope waiting to
come out of the sheave train.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal
from typing import Final

from sheaveline.line.catenary import LineError, SagModel
from sheaveline.measures.angle import Angle
from sheaveline.measures.load import Load
from sheaveline.plant.span import Span
from sheaveline.plant.tower import AssemblyKind, Tower

MINIMUM_HOLD_DOWN_FRACTION: Final = Decimal("0.02")
DESIGN_TOLERANCE: Final = Angle.from_degrees("0.25")


def sag_slope(sag: SagModel, span: Span) -> Angle:
    """How far the sag tips the rope up at either end, four d over S.

    A span cannot be built with no length, so there is nothing to guard here.
    """
    horizontal = span.horizontal()
    return Angle.from_percent_grade(sag.sag_over(horizontal).ratio(horizontal) * 400)


def arrival_slope(sag: SagModel, span: Span) -> Angle:
    """The slope the rope is running at as it reaches the upper support."""
    return span.slope() + sag_slope(sag, span)


def departure_slope(sag: SagModel, span: Span) -> Angle:
    """The slope the rope is running at as it leaves the lower support."""
    return span.slope() - sag_slope(sag, span)


@dataclass(frozen=True)
class TowerLoading:
    """One tower, the two spans either side of it, and what the rope does to it."""

    tower: Tower
    below: Span
    above: Span
    sag: SagModel

    def __post_init__(self) -> None:
        if self.below.upper.station != self.tower.station:
            raise LineError(f"{self.tower.ref} is not at the top of the span below it")
        if self.above.lower.station != self.tower.station:
            raise LineError(f"{self.tower.ref} is not at the foot of the span above it")

    def deviation(self) -> Angle:
        """Positive bends the rope down onto the sheaves, negative lifts it off them."""
        return arrival_slope(self.sag, self.below) - departure_slope(self.sag, self.above)

    def designed_deviation(self) -> Angle:
        return self.tower.uphill.deviation + self.tower.downhill.deviation

    def deviation_error(self) -> Angle:
        return self.deviation() - self.designed_deviation()

    def matches_design(self, tolerance: Angle = DESIGN_TOLERANCE) -> bool:
        return abs(self.deviation_error()) <= tolerance

    def radial_force(self) -> Load:
        """Tension times the deviation, which is the force into the assembly."""
        return self.sag.tension.scaled(Decimal(str(self.deviation().radians)))

    def presses_down(self) -> bool:
        return self.deviation().millidegrees > 0

    def assembly_kind_seen(self) -> AssemblyKind:
        """What the rope says the tower is, which need not be what the schedule says."""
        if self.deviation().is_flat():
            return AssemblyKind.COMBINATION
        return AssemblyKind.SUPPORT if self.presses_down() else AssemblyKind.HOLD_DOWN

    def agrees_with_schedule(self) -> bool:
        return self.assembly_kind_seen() in self.tower.kinds()

    def minimum_hold_down_force(self) -> Load:
        return self.sag.tension.scaled(MINIMUM_HOLD_DOWN_FRACTION)

    def lifts_off(self) -> bool:
        """A hold down with too little force on it is a deropement waiting to happen."""
        if not self.tower.is_hold_down():
            return False
        upward = -self.radial_force().pounds
        return upward < self.minimum_hold_down_force().pounds

    def per_sheave(self) -> Load:
        side = self.tower.uphill if self.presses_down() else self.tower.downhill
        magnitude = Load.from_pounds(abs(self.radial_force().pounds))
        return side.per_sheave(magnitude)

    def __str__(self) -> str:
        return (
            f"{self.tower.ref} at {self.deviation().format_degrees()}, "
            f"{abs(self.radial_force().pounds):,} lb into the assembly"
        )
