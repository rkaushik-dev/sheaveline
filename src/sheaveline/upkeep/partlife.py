"""Parts that wear out, and the two clocks they wear out on.

A sheave liner has a life in cycles because it is worn by rope passing over it.
A gearbox seal has a life in hours because it is worn by turning. A rubber tyre
in a detachable terminal has both, and it also perishes sitting still over a
summer, so it has a calendar life as well and whichever arrives first is the one
that ends it.

The number that matters is how much of the life is used rather than how much is
left, because the crew orders parts on a lead time and a part at ninety percent
has to be on the shelf already.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import date, timedelta
from decimal import Decimal

from sheaveline.plant.identity import LiftCode
from sheaveline.upkeep.interval import Basis, UpkeepError

ORDER_AT = Decimal("0.90")


@dataclass(frozen=True)
class PartKind:
    """What a part is and how long it is meant to last."""

    code: str
    name: str
    hours: int | None = None
    cycles: int | None = None
    days: int | None = None

    def __post_init__(self) -> None:
        if not self.code.strip():
            raise UpkeepError("a part kind has a code")
        if not self.name.strip():
            raise UpkeepError(f"{self.code} has no name")
        if self.hours is None and self.cycles is None and self.days is None:
            raise UpkeepError(f"{self.code} wears out on at least one clock")
        for label, value in (
            ("hours", self.hours),
            ("cycles", self.cycles),
            ("days", self.days),
        ):
            if value is not None and value < 1:
                raise UpkeepError(f"{self.code} has a life of {value} {label}")

    def bases(self) -> frozenset[Basis]:
        found = set()
        if self.hours is not None:
            found.add(Basis.HOURS)
        if self.cycles is not None:
            found.add(Basis.CYCLES)
        if self.days is not None:
            found.add(Basis.CALENDAR)
        return frozenset(found)

    def __str__(self) -> str:
        parts = []
        if self.hours is not None:
            parts.append(f"{self.hours} hours")
        if self.cycles is not None:
            parts.append(f"{self.cycles:,} cycles")
        if self.days is not None:
            parts.append(f"{self.days} days")
        return f"{self.code} {self.name}, " + " or ".join(parts)


@dataclass(frozen=True)
class FittedPart:
    """One part on one machine, with the meters as they read when it went on."""

    kind: PartKind
    lift: LiftCode
    where: str
    fitted: date
    hours_at_fitting: Decimal = Decimal(0)
    cycles_at_fitting: int = 0
    serial: str | None = None

    def __post_init__(self) -> None:
        if not self.where.strip():
            raise UpkeepError(f"{self.kind.code} was fitted nowhere in particular")
        if self.hours_at_fitting < 0 or self.cycles_at_fitting < 0:
            raise UpkeepError("a meter does not read negative")

    def hours_used(self, hours_now: Decimal) -> Decimal:
        if hours_now < self.hours_at_fitting:
            raise UpkeepError("the hour meter has gone backwards")
        return hours_now - self.hours_at_fitting

    def cycles_used(self, cycles_now: int) -> int:
        if cycles_now < self.cycles_at_fitting:
            raise UpkeepError("the cycle counter has gone backwards")
        return cycles_now - self.cycles_at_fitting

    def days_used(self, on: date) -> int:
        if on < self.fitted:
            raise UpkeepError("that day is before the part went on")
        return (on - self.fitted).days

    def readings(self, hours: Decimal | None, cycles: int | None) -> tuple[Decimal, int]:
        """A clock not given has not moved, which is not the same as reading nought."""
        return (
            self.hours_at_fitting if hours is None else hours,
            self.cycles_at_fitting if cycles is None else cycles,
        )

    def used_on(
        self, basis: Basis, on: date, hours: Decimal | None = None, cycles: int | None = None
    ) -> Decimal:
        """How much of one clock's life has gone, as a fraction."""
        ran, turned = self.readings(hours, cycles)
        if basis is Basis.HOURS:
            if self.kind.hours is None:
                return Decimal(0)
            return self.hours_used(ran) / self.kind.hours
        if basis is Basis.CYCLES:
            if self.kind.cycles is None:
                return Decimal(0)
            return Decimal(self.cycles_used(turned)) / self.kind.cycles
        if self.kind.days is None:
            return Decimal(0)
        return Decimal(self.days_used(on)) / self.kind.days

    def used(self, on: date, hours: Decimal | None = None, cycles: int | None = None) -> Decimal:
        """The worst of the clocks, which is the one that ends the part."""
        return max(
            (self.used_on(basis, on, hours, cycles) for basis in self.kind.bases()),
            default=Decimal(0),
        )

    def spent_clock(
        self, on: date, hours: Decimal | None = None, cycles: int | None = None
    ) -> Basis:
        return max(
            self.kind.bases(),
            key=lambda basis: self.used_on(basis, on, hours, cycles),
        )

    def is_due(self, on: date, hours: Decimal | None = None, cycles: int | None = None) -> bool:
        return self.used(on, hours, cycles) >= 1

    def should_be_ordered(
        self, on: date, hours: Decimal | None = None, cycles: int | None = None
    ) -> bool:
        return self.used(on, hours, cycles) >= ORDER_AT

    def calendar_due(self) -> date | None:
        if self.kind.days is None:
            return None
        return self.fitted + timedelta(days=self.kind.days)

    def __str__(self) -> str:
        return f"{self.kind.code} at {self.where}, fitted {self.fitted.isoformat()}"


@dataclass(frozen=True)
class PartRegister:
    """Every part fitted to the hill that has a life on it."""

    parts: tuple[FittedPart, ...] = ()

    @classmethod
    def of(cls, parts: Sequence[FittedPart] = ()) -> PartRegister:
        ordered = tuple(sorted(parts, key=lambda one: (one.lift.value, one.where)))
        return cls(ordered)

    def on_lift(self, lift: LiftCode) -> tuple[FittedPart, ...]:
        return tuple(one for one in self.parts if one.lift == lift)

    def due(
        self, lift: LiftCode, on: date, hours: Decimal | None = None, cycles: int | None = None
    ) -> tuple[FittedPart, ...]:
        return tuple(one for one in self.on_lift(lift) if one.is_due(on, hours, cycles))

    def to_order(
        self, lift: LiftCode, on: date, hours: Decimal | None = None, cycles: int | None = None
    ) -> tuple[FittedPart, ...]:
        return tuple(
            one
            for one in self.on_lift(lift)
            if one.should_be_ordered(on, hours, cycles) and not one.is_due(on, hours, cycles)
        )

    def worst(
        self, lift: LiftCode, on: date, hours: Decimal | None = None, cycles: int | None = None
    ) -> FittedPart | None:
        fitted = self.on_lift(lift)
        if not fitted:
            return None
        return max(fitted, key=lambda one: one.used(on, hours, cycles))

    def replace(self, going: FittedPart, coming: FittedPart) -> PartRegister:
        if going not in self.parts:
            raise UpkeepError(f"{going.kind.code} at {going.where} is not on the register")
        remaining = [one for one in self.parts if one is not going]
        return PartRegister.of([*remaining, coming])

    def with_part(self, part: FittedPart) -> PartRegister:
        return PartRegister.of([*self.parts, part])

    def __len__(self) -> int:
        return len(self.parts)
