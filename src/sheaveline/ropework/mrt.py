"""Magnetic rope testing, and how often a rope has to have one.

A caliper reads the outside of the rope. The magnetic test reads the inside, and
on a haul rope the inside is where the trouble starts, because the core crushes
and the inner wires break long before anything shows on the crowns. The test
comes back as a trace with two kinds of mark on it. Loss of metallic area is a
gradual thinning measured as a percentage. A local fault is a step in the trace
at one place, which is a broken wire or a group of them.

How often a rope is tested is not a fixed interval. A new rope gets four years
before its first test, then two years between tests, and once it is ten years
old it is tested every year. So the interval depends on the rope's age at the
time the question is asked, which is not the same as its age at the last test.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import date, timedelta
from decimal import Decimal
from enum import Enum
from typing import Final

from sheaveline.measures.length import Length
from sheaveline.plant.rope import HaulRope
from sheaveline.ropework.wear import RopeworkError

FIRST_TEST_YEARS: Final = 4
MID_LIFE_YEARS: Final = 10
MID_LIFE_INTERVAL_YEARS: Final = 2
LATE_LIFE_INTERVAL_YEARS: Final = 1

LMA_CONDEMN: Final = Decimal(10)
LMA_WATCH: Final = Decimal(6)
LOCAL_FAULT_CONDEMN: Final = 4
LOCAL_FAULT_WATCH: Final = 2


class IndicationKind(Enum):
    """What the trace showed."""

    LOSS_OF_AREA = "loss of metallic area"
    LOCAL_FAULT = "local fault"
    CORROSION = "corrosion"

    @property
    def is_measured_as_percent(self) -> bool:
        return self is not IndicationKind.LOCAL_FAULT


@dataclass(frozen=True)
class Indication:
    """One mark on the trace, at a station along the rope."""

    station: Length
    kind: IndicationKind
    magnitude: Decimal

    def __post_init__(self) -> None:
        if self.station.mils < 0:
            raise RopeworkError("an indication station is measured from the splice, forwards")
        if self.magnitude <= 0:
            raise RopeworkError("an indication with no magnitude is not an indication")
        if self.kind.is_measured_as_percent and self.magnitude > 100:
            raise RopeworkError("a percentage indication above a hundred is a bad trace")
        if not self.kind.is_measured_as_percent and self.magnitude != self.magnitude.to_integral():
            raise RopeworkError("a local fault is counted in whole wires")

    @classmethod
    def loss(cls, station: Length, percent: str | Decimal) -> Indication:
        return cls(station, IndicationKind.LOSS_OF_AREA, Decimal(str(percent)))

    @classmethod
    def fault(cls, station: Length, wires: int) -> Indication:
        return cls(station, IndicationKind.LOCAL_FAULT, Decimal(wires))

    def __str__(self) -> str:
        if self.kind.is_measured_as_percent:
            return f"{self.magnitude}% {self.kind.value} at {self.station.feet:.0f} ft"
        return f"{self.magnitude:.0f} wire local fault at {self.station.feet:.0f} ft"


@dataclass(frozen=True)
class MagneticTest:
    """One pass of the instrument over the whole rope."""

    rope: HaulRope
    taken: date
    contractor: str
    instrument: str
    indications: tuple[Indication, ...]

    def __post_init__(self) -> None:
        if not self.contractor.strip():
            raise RopeworkError("a magnetic test names the outfit that ran it")
        if not self.instrument.strip():
            raise RopeworkError("a magnetic test names the instrument")
        if self.taken < self.rope.installed:
            raise RopeworkError("a test cannot predate the rope it is of")
        for mark in self.indications:
            if mark.station > self.rope.length:
                raise RopeworkError(f"station {mark.station.feet:.0f} is past the end")

    @classmethod
    def of(
        cls,
        rope: HaulRope,
        taken: date,
        contractor: str,
        instrument: str,
        indications: Sequence[Indication] = (),
    ) -> MagneticTest:
        return cls(rope, taken, contractor, instrument, tuple(indications))

    def of_kind(self, kind: IndicationKind) -> tuple[Indication, ...]:
        return tuple(mark for mark in self.indications if mark.kind is kind)

    def peak_loss(self) -> Decimal:
        losses = self.of_kind(IndicationKind.LOSS_OF_AREA)
        return max((mark.magnitude for mark in losses), default=Decimal(0))

    def largest_fault(self) -> int:
        faults = self.of_kind(IndicationKind.LOCAL_FAULT)
        return int(max((mark.magnitude for mark in faults), default=Decimal(0)))

    def __str__(self) -> str:
        return f"{self.rope.serial} tested {self.taken.isoformat()}, {len(self.indications)} marks"


@dataclass(frozen=True)
class RetestSchedule:
    """When the next magnetic test falls due."""

    rope: HaulRope
    last_test: date | None = None

    def __post_init__(self) -> None:
        if self.last_test is not None and self.last_test < self.rope.installed:
            raise RopeworkError("a test cannot predate the rope it is of")

    def interval_years(self, on: date) -> int:
        """The interval in force at that date, which turns on the rope's age then."""
        age = self.rope.seasons_in_service(on)
        if age >= MID_LIFE_YEARS:
            return LATE_LIFE_INTERVAL_YEARS
        return MID_LIFE_INTERVAL_YEARS

    def next_due(self, on: date) -> date:
        """When the rope is next due, read as at a given date."""
        if self.last_test is None:
            return _add_years(self.rope.installed, FIRST_TEST_YEARS)
        return _add_years(self.last_test, self.interval_years(on))

    def is_overdue(self, on: date) -> bool:
        return on > self.next_due(on)

    def days_until_due(self, on: date) -> int:
        return (self.next_due(on) - on).days

    def after(self, test: MagneticTest) -> RetestSchedule:
        if test.rope.serial != self.rope.serial:
            raise RopeworkError("that test is of another rope")
        return RetestSchedule(self.rope, test.taken)


def _add_years(day: date, years: int) -> date:
    try:
        return day.replace(year=day.year + years)
    except ValueError:
        # The twenty ninth of February in a year that has no twenty ninth.
        return day.replace(year=day.year + years, day=day.day - 1) + timedelta(days=1)
