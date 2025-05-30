"""How much daylight there is under the carriers, and how much there has to be.

Two numbers have to be worked out at the same station and they both move. What
there is: the chord, less the sag, less the hanger and the carrier under it,
less the snow that will be standing there in February. What there has to be:
whatever the ground under that station is being used for, because twelve feet
over untracked timber is fine and twelve feet over a groomed road with a cat on
it is not.

So a clearance check is not one number against one limit. It is a walk along the
span with both sides of the comparison changing underneath it.
"""

from __future__ import annotations

from collections.abc import Iterator, Sequence
from dataclasses import dataclass
from enum import Enum
from typing import Final

from sheaveline.line.catenary import LineError, SagModel
from sheaveline.measures.length import Length
from sheaveline.plant.profile import LineProfile
from sheaveline.plant.span import Span

SAMPLE_STEP: Final = Length.from_feet(25)


class GroundUse(Enum):
    """What is happening under the rope, which sets the clearance wanted."""

    OPEN = "open ground"
    TRAIL = "groomed trail"
    ROAD = "road"
    STRUCTURE = "structure"
    WATER = "open water"

    @property
    def minimum(self) -> Length:
        return _MINIMUM[self]

    @property
    def is_trafficked(self) -> bool:
        return self in {GroundUse.TRAIL, GroundUse.ROAD}


_MINIMUM: Final[dict[GroundUse, Length]] = {
    GroundUse.OPEN: Length.from_feet(12),
    GroundUse.TRAIL: Length.from_feet(16),
    GroundUse.ROAD: Length.from_feet(24),
    GroundUse.STRUCTURE: Length.from_feet(20),
    GroundUse.WATER: Length.from_feet(30),
}


@dataclass(frozen=True)
class UseSegment:
    """A stretch of the line where the ground is used for one thing."""

    start: Length
    end: Length
    use: GroundUse

    def __post_init__(self) -> None:
        if self.end <= self.start:
            raise LineError("a use segment runs up the line")

    def covers(self, station: Length) -> bool:
        return self.start <= station <= self.end


@dataclass(frozen=True)
class GroundUseMap:
    """What the ground is doing, station by station, with open ground as the default."""

    segments: tuple[UseSegment, ...] = ()

    @classmethod
    def of(cls, segments: Sequence[UseSegment] = ()) -> GroundUseMap:
        ordered = tuple(sorted(segments, key=lambda segment: segment.start.mils))
        return cls(ordered)

    def use_at(self, station: Length) -> GroundUse:
        """The most demanding use recorded at that station."""
        covering = [segment.use for segment in self.segments if segment.covers(station)]
        if not covering:
            return GroundUse.OPEN
        return max(covering, key=lambda use: use.minimum.mils)

    def minimum_at(self, station: Length) -> Length:
        return self.use_at(station).minimum


@dataclass(frozen=True)
class ClearanceReading:
    """One station, what is there and what is wanted."""

    station: Length
    carrier_bottom: Length
    ground: Length
    snow: Length
    required: Length
    use: GroundUse

    @property
    def available(self) -> Length:
        return self.carrier_bottom - (self.ground + self.snow)

    @property
    def margin(self) -> Length:
        return self.available - self.required

    def passes(self) -> bool:
        return self.margin.mils >= 0

    def __str__(self) -> str:
        return (
            f"{self.available.feet:.1f} ft of {self.required.feet:.0f} ft wanted "
            f"at station {self.station.feet:.0f}"
        )


@dataclass(frozen=True)
class ClearanceCheck:
    """A walk of one span with the sag, the snow and the ground use all in hand."""

    span: Span
    ground: LineProfile
    sag: SagModel
    carrier_drop: Length
    snow: Length
    uses: GroundUseMap = GroundUseMap()

    def __post_init__(self) -> None:
        if self.carrier_drop.mils < 0:
            raise LineError("a carrier hangs below the rope, not above it")
        if self.snow.mils < 0:
            raise LineError("a snow allowance is not negative")

    def reading_at(self, station: Length) -> ClearanceReading:
        rope = self.sag.rope_elevation(self.span, station)
        use = self.uses.use_at(station)
        return ClearanceReading(
            station=station,
            carrier_bottom=rope - self.carrier_drop,
            ground=self.ground.elevation_at(station),
            snow=self.snow,
            required=use.minimum,
            use=use,
        )

    def readings(self, step: Length = SAMPLE_STEP) -> Iterator[ClearanceReading]:
        for station in self.ground.stations(self.span, step):
            yield self.reading_at(station)

    def tightest(self, step: Length = SAMPLE_STEP) -> ClearanceReading:
        """The station with the least margin, which is not the station with the least gap."""
        return min(self.readings(step), key=lambda reading: reading.margin.mils)

    def lowest(self, step: Length = SAMPLE_STEP) -> ClearanceReading:
        return min(self.readings(step), key=lambda reading: reading.available.mils)

    def failures(self, step: Length = SAMPLE_STEP) -> tuple[ClearanceReading, ...]:
        return tuple(reading for reading in self.readings(step) if not reading.passes())

    def passes(self, step: Length = SAMPLE_STEP) -> bool:
        return not self.failures(step)

    def with_snow(self, snow: Length) -> ClearanceCheck:
        return ClearanceCheck(self.span, self.ground, self.sag, self.carrier_drop, snow, self.uses)
