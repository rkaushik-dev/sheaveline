"""Spans, which are the pieces of rope between one support and the next.

Almost every question about a lift is really a question about one span. Where
is the rope highest off the ground, how far apart are the carriers when the line
stops, which piece would a rescue crew have to work under. So the line is cut
into spans once, in order, and everything downstream reads the list.

A support is either a terminal or a tower. The terminals are in the list because
the first and last spans are real spans, and on a steep loading terminal they
are the ones with the trouble.
"""

from __future__ import annotations

from collections.abc import Iterator, Sequence
from dataclasses import dataclass
from decimal import Decimal
from enum import Enum

from sheaveline.measures.angle import Angle
from sheaveline.measures.length import Length
from sheaveline.plant.classes import PlantError
from sheaveline.plant.tower import Tower, TowerLine


class SupportKind(Enum):
    TERMINAL = "terminal"
    TOWER = "tower"


@dataclass(frozen=True)
class Support:
    """A place where the rope is held up, and how high it is held."""

    kind: SupportKind
    label: str
    station: Length
    rope_elevation: Length

    @classmethod
    def terminal(cls, label: str, station: Length, rope_elevation: Length) -> Support:
        return cls(SupportKind.TERMINAL, label, station, rope_elevation)

    @classmethod
    def from_tower(cls, tower: Tower) -> Support:
        return cls(
            SupportKind.TOWER,
            str(tower.ref),
            tower.station,
            tower.crossarm_elevation(),
        )

    def is_tower(self) -> bool:
        return self.kind is SupportKind.TOWER

    def __str__(self) -> str:
        return self.label


@dataclass(frozen=True)
class Span:
    """One piece of rope, from the lower support to the upper one."""

    index: int
    lower: Support
    upper: Support

    def __post_init__(self) -> None:
        if self.upper.station <= self.lower.station:
            raise PlantError(f"span {self.index} does not run up the hill")
        if self.index < 1:
            raise PlantError("spans are numbered from one")

    @property
    def label(self) -> str:
        return f"{self.lower.label} to {self.upper.label}"

    def horizontal(self) -> Length:
        """Distance along the ground, which is what the station numbers give."""
        return self.upper.station - self.lower.station

    def rise(self) -> Length:
        """Signed. A span that drops has a negative rise."""
        return self.upper.rope_elevation - self.lower.rope_elevation

    def chord(self) -> Length:
        """Straight line between the two supports, ignoring the sag."""
        run = Decimal(self.horizontal().mils)
        rise = Decimal(self.rise().mils)
        return Length(int((run * run + rise * rise).sqrt().to_integral_value()))

    def slope(self) -> Angle:
        return Angle.from_rise_run(self.rise(), self.horizontal())

    def is_downhill(self) -> bool:
        return self.rise().mils < 0

    def midpoint_station(self) -> Length:
        return self.lower.station + self.horizontal().scaled("0.5")

    def chord_elevation_at(self, station: Length) -> Length:
        """Where the straight chord sits above a station inside the span."""
        if not self.lower.station <= station <= self.upper.station:
            raise PlantError(f"station {station.feet:.0f} is outside span {self.index}")
        along = (station - self.lower.station).ratio(self.horizontal())
        return self.lower.rope_elevation + self.rise().scaled(along)

    def __str__(self) -> str:
        return f"span {self.index}, {self.label}, {self.horizontal().feet:.0f} ft"


class SpanLine:
    """Every span of one lift, from the bottom terminal to the top."""

    __slots__ = ("_spans",)

    def __init__(self, supports: Sequence[Support]) -> None:
        if len(supports) < 2:
            raise PlantError("a line has at least two supports")
        spans = []
        for index, (lower, upper) in enumerate(zip(supports, supports[1:], strict=False), start=1):
            spans.append(Span(index, lower, upper))
        self._spans = tuple(spans)

    @classmethod
    def between(cls, bottom: Support, towers: TowerLine, top: Support) -> SpanLine:
        supports = [bottom, *(Support.from_tower(tower) for tower in towers), top]
        return cls(supports)

    def __len__(self) -> int:
        return len(self._spans)

    def __iter__(self) -> Iterator[Span]:
        return iter(self._spans)

    def __getitem__(self, index: int) -> Span:
        if not 1 <= index <= len(self._spans):
            raise PlantError(f"there is no span {index}")
        return self._spans[index - 1]

    def supports(self) -> tuple[Support, ...]:
        return (self._spans[0].lower, *(span.upper for span in self._spans))

    def slope_length(self) -> Length:
        """The rope path from bottom to top, summed span by span."""
        total = Length(0)
        for span in self._spans:
            total = total + span.chord()
        return total

    def horizontal_length(self) -> Length:
        return self._spans[-1].upper.station - self._spans[0].lower.station

    def vertical_rise(self) -> Length:
        return self._spans[-1].upper.rope_elevation - self._spans[0].lower.rope_elevation

    def average_slope(self) -> Angle:
        return Angle.from_rise_run(self.vertical_rise(), self.horizontal_length())

    def longest(self) -> Span:
        return max(self._spans, key=lambda span: span.horizontal().mils)

    def steepest(self) -> Span:
        return max(self._spans, key=lambda span: abs(span.slope().millidegrees))

    def downhill_spans(self) -> tuple[Span, ...]:
        return tuple(span for span in self._spans if span.is_downhill())

    def containing(self, station: Length) -> Span:
        for span in self._spans:
            if span.lower.station <= station <= span.upper.station:
                return span
        raise PlantError(f"station {station.feet:.0f} is off the line")
