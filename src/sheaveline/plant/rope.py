"""The haul rope, its specification, and the one it actually is.

Two records here and they get confused constantly. The specification is what the
mill makes, a construction and a diameter and a breaking strength, and it is the
same for every reel of that product. The haul rope is the piece of it on this
machine, with a serial, a date it went on, a length and a count of splices in
it. The design factor is a property of the pair and of the tension, never of
either one alone.

Construction matters because it decides how many outer wires there are, and the
broken wire limits downstream are counted against that number rather than
against a flat figure.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from decimal import Decimal
from enum import Enum
from typing import Final

from sheaveline.measures.length import Length
from sheaveline.measures.load import Load, LoadPerFoot
from sheaveline.plant.classes import PlantError
from sheaveline.plant.identity import RopeSerial

MINIMUM_DESIGN_FACTOR: Final = Decimal(4)


class LayDirection(Enum):
    """Which way the strands wind, which decides how wear shows up."""

    RIGHT_REGULAR = "right regular lay"
    LEFT_REGULAR = "left regular lay"
    RIGHT_LANG = "right lang lay"
    LEFT_LANG = "left lang lay"

    @property
    def is_lang(self) -> bool:
        """Lang lay wears flatter across the crowns and hides breaks better."""
        return self in {LayDirection.RIGHT_LANG, LayDirection.LEFT_LANG}


class RopeGrade(Enum):
    """The wire grade the mill drew."""

    IPS = "improved plow steel"
    EIP = "extra improved plow steel"
    EEIP = "extra extra improved plow steel"


class RopeConstruction(Enum):
    """Strands by wires, as the reel tag reads."""

    SIX_BY_TWENTY_FIVE = "6x25 filler wire"
    SIX_BY_TWENTY_SIX = "6x26 warrington seale"
    SIX_BY_THIRTY_SIX = "6x36 warrington seale"
    EIGHT_BY_NINETEEN = "8x19 seale"

    @property
    def strands(self) -> int:
        return _STRANDS[self]

    @property
    def outer_wires_per_strand(self) -> int:
        return _OUTER_WIRES[self]

    @property
    def outer_wires(self) -> int:
        """Every wire that can be seen, and therefore every wire a break is counted in."""
        return self.strands * self.outer_wires_per_strand


_STRANDS: Final[dict[RopeConstruction, int]] = {
    RopeConstruction.SIX_BY_TWENTY_FIVE: 6,
    RopeConstruction.SIX_BY_TWENTY_SIX: 6,
    RopeConstruction.SIX_BY_THIRTY_SIX: 6,
    RopeConstruction.EIGHT_BY_NINETEEN: 8,
}

_OUTER_WIRES: Final[dict[RopeConstruction, int]] = {
    RopeConstruction.SIX_BY_TWENTY_FIVE: 12,
    RopeConstruction.SIX_BY_TWENTY_SIX: 10,
    RopeConstruction.SIX_BY_THIRTY_SIX: 14,
    RopeConstruction.EIGHT_BY_NINETEEN: 9,
}


@dataclass(frozen=True)
class RopeSpec:
    """What the mill makes, before any of it is cut."""

    construction: RopeConstruction
    grade: RopeGrade
    lay: LayDirection
    nominal_diameter: Length
    breaking_strength: Load
    weight: LoadPerFoot

    def __post_init__(self) -> None:
        if self.nominal_diameter.mils <= 0:
            raise PlantError("a rope has a diameter")
        if self.breaking_strength.pounds <= 0:
            raise PlantError("a rope has a breaking strength")
        if self.weight.millipounds_per_foot <= 0:
            raise PlantError("a rope has a weight per foot")

    @property
    def outer_wires(self) -> int:
        return self.construction.outer_wires

    def lay_length(self) -> Length:
        """One full turn of a strand, taken at six and a half diameters."""
        return self.nominal_diameter.scaled("6.5")

    def condemning_diameter(self) -> Length:
        """Ninety three percent of nominal, below which the rope comes off."""
        return self.nominal_diameter.scaled("0.93")

    def weight_of(self, length: Length) -> Load:
        return self.weight.times(length)

    def design_factor_at(self, tension: Load) -> Decimal:
        if tension.pounds <= 0:
            raise PlantError("a design factor needs a tension")
        return self.breaking_strength.ratio(tension)

    def safe_at(self, tension: Load) -> bool:
        return self.design_factor_at(tension) >= MINIMUM_DESIGN_FACTOR

    def maximum_tension(self) -> Load:
        return self.breaking_strength.scaled(Decimal(1) / MINIMUM_DESIGN_FACTOR)

    def __str__(self) -> str:
        return f"{self.nominal_diameter.format_inches(3)} {self.construction.value}"


@dataclass(frozen=True)
class HaulRope:
    """The piece of that rope on this machine."""

    serial: RopeSerial
    spec: RopeSpec
    installed: date
    length: Length
    splices: int = 1

    def __post_init__(self) -> None:
        if self.length.mils <= 0:
            raise PlantError(f"rope {self.serial} has no length")
        if self.splices < 0:
            raise PlantError(f"rope {self.serial} cannot have negative splices")
        if self.installed.year < self.serial.year:
            raise PlantError(f"rope {self.serial} went on before it was drawn")

    @property
    def diameter(self) -> Length:
        return self.spec.nominal_diameter

    def age_in_days(self, on: date) -> int:
        if on < self.installed:
            raise PlantError(f"{on.isoformat()} is before rope {self.serial} went on")
        return (on - self.installed).days

    def seasons_in_service(self, on: date) -> int:
        """Whole years on the machine, which is how the rope file counts."""
        return self.age_in_days(on) // 365

    def weight(self) -> Load:
        return self.spec.weight_of(self.length)

    def is_endless(self) -> bool:
        """A loop has at least one splice. A tram rope has none and two ends."""
        return self.splices > 0

    def with_splice(self) -> HaulRope:
        return HaulRope(
            serial=self.serial,
            spec=self.spec,
            installed=self.installed,
            length=self.length,
            splices=self.splices + 1,
        )

    def __str__(self) -> str:
        return f"{self.serial} {self.spec}, {self.length.feet:.0f} ft"
