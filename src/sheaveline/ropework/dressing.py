"""When the rope was last dressed, and what it costs to be late.

A haul rope is lubricated from the inside. The dressing the mill puts on works
its way out of the core as the rope bends over the sheaves, and when it is gone
the wires start working against each other dry. That does not show up as a
diameter reading for a long time and then it shows up as broken wires all at
once, which is the wrong order to find things out in.

The interval is measured in rope miles rather than in weeks, because a rope that
ran three hundred hours in a thin winter has not aged the way one that ran a
thousand has, and the calendar cannot tell the difference. Miles come off the
meter history and the line speed, which is the same arithmetic the retirement
forecast uses.

The part worth writing down is what being late costs. A dry rope wears faster, so
the wear rate the forecast is drawn from is not a property of the rope, it is a
property of the rope and how it has been looked after. Overdue dressings scale
the rate, and the scaled rate goes back into the forecast. Nobody should have to
hold that chain in their head in October.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import date
from decimal import Decimal
from typing import Final

from sheaveline.measures.length import Length
from sheaveline.plant.rope import HaulRope
from sheaveline.ropework.wear import RopeworkError

FEET_IN_A_MILE: Final = 5280
DRESSING_INTERVAL: Final = Length.from_feet(FEET_IN_A_MILE * 25_000)
WEAR_PENALTY_A_LATE_INTERVAL: Final = Decimal("0.35")
WORST_PENALTY: Final = Decimal("2.5")


@dataclass(frozen=True)
class Dressing:
    """One application, and how far the rope had run when it went on."""

    rope_serial: str
    on: date
    travel_at: Length
    product: str
    by: str

    def __post_init__(self) -> None:
        if self.travel_at.mils < 0:
            raise RopeworkError("a rope cannot have run a negative distance")
        if not self.product.strip():
            raise RopeworkError("a dressing names what went on the rope")
        if not self.by.strip():
            raise RopeworkError("a dressing names who put it on")

    def miles_at(self) -> Decimal:
        return self.travel_at.feet / FEET_IN_A_MILE

    def __str__(self) -> str:
        return (
            f"{self.rope_serial} dressed {self.on.isoformat()} with {self.product} "
            f"at {self.miles_at():.0f} miles"
        )


@dataclass(frozen=True)
class DressingLog:
    """Every application on one rope, in the order they went on."""

    rope: HaulRope
    dressings: tuple[Dressing, ...] = ()
    interval: Length = DRESSING_INTERVAL

    def __post_init__(self) -> None:
        if self.interval.mils <= 0:
            raise RopeworkError("a dressing interval is a distance")
        for one in self.dressings:
            if one.rope_serial != str(self.rope.serial):
                raise RopeworkError(f"that dressing is on {one.rope_serial}")
        travels = [one.travel_at.mils for one in self.dressings]
        if travels != sorted(travels):
            raise RopeworkError("a rope cannot be dressed at a distance it has not run")

    @classmethod
    def of(
        cls,
        rope: HaulRope,
        dressings: Sequence[Dressing] = (),
        interval: Length = DRESSING_INTERVAL,
    ) -> DressingLog:
        ordered = tuple(sorted(dressings, key=lambda one: (one.travel_at.mils, one.on)))
        return cls(rope, ordered, interval)

    def last(self) -> Dressing | None:
        return self.dressings[-1] if self.dressings else None

    def travel_since(self, travel_now: Length) -> Length:
        """How far it has run since the last dressing, or since it went on the line."""
        found = self.last()
        run = travel_now - (found.travel_at if found is not None else Length(0))
        if run.mils < 0:
            raise RopeworkError("the meter has gone backwards since the last dressing")
        return run

    def is_due(self, travel_now: Length) -> bool:
        return self.travel_since(travel_now) >= self.interval

    def intervals_overdue(self, travel_now: Length) -> Decimal:
        """How many whole intervals late, which is what the wear penalty is scaled on."""
        over = self.travel_since(travel_now).mils - self.interval.mils
        if over <= 0:
            return Decimal(0)
        return Decimal(over) / self.interval.mils

    def travel_until_due(self, travel_now: Length) -> Length:
        left = self.interval.mils - self.travel_since(travel_now).mils
        return Length(max(0, left))

    def wear_penalty(self, travel_now: Length) -> Decimal:
        """What a dry rope costs, as a multiplier on the wear rate.

        One at the interval, rising with every interval missed, and capped. The
        cap is not physics, it is an admission that nobody here has measured a
        rope that far past its dressing and a number invented past the data is
        worse than a number that says it has stopped.
        """
        late = self.intervals_overdue(travel_now)
        if late <= 0:
            return Decimal(1)
        return min(WORST_PENALTY, Decimal(1) + late * WEAR_PENALTY_A_LATE_INTERVAL)

    def miles_run_since(self, travel_now: Length) -> Decimal:
        return self.travel_since(travel_now).feet / FEET_IN_A_MILE

    def __len__(self) -> int:
        return len(self.dressings)

    def __str__(self) -> str:
        found = self.last()
        if found is None:
            return f"{self.rope.serial} has never been dressed"
        return str(found)
