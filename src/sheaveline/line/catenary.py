"""Where the rope actually is between two towers.

Nothing on the tower schedule tells you where the rope sits at midspan, because
the rope is not on the chord. It hangs under it by an amount that depends on
what the rope and the carriers on it weigh and on how hard the tension system is
pulling. Every clearance figure in this system is wrong without it, and by a
long way: a thousand foot span on a quad sags the better part of thirty feet.

The parabola is used rather than the true catenary. At the sag to span ratios a
passenger ropeway runs at, the two differ by less than the survey does, and the
parabola inverts in closed form, which the department needs when it asks the
other question: what tension would hold the rope off that knoll.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal

from sheaveline.errors import SheavelineError
from sheaveline.measures.length import Length
from sheaveline.measures.load import Load, LoadPerFoot
from sheaveline.plant.span import Span


class LineError(SheavelineError):
    """A line geometry question was asked in a way that has no answer."""


@dataclass(frozen=True)
class SagModel:
    """A weight per foot and a horizontal tension, which is all sag needs."""

    weight: LoadPerFoot
    tension: Load

    def __post_init__(self) -> None:
        if self.weight.millipounds_per_foot <= 0:
            raise LineError("a sag model needs a weight on the rope")
        if self.tension.pounds <= 0:
            raise LineError("a sag model needs a tension in the rope")

    def sag_over(self, horizontal: Length) -> Length:
        """Midspan sag below the chord, w S squared over eight H."""
        if horizontal.mils < 0:
            raise LineError("a span has a positive horizontal distance")
        span_feet = horizontal.feet
        drop = self.weight.pounds_per_foot * span_feet * span_feet / (8 * self.tension.pounds)
        return Length.from_feet(drop)

    def sag_at(self, horizontal: Length, along: Length) -> Length:
        """Sag below the chord at a point inside the span."""
        if not Length(0) <= along <= horizontal:
            raise LineError("that point is outside the span")
        if horizontal.mils == 0:
            return Length(0)
        midspan = self.sag_over(horizontal)
        x = along.feet
        s = horizontal.feet
        shape = 4 * x * (s - x) / (s * s)
        return midspan.scaled(shape)

    def rope_elevation(self, span: Span, station: Length) -> Length:
        """Where the rope is, which is the chord less the sag."""
        along = station - span.lower.station
        return span.chord_elevation_at(station) - self.sag_at(span.horizontal(), along)

    def low_point(self, span: Span) -> Length:
        """The station where the rope is lowest, which is not midspan on a slope."""
        horizontal = span.horizontal()
        midspan = self.sag_over(horizontal)
        if midspan.is_zero():
            return span.lower.station if span.rise().mils >= 0 else span.upper.station
        offset = span.rise().ratio(midspan.scaled(4))
        fraction = (Decimal(1) - offset) / 2
        fraction = min(max(fraction, Decimal(0)), Decimal(1))
        return span.lower.station + horizontal.scaled(fraction)

    def tension_for_sag(self, horizontal: Length, wanted: Length) -> Load:
        """What tension would hold the midspan sag down to that figure."""
        if wanted.mils <= 0:
            raise LineError("a wanted sag is a positive length")
        span_feet = horizontal.feet
        pounds = self.weight.pounds_per_foot * span_feet * span_feet / (8 * wanted.feet)
        return Load.from_pounds(pounds)

    def with_tension(self, tension: Load) -> SagModel:
        return SagModel(self.weight, tension)

    def with_weight(self, weight: LoadPerFoot) -> SagModel:
        return SagModel(weight, self.tension)

    def sag_ratio(self, horizontal: Length) -> Decimal:
        """Sag as a fraction of the span, which is how a drawing quotes it."""
        if horizontal.mils == 0:
            raise LineError("a sag ratio needs a span")
        return self.sag_over(horizontal).ratio(horizontal)

    def __str__(self) -> str:
        return f"{self.weight} at {self.tension.format_pounds()}"


def line_weight(
    rope: LoadPerFoot,
    carrier_weight: Load,
    spacing: Length,
) -> LoadPerFoot:
    """Rope plus carriers, smeared along the line the way the sag formula wants."""
    if spacing.mils <= 0:
        raise LineError("carriers on a line have a spacing")
    per_foot = carrier_weight.over(spacing)
    return LoadPerFoot(rope.millipounds_per_foot + per_foot.millipounds_per_foot)
