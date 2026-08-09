"""What is on the shelf, against what the line is going to want off it.

The part register knows when a sheave liner is spent. The shelf knows how many
liners are in the shed. Neither of them on its own answers the question the
mechanic asks in October, which is whether the season can be finished without
somebody driving to Salt Lake, and that is the question this file exists for.

The rule that makes it worth writing down is that a part's lead time and its
remaining life are measured on different clocks. A liner wears on cycles and
arrives in weeks, and turning one into the other needs a cycle rate, which is a
thing the meter history knows and the shelf does not. So the rate is asked for
rather than assumed, and a shortage worked out at last winter's rate is labelled
as such rather than presented as a fact.

Nothing here orders anything. It says how many are short and by when.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import date, timedelta
from decimal import Decimal

from sheaveline.plant.identity import LiftCode
from sheaveline.upkeep.interval import UpkeepError
from sheaveline.upkeep.partlife import FittedPart, PartKind, PartRegister


@dataclass(frozen=True)
class ShelfItem:
    """One part number and how many of it are in the shed."""

    kind: PartKind
    on_hand: int
    lead_days: int
    least: int = 0

    def __post_init__(self) -> None:
        if self.on_hand < 0:
            raise UpkeepError(f"{self.kind.code} cannot have {self.on_hand} on the shelf")
        if self.lead_days < 0:
            raise UpkeepError(f"{self.kind.code} cannot arrive before it is ordered")
        if self.least < 0:
            raise UpkeepError(f"{self.kind.code} cannot want fewer than none in reserve")

    def spare(self) -> int:
        """What is on the shelf over and above what has to stay there."""
        return max(0, self.on_hand - self.least)

    def arrives_if_ordered(self, on: date) -> date:
        return on + timedelta(days=self.lead_days)

    def __str__(self) -> str:
        return f"{self.kind.code} {self.on_hand} on hand, {self.lead_days} days out"


@dataclass(frozen=True)
class Shortage:
    """One part number the line is going to want and the shed cannot cover."""

    kind: PartKind
    wanted: int
    on_hand: int
    least: int
    order_by: date | None
    estimated: bool

    def short_by(self) -> int:
        return max(0, self.wanted - max(0, self.on_hand - self.least))

    def is_urgent(self, on: date) -> bool:
        return self.order_by is not None and self.order_by <= on

    def __str__(self) -> str:
        when = "no date" if self.order_by is None else self.order_by.isoformat()
        note = ", estimated" if self.estimated else ""
        return f"{self.kind.code} short {self.short_by()}, order by {when}{note}"


@dataclass(frozen=True)
class Shelf:
    """The shed, read against a machine's fitted parts."""

    items: tuple[ShelfItem, ...] = ()

    def __post_init__(self) -> None:
        codes = [one.kind.code for one in self.items]
        if len(set(codes)) != len(codes):
            raise UpkeepError("a part number is on the shelf once")

    @classmethod
    def of(cls, items: Sequence[ShelfItem] = ()) -> Shelf:
        return cls(tuple(sorted(items, key=lambda one: one.kind.code)))

    def item(self, code: str) -> ShelfItem | None:
        for one in self.items:
            if one.kind.code == code.upper():
                return one
        return None

    def on_hand(self, code: str) -> int:
        found = self.item(code)
        return 0 if found is None else found.on_hand

    def took(self, code: str, count: int = 1) -> Shelf:
        """One off the shelf, which is what fitting a part does to the shed."""
        found = self.item(code)
        if found is None:
            raise UpkeepError(f"there is no {code} on the shelf")
        if found.on_hand < count:
            raise UpkeepError(f"{code} has {found.on_hand} on hand and {count} was asked for")
        return Shelf.of(
            [
                ShelfItem(one.kind, one.on_hand - count, one.lead_days, one.least)
                if one is found
                else one
                for one in self.items
            ]
        )

    def received(self, code: str, count: int) -> Shelf:
        found = self.item(code)
        if found is None:
            raise UpkeepError(f"there is no {code} on the shelf")
        if count < 1:
            raise UpkeepError("a delivery has something in it")
        return Shelf.of(
            [
                ShelfItem(one.kind, one.on_hand + count, one.lead_days, one.least)
                if one is found
                else one
                for one in self.items
            ]
        )

    def __len__(self) -> int:
        return len(self.items)


def days_until_spent(
    part: FittedPart,
    on: date,
    hours: Decimal | None = None,
    cycles: int | None = None,
    cycles_a_day: int | None = None,
    hours_a_day: Decimal | None = None,
) -> int | None:
    """When a fitted part runs out, in days, on whichever clock ends it first.

    The calendar clock needs no rate. The other two do, and a caller that has not
    supplied one gets nothing back rather than a number worked out from a guess.
    """
    soonest: int | None = None
    if part.kind.days is not None:
        due = part.calendar_due()
        if due is not None:
            soonest = max(0, (due - on).days)
    if part.kind.cycles is not None and cycles is not None and cycles_a_day:
        left = part.kind.cycles - part.cycles_used(cycles)
        found = max(0, left // cycles_a_day)
        soonest = found if soonest is None else min(soonest, found)
    if part.kind.hours is not None and hours is not None and hours_a_day:
        remaining = Decimal(part.kind.hours) - part.hours_used(hours)
        found = max(0, int(remaining / hours_a_day))
        soonest = found if soonest is None else min(soonest, found)
    return soonest


def shortages(
    shelf: Shelf,
    register: PartRegister,
    lift: LiftCode,
    on: date,
    hours: Decimal | None = None,
    cycles: int | None = None,
    cycles_a_day: int | None = None,
    hours_a_day: Decimal | None = None,
    within: int = 180,
) -> tuple[Shortage, ...]:
    """Every part number the machine will want inside the window and cannot get."""
    wanted: dict[str, int] = {}
    soonest: dict[str, int] = {}
    for part in register.on_lift(lift):
        left = days_until_spent(part, on, hours, cycles, cycles_a_day, hours_a_day)
        if left is None or left > within:
            continue
        code = part.kind.code
        wanted[code] = wanted.get(code, 0) + 1
        soonest[code] = min(soonest.get(code, left), left)
    found = []
    for code, count in sorted(wanted.items()):
        item = shelf.item(code)
        kind = item.kind if item is not None else _kind_of(register, lift, code)
        spare = 0 if item is None else item.spare()
        if count <= spare:
            continue
        needed_on = on + timedelta(days=soonest[code])
        lead = 0 if item is None else item.lead_days
        found.append(
            Shortage(
                kind=kind,
                wanted=count,
                on_hand=0 if item is None else item.on_hand,
                least=0 if item is None else item.least,
                order_by=needed_on - timedelta(days=lead),
                estimated=cycles_a_day is not None or hours_a_day is not None,
            )
        )
    return tuple(found)


def _kind_of(register: PartRegister, lift: LiftCode, code: str) -> PartKind:
    for part in register.on_lift(lift):
        if part.kind.code == code:
            return part.kind
    raise UpkeepError(f"nothing on {lift} is a {code}")
