"""Where every carrier ends up when the rope stops, and how far off the ground.

An evacuation plan is not about the lift, it is about the eighty four places a
chair can be sitting when the drive fails. Two of those places are twelve feet
over a groomed trail and somebody walks people down a ladder. Two more are
ninety feet over a gully and a rope team has to get to them first, which is a
different plan, a different kit and about forty minutes.

Where the carriers sit depends on where the rope stopped, and that is not known
in advance. So the planning number is the worst the machine can do: the stop
offset that puts the most people in the awkward places.
"""

from __future__ import annotations

from collections.abc import Iterator
from dataclasses import dataclass
from decimal import Decimal
from typing import Final

from sheaveline.errors import SheavelineError
from sheaveline.line.catenary import SagModel
from sheaveline.measures.length import Length
from sheaveline.plant.lift import Lift
from sheaveline.plant.span import Span, SpanLine

OFFSET_SAMPLES: Final = 12


class EvacuationError(SheavelineError):
    """An evacuation question was asked in a way that has no answer."""


class Side:
    """Which way a carrier is travelling, which decides which side it hangs on."""

    UP: Final = "up"
    DOWN: Final = "down"


@dataclass(frozen=True)
class CarrierStop:
    """One carrier, where it stopped and how far it is off the ground."""

    number: int
    side: str
    span: Span
    station: Length
    rope_elevation: Length
    ground_elevation: Length
    seat_height: Length

    @property
    def height(self) -> Length:
        return self.seat_height - self.ground_elevation

    def is_above(self, limit: Length) -> bool:
        return self.height > limit

    def __str__(self) -> str:
        return (
            f"carrier {self.number} {self.side} in span {self.span.index}, "
            f"{self.height.feet:.0f} ft up"
        )


@dataclass(frozen=True)
class EvacuationProfile:
    """One machine, one set of conditions, and where the carriers can stop."""

    lift: Lift
    sag: SagModel
    snow: Length = Length(0)

    def __post_init__(self) -> None:
        if self.snow.mils < 0:
            raise EvacuationError("a snow allowance is not negative")

    def spans(self) -> SpanLine:
        return self.lift.spans()

    def slope_length(self) -> Length:
        return self.lift.slope_length()

    def loop_length(self) -> Length:
        """Both sides of the line, ignoring the few feet round the bullwheels."""
        return self.slope_length().scaled(2)

    def spacing(self) -> Length:
        carriers = self.lift.carriers_on_line()
        if carriers < 1:
            raise EvacuationError("a line with no carriers has nothing to evacuate")
        return Length(self.loop_length().mils // carriers)

    def station_at(self, along: Length) -> tuple[Span, Length]:
        """Turn a distance measured along the rope into a span and a station."""
        if along.mils < 0 or along > self.slope_length():
            raise EvacuationError("that distance is off the line")
        walked = Length(0)
        for span in self.spans():
            chord = span.chord()
            if along <= walked + chord:
                into = along - walked
                fraction = into.ratio(chord) if chord.mils else Decimal(0)
                station = span.lower.station + span.horizontal().scaled(fraction)
                return span, station
            walked = walked + chord
        last = self.spans()[len(self.spans())]
        return last, last.upper.station

    def carrier_at(self, number: int, along: Length, side: str) -> CarrierStop:
        span, station = self.station_at(along)
        rope = self.sag.rope_elevation(span, station)
        ground = self.lift.ground.elevation_at(station) + self.snow
        hanger = max(carrier.hanger for carrier in self.lift.carriers.on_line())
        return CarrierStop(
            number=number,
            side=side,
            span=span,
            station=station,
            rope_elevation=rope,
            ground_elevation=ground,
            seat_height=rope - hanger,
        )

    def stops_at_offset(self, offset: Length) -> Iterator[CarrierStop]:
        """Every carrier on the line when the rope stops at that offset."""
        spacing = self.spacing()
        if offset.mils < 0 or offset >= spacing:
            raise EvacuationError("an offset is inside one carrier spacing")
        slope = self.slope_length()
        loop = self.loop_length()
        for index in range(self.lift.carriers_on_line()):
            around = Length((offset.mils + index * spacing.mils) % loop.mils)
            if around <= slope:
                yield self.carrier_at(index + 1, around, Side.UP)
            else:
                yield self.carrier_at(index + 1, loop - around, Side.DOWN)

    def worst_offset(self, limit: Length) -> Length:
        """The stop that strands the most people above a height, sampled across the spacing."""
        spacing = self.spacing()
        best = Length(0)
        most = -1
        for step in range(OFFSET_SAMPLES):
            offset = Length(spacing.mils * step // OFFSET_SAMPLES)
            count = sum(1 for stop in self.stops_at_offset(offset) if stop.is_above(limit))
            if count > most:
                most = count
                best = offset
        return best

    def highest(self, offset: Length) -> CarrierStop:
        stops = list(self.stops_at_offset(offset))
        if not stops:
            raise EvacuationError("no carriers stopped on the line")
        return max(stops, key=lambda stop: stop.height.mils)

    def above(self, offset: Length, limit: Length) -> tuple[CarrierStop, ...]:
        return tuple(stop for stop in self.stops_at_offset(offset) if stop.is_above(limit))

    def count_at(self, offset: Length) -> int:
        return sum(1 for _ in self.stops_at_offset(offset))

    def with_snow(self, snow: Length) -> EvacuationProfile:
        return EvacuationProfile(self.lift, self.sag, snow)
