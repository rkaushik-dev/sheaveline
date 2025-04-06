"""Carriers and the grips that hold them on the rope.

A carrier is the only part of the machine the public touches, and it is also the
part with a serial number painted on it that gets pulled off the line, worked
on, and put back somewhere else in the sequence. So the fleet is a set of
numbered records rather than a count, and the count is derived.

The grip is where the load actually goes into the rope. A fixed grip is
torqued once and checked; a detachable opens and closes twice a lap and is slip
tested on a schedule. A surface lift has a hanger and no grip at all, and the
type system says so rather than carrying a null everyone has to remember.
"""

from __future__ import annotations

from collections.abc import Iterator, Sequence
from dataclasses import dataclass
from datetime import date

from sheaveline.measures.length import Length
from sheaveline.measures.load import PASSENGER_DESIGN_LOAD, Load
from sheaveline.plant.classes import CarrierType, GripType, LiftClass, PlantError


@dataclass(frozen=True)
class Grip:
    """One grip, its spring pack, and the pull it is meant to let go at."""

    kind: GripType
    model: str
    jaws: int
    spring_pack: str
    rated_slip: Load
    installed: date

    def __post_init__(self) -> None:
        if self.kind is GripType.TOW:
            raise PlantError("a tow hanger is not a grip")
        if self.jaws not in (1, 2):
            raise PlantError(f"a grip has one or two jaws, not {self.jaws}")
        if self.rated_slip.pounds <= 0:
            raise PlantError("a grip has a rated slip force")
        if not self.model.strip():
            raise PlantError("a grip has a model")

    def is_slip_tested(self) -> bool:
        return self.kind.is_tested_for_slip

    def age_in_days(self, on: date) -> int:
        if on < self.installed:
            raise PlantError(f"{on.isoformat()} is before the grip went on")
        return (on - self.installed).days

    def holds(self, pull: Load) -> bool:
        """A grip holds anything up to its rated slip and lets go above it."""
        return pull <= self.rated_slip

    def __str__(self) -> str:
        return f"{self.model} {self.kind.value} grip"


@dataclass(frozen=True)
class Carrier:
    """One chair, cabin, car or bar, with the number painted on its back."""

    number: int
    kind: CarrierType
    tare: Load
    hanger: Length
    grip: Grip | None = None
    restraining_bar: bool = False
    in_service: bool = True

    def __post_init__(self) -> None:
        if self.number < 1:
            raise PlantError("carriers are numbered from one")
        if self.tare.pounds <= 0:
            raise PlantError(f"carrier {self.number} has no empty weight")
        if self.hanger.mils <= 0:
            raise PlantError(f"carrier {self.number} has no hanger length")
        if self.kind.is_surface and self.grip is not None:
            raise PlantError(f"carrier {self.number} is a surface carrier and has no grip")
        if not self.kind.is_surface and self.grip is None:
            raise PlantError(f"carrier {self.number} hangs off the rope and needs a grip")
        if self.restraining_bar and self.kind.is_enclosed:
            raise PlantError(f"carrier {self.number} is enclosed and has no bar")

    @property
    def seats(self) -> int:
        return self.kind.seats

    def design_load(self) -> Load:
        """What the board says the carrier is designed to carry."""
        return PASSENGER_DESIGN_LOAD.scaled(self.seats)

    def gross(self) -> Load:
        return self.tare + self.design_load()

    def grip_of(self) -> Grip:
        if self.grip is None:
            raise PlantError(f"carrier {self.number} has no grip")
        return self.grip

    def suits(self, lift_class: LiftClass) -> bool:
        if not lift_class.accepts(self.kind):
            return False
        if self.grip is None:
            return lift_class.grip is GripType.TOW
        return self.grip.kind is lift_class.grip

    def require_suits(self, lift_class: LiftClass) -> None:
        if not self.suits(lift_class):
            raise PlantError(f"carrier {self.number} does not belong on a {lift_class.value}")

    def withdrawn(self) -> Carrier:
        """The same carrier, off the line. Records do not get deleted here."""
        return Carrier(
            number=self.number,
            kind=self.kind,
            tare=self.tare,
            hanger=self.hanger,
            grip=self.grip,
            restraining_bar=self.restraining_bar,
            in_service=False,
        )

    def __str__(self) -> str:
        state = "" if self.in_service else " (off line)"
        return f"carrier {self.number}, {self.kind.value}{state}"


class CarrierSet:
    """Every carrier on one lift, numbered without duplicates."""

    __slots__ = ("_carriers",)

    def __init__(self, carriers: Sequence[Carrier]) -> None:
        ordered = tuple(sorted(carriers, key=lambda carrier: carrier.number))
        if not ordered:
            raise PlantError("a lift has carriers")
        numbers = [carrier.number for carrier in ordered]
        if len(set(numbers)) != len(numbers):
            raise PlantError("two carriers share a number")
        kinds = {carrier.kind for carrier in ordered}
        if len(kinds) > 1:
            raise PlantError("a lift carries one kind of carrier")
        self._carriers = ordered

    @property
    def kind(self) -> CarrierType:
        return self._carriers[0].kind

    def __len__(self) -> int:
        return len(self._carriers)

    def __iter__(self) -> Iterator[Carrier]:
        return iter(self._carriers)

    def __getitem__(self, number: int) -> Carrier:
        for carrier in self._carriers:
            if carrier.number == number:
                return carrier
        raise PlantError(f"there is no carrier {number}")

    def on_line(self) -> tuple[Carrier, ...]:
        return tuple(carrier for carrier in self._carriers if carrier.in_service)

    def withdrawn(self) -> tuple[Carrier, ...]:
        return tuple(carrier for carrier in self._carriers if not carrier.in_service)

    def seats_on_line(self) -> int:
        return sum(carrier.seats for carrier in self.on_line())

    def design_load(self) -> Load:
        total = Load(0)
        for carrier in self.on_line():
            total = total + carrier.design_load()
        return total

    def gross(self) -> Load:
        total = Load(0)
        for carrier in self.on_line():
            total = total + carrier.gross()
        return total

    def require_suits(self, lift_class: LiftClass) -> None:
        for carrier in self._carriers:
            carrier.require_suits(lift_class)

    def missing_numbers(self) -> tuple[int, ...]:
        """Gaps in the painted numbers, which usually means a carrier is in the shop."""
        numbers = {carrier.number for carrier in self._carriers}
        highest = max(numbers)
        return tuple(number for number in range(1, highest + 1) if number not in numbers)
