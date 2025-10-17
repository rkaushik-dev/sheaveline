"""Rope diameter and lay length, as the crew measures them on the line.

Two numbers say most of what is worth knowing about a used rope. Diameter falls
as the wires wear and as the core crushes, and it falls fastest where the rope
sits on a sheave. Lay length stretches as the core gives up, and a lay that has
opened out by more than a few percent means the core is going even where the
diameter still reads well.

Both are measured at stations along the rope, counted from the splice, because
that is the only mark on it that does not move.

What the readings mean is not decided here. The survey carries numbers and the
rule table in verdict.py decides what they cost, so there is one place to look
when the board asks where a threshold came from.
"""

from __future__ import annotations

from collections.abc import Iterator, Sequence
from dataclasses import dataclass
from datetime import date
from decimal import Decimal
from enum import Enum

from sheaveline.errors import SheavelineError
from sheaveline.measures.length import Length
from sheaveline.plant.rope import HaulRope


class RopeworkError(SheavelineError):
    """A rope measurement was taken or recorded in a way that cannot hold."""


class WearVerdict(Enum):
    """What the rope file says about a rope after a survey."""

    SERVICEABLE = "serviceable"
    MONITOR = "monitor"
    CONDEMNED = "condemned"

    @property
    def may_run(self) -> bool:
        return self is not WearVerdict.CONDEMNED

    @property
    def needs_a_return_visit(self) -> bool:
        return self is WearVerdict.MONITOR


@dataclass(frozen=True)
class DiameterReading:
    """One caliper reading, taken twice at right angles."""

    station: Length
    across_crowns: Length
    across_valleys: Length

    def __post_init__(self) -> None:
        if self.station.mils < 0:
            raise RopeworkError("a reading station is measured from the splice, forwards")
        for value in (self.across_crowns, self.across_valleys):
            if value.mils <= 0:
                raise RopeworkError("a caliper reading is a positive diameter")

    def mean(self) -> Length:
        return Length((self.across_crowns.mils + self.across_valleys.mils) // 2)

    def ovality(self) -> Length:
        """How far from round the rope has gone at this station."""
        return abs(self.across_crowns - self.across_valleys)

    def __str__(self) -> str:
        return f"{self.mean().format_inches(3)} at {self.station.feet:.0f} ft"


@dataclass(frozen=True)
class LayReading:
    """A lay length, measured over six strands with a rule."""

    station: Length
    measured: Length

    def __post_init__(self) -> None:
        if self.measured.mils <= 0:
            raise RopeworkError("a lay reading is a positive length")

    def stretch_against(self, nominal: Length) -> Decimal:
        """How far the lay has opened out, as a fraction of what it should be."""
        return (self.measured - nominal).ratio(nominal)


@dataclass(frozen=True)
class WearSurvey:
    """One walk of the rope with a caliper and a rule."""

    rope: HaulRope
    taken: date
    inspector: str
    diameters: tuple[DiameterReading, ...]
    lays: tuple[LayReading, ...] = ()

    def __post_init__(self) -> None:
        if not self.inspector.strip():
            raise RopeworkError("a survey is signed by whoever took it")
        if not self.diameters:
            raise RopeworkError("a survey has at least one diameter reading")
        if self.taken < self.rope.installed:
            raise RopeworkError("a survey cannot predate the rope it is of")
        for reading in self.diameters:
            if reading.station > self.rope.length:
                raise RopeworkError(f"station {reading.station.feet:.0f} is past the end")

    @classmethod
    def of(
        cls,
        rope: HaulRope,
        taken: date,
        inspector: str,
        diameters: Sequence[DiameterReading],
        lays: Sequence[LayReading] = (),
    ) -> WearSurvey:
        return cls(rope, taken, inspector, tuple(diameters), tuple(lays))

    @property
    def nominal(self) -> Length:
        return self.rope.diameter

    def smallest(self) -> DiameterReading:
        return min(self.diameters, key=lambda reading: reading.mean().mils)

    def worst_ovality(self) -> DiameterReading:
        return max(self.diameters, key=lambda reading: reading.ovality().mils)

    def percent_of_nominal(self, reading: DiameterReading) -> Decimal:
        return reading.mean().ratio(self.nominal) * 100

    def below_condemning(self) -> tuple[DiameterReading, ...]:
        limit = self.rope.spec.condemning_diameter()
        return tuple(reading for reading in self.diameters if reading.mean() < limit)

    def worst_lay_stretch(self) -> Decimal:
        if not self.lays:
            return Decimal(0)
        nominal = self.rope.spec.lay_length()
        return max(reading.stretch_against(nominal) for reading in self.lays)

    def stations(self) -> Iterator[Length]:
        for reading in self.diameters:
            yield reading.station

    def __str__(self) -> str:
        return f"{self.rope.serial} walked on {self.taken.isoformat()}"
