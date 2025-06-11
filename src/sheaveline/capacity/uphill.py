"""Uphill capacity, and the certificate the board holds a machine to.

Capacity gets quoted three different ways and only one of them is honest. Seats
an hour is what the machine could do with every place filled, which never
happens. Persons an hour is what it does with the loading efficiency the crew
actually achieves, and that is the number the ski school and the patrol plan
against. Vertical feet an hour is the two multiplied by the rise, and it is the
only figure that lets one lift be compared against another.

The certificate is a separate thing again. It is the figure filed with the board
when the machine was accepted, and running above it is not a capacity question,
it is a paperwork question with an inspector on the end of it.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from decimal import Decimal
from typing import Final

from sheaveline.capacity.spacing import CapacityError, LineSpacing
from sheaveline.measures.duration import SECONDS_PER_HOUR
from sheaveline.measures.length import Length
from sheaveline.plant.identity import FilingNumber
from sheaveline.plant.lift import Lift

DEFAULT_OCCUPANCY: Final = Decimal("0.85")
CERTIFICATE_TOLERANCE: Final = Decimal("0.02")


@dataclass(frozen=True)
class UphillCapacity:
    """What one machine moves in an hour at a given spacing and loading."""

    spacing: LineSpacing
    seats: int
    occupancy: Decimal = DEFAULT_OCCUPANCY

    def __post_init__(self) -> None:
        if self.seats < 1:
            raise CapacityError("a carrier has seats")
        if not 0 < self.occupancy <= 1:
            raise CapacityError("loading efficiency is a fraction of the seats")

    @classmethod
    def of(cls, lift: Lift, occupancy: Decimal = DEFAULT_OCCUPANCY) -> UphillCapacity:
        return cls(LineSpacing.of(lift), lift.carriers.kind.seats, occupancy)

    def carriers_per_hour(self) -> int:
        return int(Decimal(SECONDS_PER_HOUR) / self.spacing.interval().seconds)

    def seats_per_hour(self) -> int:
        """Every place filled, which is the figure a brochure prints."""
        return self.carriers_per_hour() * self.seats

    def persons_per_hour(self) -> int:
        """What the crew actually loads, which is the figure worth planning against."""
        return int(Decimal(self.seats_per_hour()) * self.occupancy)

    def vertical_feet_per_hour(self, rise: Length) -> int:
        """Persons an hour times the rise, which is how two lifts are compared."""
        if rise.mils <= 0:
            raise CapacityError("vertical transport needs a rise")
        return int(Decimal(self.persons_per_hour()) * rise.feet)

    def at_occupancy(self, occupancy: Decimal | str) -> UphillCapacity:
        return UphillCapacity(self.spacing, self.seats, Decimal(str(occupancy)))

    def with_spacing(self, spacing: LineSpacing) -> UphillCapacity:
        return UphillCapacity(spacing, self.seats, self.occupancy)

    def carriers_for_persons_per_hour(self, wanted: int) -> int:
        """How many carriers the line needs to make a target, rounded up."""
        if wanted < 1:
            raise CapacityError("a capacity target is a positive number of people")
        per_carrier = self.persons_per_hour() / self.spacing.carriers
        return int(Decimal(wanted / per_carrier).to_integral_value("ROUND_UP"))

    def __str__(self) -> str:
        return f"{self.persons_per_hour():,} people an hour, {self.seats_per_hour():,} seats"


@dataclass(frozen=True)
class CapacityCertificate:
    """The figure filed with the board when the machine was accepted."""

    filing: FilingNumber
    issued: date
    rated_persons_per_hour: int
    rated_carriers: int

    def __post_init__(self) -> None:
        if self.rated_persons_per_hour < 1:
            raise CapacityError("a certificate rates a positive capacity")
        if self.rated_carriers < 1:
            raise CapacityError("a certificate names the carriers it was rated with")

    def band(self) -> tuple[int, int]:
        margin = Decimal(self.rated_persons_per_hour) * CERTIFICATE_TOLERANCE
        return (
            int(Decimal(self.rated_persons_per_hour) - margin),
            int(Decimal(self.rated_persons_per_hour) + margin),
        )

    def agrees_with(self, capacity: UphillCapacity) -> bool:
        low, high = self.band()
        return low <= capacity.persons_per_hour() <= high

    def exceeded_by(self, capacity: UphillCapacity) -> bool:
        """Running above the filed figure, which is a matter for the board."""
        return capacity.persons_per_hour() > self.band()[1]

    def carriers_changed(self, capacity: UphillCapacity) -> int:
        return capacity.spacing.carriers - self.rated_carriers

    def age_in_years(self, on: date) -> int:
        if on < self.issued:
            raise CapacityError(f"{on.isoformat()} is before the certificate was issued")
        years = on.year - self.issued.year
        if (on.month, on.day) < (self.issued.month, self.issued.day):
            years -= 1
        return years

    def __str__(self) -> str:
        return f"{self.filing}, {self.rated_persons_per_hour:,} an hour"
