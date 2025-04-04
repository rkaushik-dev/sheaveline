"""The ground under the line, as the survey crew wrote it down.

Every clearance question needs two numbers at the same station, the rope and the
ground, and only one of them is on the tower schedule. The survey gives the
other as a list of shots along the centreline, so the ground between two shots
is a straight line and anything else is guessing.

Sampling matters more than it looks. A clearance check that only reads the shot
stations will walk straight past a knoll that sits between two of them, so the
sampler steps at a fixed interval and puts the shot stations in as well.
"""

from __future__ import annotations

from collections.abc import Iterator, Sequence
from dataclasses import dataclass

from sheaveline.measures.length import Length
from sheaveline.plant.classes import PlantError
from sheaveline.plant.span import Span

DEFAULT_STEP = Length.from_feet(25)


@dataclass(frozen=True)
class GroundPoint:
    """One shot from the survey, a station and the ground elevation there."""

    station: Length
    elevation: Length

    @classmethod
    def at_feet(cls, station: float | int | str, elevation: float | int | str) -> GroundPoint:
        return cls(Length.from_feet(station), Length.from_feet(elevation))


@dataclass(frozen=True)
class Clearance:
    """How much daylight there is under the rope at one station."""

    station: Length
    rope_elevation: Length
    ground_elevation: Length

    @property
    def height(self) -> Length:
        return self.rope_elevation - self.ground_elevation

    def is_below(self, minimum: Length) -> bool:
        return self.height < minimum

    def __str__(self) -> str:
        return f"{self.height.feet:.1f} ft at station {self.station.feet:.0f}"


class LineProfile:
    """The ground survey along one lift centreline."""

    __slots__ = ("_points",)

    def __init__(self, points: Sequence[GroundPoint]) -> None:
        ordered = tuple(sorted(points, key=lambda point: point.station.mils))
        if len(ordered) < 2:
            raise PlantError("a profile needs at least two survey shots")
        for earlier, later in zip(ordered, ordered[1:], strict=False):
            if later.station == earlier.station:
                raise PlantError(f"two shots share station {later.station.feet:.0f}")
        self._points = ordered

    @classmethod
    def from_survey(cls, shots: Sequence[tuple[float | int, float | int]]) -> LineProfile:
        return cls([GroundPoint.at_feet(station, elevation) for station, elevation in shots])

    @property
    def points(self) -> tuple[GroundPoint, ...]:
        return self._points

    def first_station(self) -> Length:
        return self._points[0].station

    def last_station(self) -> Length:
        return self._points[-1].station

    def covers(self, station: Length) -> bool:
        return self.first_station() <= station <= self.last_station()

    def elevation_at(self, station: Length) -> Length:
        """Straight line between the two shots either side of the station."""
        if not self.covers(station):
            raise PlantError(f"station {station.feet:.0f} is off the survey")
        for lower, upper in zip(self._points, self._points[1:], strict=False):
            if lower.station <= station <= upper.station:
                run = upper.station - lower.station
                along = (station - lower.station).ratio(run)
                return lower.elevation + (upper.elevation - lower.elevation).scaled(along)
        raise PlantError(f"station {station.feet:.0f} fell between no two shots")

    def highest(self) -> GroundPoint:
        return max(self._points, key=lambda point: point.elevation.mils)

    def lowest(self) -> GroundPoint:
        return min(self._points, key=lambda point: point.elevation.mils)

    def stations(self, span: Span, step: Length = DEFAULT_STEP) -> Iterator[Length]:
        """Every station to test inside a span, shots included so a knoll is not missed."""
        if step.mils <= 0:
            raise PlantError("a sampling step has to be positive")
        start = span.lower.station
        end = span.upper.station
        marks = {start.mils, end.mils}
        walk = start
        while walk < end:
            marks.add(walk.mils)
            walk = walk + step
        marks.update(point.station.mils for point in self._points if start <= point.station <= end)
        for mils in sorted(marks):
            yield Length(mils)

    def clearance_at(self, span: Span, station: Length) -> Clearance:
        return Clearance(
            station=station,
            rope_elevation=span.chord_elevation_at(station),
            ground_elevation=self.elevation_at(station),
        )

    def clearances(self, span: Span, step: Length = DEFAULT_STEP) -> Iterator[Clearance]:
        for station in self.stations(span, step):
            yield self.clearance_at(span, station)

    def tightest(self, span: Span, step: Length = DEFAULT_STEP) -> Clearance:
        """The worst clearance in a span, which is the only one anyone acts on."""
        return min(self.clearances(span, step), key=lambda clearance: clearance.height.mils)
