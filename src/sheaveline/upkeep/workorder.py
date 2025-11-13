"""Work orders, which are what a defect turns into once somebody owns it.

A defect is an observation. A work order is a promise, with a number, a name
against it and a date it should be done by. The two are separate because a
defect can sit in the book for a season with nobody arguing about it, and the
moment it becomes a work order the clock the mountain manager reads starts.

Priority is not typed in. It falls out of what raised the order, because a
serious defect that somebody decided was routine is exactly the thing an
accident report goes looking for afterwards.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import date, timedelta
from enum import Enum
from typing import Final

from sheaveline.plant.identity import LiftCode
from sheaveline.upkeep.interval import UpkeepError
from sheaveline.upkeep.record import Defect, DefectLevel


class Priority(Enum):
    """How long the department has, and what it may run in the meantime."""

    IMMEDIATE = "immediate"
    URGENT = "urgent"
    SCHEDULED = "scheduled"
    ROUTINE = "routine"

    @property
    def target_days(self) -> int:
        return _TARGET_DAYS[self]

    @property
    def stops_the_machine(self) -> bool:
        return self is Priority.IMMEDIATE


_TARGET_DAYS: Final[dict[Priority, int]] = {
    Priority.IMMEDIATE: 0,
    Priority.URGENT: 3,
    Priority.SCHEDULED: 30,
    Priority.ROUTINE: 180,
}

_FROM_DEFECT: Final[dict[DefectLevel, Priority]] = {
    DefectLevel.SERIOUS: Priority.IMMEDIATE,
    DefectLevel.DEFECT: Priority.SCHEDULED,
    DefectLevel.OBSERVATION: Priority.ROUTINE,
}


def priority_for(level: DefectLevel) -> Priority:
    """The one place a defect level turns into a deadline."""
    return _FROM_DEFECT[level]


@dataclass(frozen=True)
class WorkOrder:
    """One job somebody owns, with a number and a date."""

    number: int
    lift: LiftCode
    raised: date
    raised_by: str
    detail: str
    priority: Priority
    where: str | None = None
    closed: date | None = None
    closed_by: str | None = None

    def __post_init__(self) -> None:
        if self.number < 1:
            raise UpkeepError("work orders are numbered from one")
        if not self.raised_by.strip():
            raise UpkeepError(f"work order {self.number} is signed by whoever raised it")
        if not self.detail.strip():
            raise UpkeepError(f"work order {self.number} says nothing")
        if self.closed is not None and self.closed < self.raised:
            raise UpkeepError(f"work order {self.number} closed before it was raised")
        if (self.closed is None) != (self.closed_by is None):
            raise UpkeepError("a work order is closed by somebody on some day, or neither")

    @classmethod
    def from_defect(
        cls,
        number: int,
        lift: LiftCode,
        defect: Defect,
        raised_by: str,
    ) -> WorkOrder:
        """Priority comes off the defect rather than off whoever is typing."""
        return cls(
            number=number,
            lift=lift,
            raised=defect.raised,
            raised_by=raised_by,
            detail=defect.detail,
            priority=priority_for(defect.level),
            where=defect.where,
        )

    def is_open(self) -> bool:
        return self.closed is None

    def due_by(self) -> date:
        return self.raised + timedelta(days=self.priority.target_days)

    def is_overdue(self, on: date) -> bool:
        if not self.is_open():
            return self.closed is not None and self.closed > self.due_by()
        return on > self.due_by()

    def age_in_days(self, on: date) -> int:
        end = self.closed or on
        if end < self.raised:
            raise UpkeepError("that day is before the order was raised")
        return (end - self.raised).days

    def close(self, on: date, by: str) -> WorkOrder:
        if not self.is_open():
            raise UpkeepError(f"work order {self.number} is already closed")
        if not by.strip():
            raise UpkeepError("a work order is closed by somebody")
        return WorkOrder(
            number=self.number,
            lift=self.lift,
            raised=self.raised,
            raised_by=self.raised_by,
            detail=self.detail,
            priority=self.priority,
            where=self.where,
            closed=on,
            closed_by=by,
        )

    def __str__(self) -> str:
        state = "open" if self.is_open() else "closed"
        return f"{self.number} {self.priority.value} on {self.lift}, {self.detail} ({state})"


@dataclass(frozen=True)
class WorkOrderBook:
    """Every order raised on the hill, in number order."""

    orders: tuple[WorkOrder, ...] = ()

    def __post_init__(self) -> None:
        numbers = [order.number for order in self.orders]
        if len(set(numbers)) != len(numbers):
            raise UpkeepError("two work orders share a number")

    @classmethod
    def of(cls, orders: Sequence[WorkOrder] = ()) -> WorkOrderBook:
        return cls(tuple(sorted(orders, key=lambda order: order.number)))

    def next_number(self) -> int:
        return 1 if not self.orders else self.orders[-1].number + 1

    def raise_from(self, lift: LiftCode, defect: Defect, raised_by: str) -> WorkOrderBook:
        order = WorkOrder.from_defect(self.next_number(), lift, defect, raised_by)
        return WorkOrderBook.of([*self.orders, order])

    def get(self, number: int) -> WorkOrder:
        for order in self.orders:
            if order.number == number:
                return order
        raise UpkeepError(f"there is no work order {number}")

    def close(self, number: int, on: date, by: str) -> WorkOrderBook:
        order = self.get(number)
        others = [other for other in self.orders if other.number != number]
        return WorkOrderBook.of([*others, order.close(on, by)])

    def open_orders(self, lift: LiftCode | None = None) -> tuple[WorkOrder, ...]:
        return tuple(
            order
            for order in self.orders
            if order.is_open() and (lift is None or order.lift == lift)
        )

    def overdue(self, on: date, lift: LiftCode | None = None) -> tuple[WorkOrder, ...]:
        return tuple(
            order
            for order in self.orders
            if order.is_overdue(on) and (lift is None or order.lift == lift)
        )

    def of_priority(self, priority: Priority) -> tuple[WorkOrder, ...]:
        return tuple(order for order in self.orders if order.priority is priority)

    def holding_machines(self) -> frozenset[LiftCode]:
        return frozenset(
            order.lift for order in self.open_orders() if order.priority.stops_the_machine
        )

    def at(self, where: str) -> tuple[WorkOrder, ...]:
        return tuple(order for order in self.orders if order.where == where)

    def __len__(self) -> int:
        return len(self.orders)
