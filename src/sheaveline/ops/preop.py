"""The check sheet that gets walked before the first chair carries anybody.

Every morning a mechanic and an operator go round the machine with a list. Some
of the list is the same on every lift and some of it only exists on certain
machines, which is why the sheet is generated from the lift rather than printed
once and photocopied. A detachable has a grip conveyor to check and a fixed grip
does not, and a sheet with a blank line for something the machine does not have
is a sheet people stop reading.

Two kinds of item, and the difference is the whole point. A critical item that
fails stops the machine. Anything else can be deferred to the work order list
with a note, and the lift opens.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import date
from enum import Enum
from typing import Final

from sheaveline.errors import SheavelineError
from sheaveline.plant.classes import LiftClass
from sheaveline.plant.lift import Lift
from sheaveline.plant.terminal import BrakeKind


class OperationsError(SheavelineError):
    """A daily record was built or read in a way that cannot hold."""


class CheckOutcome(Enum):
    """What the walk found at one line of the sheet."""

    PASS = "pass"
    FAIL = "fail"
    DEFERRED = "deferred"

    @property
    def is_satisfied(self) -> bool:
        return self is CheckOutcome.PASS


@dataclass(frozen=True)
class CheckItem:
    """One line of the sheet, and which machines it belongs on."""

    code: str
    description: str
    critical: bool
    classes: frozenset[LiftClass] = frozenset(LiftClass)

    def applies_to(self, lift: Lift) -> bool:
        return lift.lift_class in self.classes

    def __str__(self) -> str:
        return f"{self.code} {self.description}"


ALL_CLASSES: Final = frozenset(LiftClass)
AERIAL: Final = frozenset(cls for cls in LiftClass if cls.is_aerial)
DETACHABLES: Final = frozenset(cls for cls in LiftClass if cls.is_detachable)

STANDARD_SHEET: Final[tuple[CheckItem, ...]] = (
    CheckItem("A1", "service brake set and released", True, ALL_CLASSES),
    CheckItem("A2", "emergency brake tested", True, AERIAL),
    CheckItem("A3", "backstop free", False, ALL_CLASSES),
    CheckItem("B1", "line ridden top to bottom", True, ALL_CLASSES),
    CheckItem("B2", "rope seated in every sheave train", True, ALL_CLASSES),
    CheckItem("B3", "tension carriage free and within travel", True, ALL_CLASSES),
    CheckItem("C1", "grip conveyor and timing checked", True, DETACHABLES),
    CheckItem("C2", "terminal gates and photo eyes", True, DETACHABLES),
    CheckItem("D1", "stop switches at both terminals", True, ALL_CLASSES),
    CheckItem("D2", "telephone or radio to both terminals", True, ALL_CLASSES),
    CheckItem("D3", "evacuation equipment on site and sealed", True, AERIAL),
    CheckItem("E1", "loading ramp groomed", False, ALL_CLASSES),
    CheckItem("E2", "signage in place", False, ALL_CLASSES),
    CheckItem("E3", "first aid kit stocked", False, ALL_CLASSES),
)


@dataclass(frozen=True)
class CheckResult:
    """What the walk recorded against one item."""

    item: CheckItem
    outcome: CheckOutcome
    by: str
    note: str = ""

    def __post_init__(self) -> None:
        if not self.by.strip():
            raise OperationsError(f"{self.item.code} is signed by whoever walked it")
        if self.outcome is not CheckOutcome.PASS and not self.note.strip():
            raise OperationsError(f"{self.item.code} did not pass and carries no note")
        if self.outcome is CheckOutcome.DEFERRED and self.item.critical:
            raise OperationsError(f"{self.item.code} is critical and cannot be deferred")

    def __str__(self) -> str:
        tail = f" ({self.note})" if self.note else ""
        return f"{self.item.code} {self.outcome.value}{tail}"


@dataclass(frozen=True)
class PreOpSheet:
    """One morning walk of one machine."""

    lift: Lift
    day: date
    results: tuple[CheckResult, ...]
    mechanic: str
    operator: str

    def __post_init__(self) -> None:
        if not self.mechanic.strip() or not self.operator.strip():
            raise OperationsError("a sheet carries a mechanic and an operator")
        codes = [result.item.code for result in self.results]
        if len(set(codes)) != len(codes):
            raise OperationsError("an item is walked once")
        for result in self.results:
            if not result.item.applies_to(self.lift):
                raise OperationsError(f"{result.item.code} is not on a {self.lift.code} sheet")

    @classmethod
    def of(
        cls,
        lift: Lift,
        day: date,
        results: Sequence[CheckResult],
        mechanic: str,
        operator: str,
    ) -> PreOpSheet:
        return cls(lift, day, tuple(results), mechanic, operator)

    def items(self) -> tuple[CheckItem, ...]:
        """The sheet this machine actually gets, which is not the whole list."""
        return tuple(item for item in STANDARD_SHEET if item.applies_to(self.lift))

    def result_for(self, code: str) -> CheckResult | None:
        for result in self.results:
            if result.item.code == code:
                return result
        return None

    def missing(self) -> tuple[CheckItem, ...]:
        walked = {result.item.code for result in self.results}
        return tuple(item for item in self.items() if item.code not in walked)

    def failures(self) -> tuple[CheckResult, ...]:
        return tuple(result for result in self.results if result.outcome is CheckOutcome.FAIL)

    def deferrals(self) -> tuple[CheckResult, ...]:
        return tuple(result for result in self.results if result.outcome is CheckOutcome.DEFERRED)

    def critical_failures(self) -> tuple[CheckResult, ...]:
        return tuple(result for result in self.failures() if result.item.critical)

    def is_complete(self) -> bool:
        return not self.missing()

    def may_open(self) -> bool:
        return self.is_complete() and not self.critical_failures()

    def blockers(self) -> tuple[str, ...]:
        reasons = [f"{item.code} not walked" for item in self.missing()]
        reasons += [f"{result.item.code} failed" for result in self.critical_failures()]
        return tuple(reasons)

    def __str__(self) -> str:
        state = "clear to open" if self.may_open() else "not clear"
        return f"{self.lift.code} {self.day.isoformat()}, {state}"


def brake_items() -> tuple[CheckItem, ...]:
    """The brake lines of the sheet, which the mechanic signs separately."""
    wanted = {BrakeKind.SERVICE: "A1", BrakeKind.EMERGENCY: "A2", BrakeKind.BACKSTOP: "A3"}
    codes = set(wanted.values())
    return tuple(item for item in STANDARD_SHEET if item.code in codes)
