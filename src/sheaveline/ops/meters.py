"""Hour meters and cycle counters, and the trouble with reading them.

Two counters on the drive and both of them lie eventually. The hour meter has
five digits and a tenth, so it wraps at ninety nine thousand nine hundred and
ninety nine point nine and starts again at nothing. The cycle counter gets
replaced when the drive controller is replaced and comes back reading zero. A
reading lower than the one before it is one of those two things and the record
has to say which, because the difference is either eleven hours or a hundred
thousand of them.

Everything with an hour based interval on it reads this module, so a wrong
answer here quietly moves a gearbox oil change by a decade.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import date
from decimal import Decimal
from typing import Final

from sheaveline.measures.duration import Duration
from sheaveline.measures.length import Length
from sheaveline.measures.speed import Speed
from sheaveline.ops.preop import OperationsError
from sheaveline.plant.identity import LiftCode

METER_ROLLOVER: Final = Decimal("100000.0")


@dataclass(frozen=True)
class MeterReading:
    """What the two counters said on one morning."""

    lift: LiftCode
    day: date
    hours: Decimal
    cycles: int
    read_by: str
    meter_replaced: bool = False

    def __post_init__(self) -> None:
        if not self.read_by.strip():
            raise OperationsError("a meter reading is signed by whoever took it")
        if self.hours < 0 or self.hours >= METER_ROLLOVER:
            raise OperationsError(f"an hour meter reads under {METER_ROLLOVER}")
        if self.cycles < 0:
            raise OperationsError("a cycle counter does not read negative")

    def running_time(self) -> Duration:
        return Duration.from_seconds(int(self.hours * 3600))

    def __str__(self) -> str:
        return f"{self.lift} {self.day.isoformat()}, {self.hours} hours, {self.cycles:,} cycles"


@dataclass(frozen=True)
class MeterHistory:
    """Every reading taken off one machine, oldest first."""

    lift: LiftCode
    readings: tuple[MeterReading, ...]

    def __post_init__(self) -> None:
        if not self.readings:
            raise OperationsError("a meter history has readings in it")
        for reading in self.readings:
            if reading.lift != self.lift:
                raise OperationsError(f"{reading.lift} does not belong in a {self.lift} history")
        days = [reading.day for reading in self.readings]
        if len(set(days)) != len(days):
            raise OperationsError("a machine is read once a day")

    @classmethod
    def of(cls, lift: LiftCode, readings: Sequence[MeterReading]) -> MeterHistory:
        ordered = tuple(sorted(readings, key=lambda reading: reading.day))
        return cls(lift, ordered)

    def latest(self) -> MeterReading:
        return self.readings[-1]

    def first(self) -> MeterReading:
        return self.readings[0]

    def on(self, day: date) -> MeterReading:
        for reading in self.readings:
            if reading.day == day:
                return reading
        raise OperationsError(f"{self.lift} was not read on {day.isoformat()}")

    def step(self, earlier: MeterReading, later: MeterReading) -> Decimal:
        """Hours between two readings, allowing for a wrap or a new meter."""
        if later.day < earlier.day:
            raise OperationsError("those readings are the wrong way round")
        if later.meter_replaced:
            return later.hours
        if later.hours >= earlier.hours:
            return later.hours - earlier.hours
        return METER_ROLLOVER - earlier.hours + later.hours

    def cycle_step(self, earlier: MeterReading, later: MeterReading) -> int:
        if later.day < earlier.day:
            raise OperationsError("those readings are the wrong way round")
        if later.meter_replaced or later.cycles >= earlier.cycles:
            return later.cycles if later.meter_replaced else later.cycles - earlier.cycles
        raise OperationsError("the cycle counter fell without the meter being replaced")

    def hours_run(self) -> Decimal:
        total = Decimal(0)
        for earlier, later in zip(self.readings, self.readings[1:], strict=False):
            total += self.step(earlier, later)
        return total

    def cycles_run(self) -> int:
        total = 0
        for earlier, later in zip(self.readings, self.readings[1:], strict=False):
            total += self.cycle_step(earlier, later)
        return total

    def hours_on(self, day: date) -> Decimal:
        """What the machine ran on one day, which needs the day before it."""
        current = self.on(day)
        position = self.readings.index(current)
        if position == 0:
            raise OperationsError(f"{day.isoformat()} is the first reading, so it has no day")
        return self.step(self.readings[position - 1], current)

    def rope_travel(self, speed: Speed) -> Length:
        """How far the rope has run, which is what a rope's life is measured in."""
        minutes = self.hours_run() * 60
        return Length(int(Decimal(speed.mils_per_minute) * minutes))

    def rollovers(self) -> int:
        count = 0
        for earlier, later in zip(self.readings, self.readings[1:], strict=False):
            if not later.meter_replaced and later.hours < earlier.hours:
                count += 1
        return count

    def replacements(self) -> tuple[MeterReading, ...]:
        return tuple(reading for reading in self.readings if reading.meter_replaced)

    def with_reading(self, reading: MeterReading) -> MeterHistory:
        return MeterHistory.of(self.lift, [*self.readings, reading])

    def __len__(self) -> int:
        return len(self.readings)
