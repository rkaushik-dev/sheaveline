"""The tension system, and where its carriage is sitting today.

Something has to keep the rope tight while it stretches in the cold, sags in the
heat and gets shorter every time a splice is made. On this hill that is either a
counterweight hanging in the return terminal or a hydraulic ram pushing the
carriage, and the two behave differently enough that the difference is a type.

A counterweight holds one tension no matter where the carriage sits, because it
is a weight on a rope. A hydraulic ram holds a tension only inside a pressure
band and drifts outside it. A screw take up holds whatever it was wound to and
nothing else, which is why nobody uses one on a passenger lift any more.

The carriage position is the number that ends ropes. Every shortening moves it
by half the length removed, because the loop is doubled, and when it reaches the
end of its travel the rope is finished whatever the caliper says.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from decimal import Decimal
from enum import Enum
from typing import Final

from sheaveline.measures.length import Length
from sheaveline.measures.load import Load
from sheaveline.measures.temperature import Temperature
from sheaveline.ropework.wear import RopeworkError

RESERVE_FRACTION: Final = Decimal("0.10")
HYDRAULIC_BAND: Final = Decimal("0.05")


class TensionKind(Enum):
    """What keeps the rope tight."""

    COUNTERWEIGHT = "counterweight"
    HYDRAULIC = "hydraulic"
    SCREW = "screw take up"

    @property
    def holds_constant(self) -> bool:
        """A weight is a weight wherever the carriage is."""
        return self is TensionKind.COUNTERWEIGHT

    @property
    def is_passive(self) -> bool:
        """No pump, no pressure switch, nothing to fail closed."""
        return self in {TensionKind.COUNTERWEIGHT, TensionKind.SCREW}

    @property
    def allowed_on_passenger_lifts(self) -> bool:
        return self is not TensionKind.SCREW


@dataclass(frozen=True)
class TensionSystem:
    """The system on one lift, with the carriage where it is now."""

    kind: TensionKind
    nominal: Load
    travel: Length
    carriage: Length

    def __post_init__(self) -> None:
        if self.nominal.pounds <= 0:
            raise RopeworkError("a tension system holds a positive tension")
        if self.travel.mils <= 0:
            raise RopeworkError("a tension system has travel")
        if self.carriage.mils < 0:
            raise RopeworkError("the carriage position is measured from the closed end")

    def remaining_out(self) -> Length:
        """How much further the carriage can go before it runs out."""
        return self.travel - self.carriage

    def remaining_in(self) -> Length:
        return self.carriage

    def reserve(self) -> Length:
        return self.travel.scaled(RESERVE_FRACTION)

    def position_percent(self) -> Decimal:
        return self.carriage.ratio(self.travel) * 100

    def is_within_travel(self) -> bool:
        return Length(0) <= self.carriage <= self.travel

    def has_reserve(self) -> bool:
        """Inside the travel and not within a tenth of either stop."""
        reserve = self.reserve()
        return self.remaining_in() >= reserve and self.remaining_out() >= reserve

    def moved_to(self, position: Length) -> TensionSystem:
        return TensionSystem(self.kind, self.nominal, self.travel, position)

    def after_shortening(self, removed: Length) -> TensionSystem:
        """A shortening of the loop moves the carriage by half of it."""
        if removed.mils <= 0:
            raise RopeworkError("a shortening removes a positive length")
        return self.moved_to(self.carriage + removed.scaled("0.5"))

    def shortenings_left(self, typical: Length) -> int:
        """How many more cuts of that size the carriage has room for."""
        if typical.mils <= 0:
            raise RopeworkError("a shortening removes a positive length")
        usable = self.remaining_out() - self.reserve()
        if usable.mils <= 0:
            return 0
        return int(usable.ratio(typical.scaled("0.5")))

    def expected_tension(self) -> Load:
        return self.nominal

    def tolerance(self) -> Load:
        if self.kind.holds_constant:
            return self.nominal.scaled("0.02")
        return self.nominal.scaled(HYDRAULIC_BAND)

    def accepts(self, measured: Load) -> bool:
        difference = abs(measured.pounds - self.nominal.pounds)
        return difference <= self.tolerance().pounds

    def __str__(self) -> str:
        return (
            f"{self.kind.value} at {self.nominal.format_pounds()}, "
            f"carriage {self.position_percent():.0f}% out"
        )


@dataclass(frozen=True)
class TensionReading:
    """What the gauge and the tape said on one morning."""

    taken: date
    carriage: Length
    measured: Load
    temperature: Temperature

    def __post_init__(self) -> None:
        if self.measured.pounds <= 0:
            raise RopeworkError("a tension reading is a positive load")
        if self.carriage.mils < 0:
            raise RopeworkError("a carriage reading is measured from the closed end")

    def deviation_from(self, system: TensionSystem) -> Load:
        return self.measured - system.nominal

    def agrees_with(self, system: TensionSystem) -> bool:
        return system.accepts(self.measured)

    def carriage_moved(self, system: TensionSystem) -> Length:
        return self.carriage - system.carriage

    def __str__(self) -> str:
        return f"{self.measured.format_pounds()} on {self.taken.isoformat()}"
