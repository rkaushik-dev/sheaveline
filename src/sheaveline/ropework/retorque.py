"""Retorquing fixed grips, and the interval that closes up when they come loose.

A fixed grip is two jaws and a stack of bolts, and the bolts relax. Not much and
not fast, but a grip that reads forty inch pounds light has lost some of the
clamp that is the only thing between the chair and the ground. So every grip
gets a wrench on it on an interval, and what the wrench finds before it moves
anything is the number that matters.

The interval is not fixed either. If more than one grip in ten is found slack,
the fleet is telling the department that the interval is too long, and it halves
until a clean round says otherwise.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import date, timedelta
from decimal import Decimal
from typing import Final

from sheaveline.measures.torque import Torque
from sheaveline.plant.carrier import CarrierSet
from sheaveline.plant.classes import GripType
from sheaveline.ropework.wear import RopeworkError

TORQUE_BAND: Final = Decimal("0.10")
SLACK_RATE_TRIGGER: Final = Decimal("0.10")
BASE_INTERVAL_DAYS: Final = 365


@dataclass(frozen=True)
class TorqueCheck:
    """One grip, what the wrench found, and what it was left at."""

    carrier: int
    checked: date
    checker: str
    specified: Torque
    found: Torque
    reset_to: Torque | None = None

    def __post_init__(self) -> None:
        if self.carrier < 1:
            raise RopeworkError("a torque check names the carrier it was on")
        if not self.checker.strip():
            raise RopeworkError("a torque check is signed by whoever ran the wrench")
        if self.specified.is_slack():
            raise RopeworkError("a torque check is read against a specified figure")

    def within_band(self) -> bool:
        return self.found.within(self.specified, TORQUE_BAND)

    def found_slack(self) -> bool:
        """Below the band. Over the band is a different fault and not this one."""
        return self.found < self.specified and not self.within_band()

    def shortfall(self) -> Torque:
        return self.specified - self.found

    def was_reset(self) -> bool:
        return self.reset_to is not None

    def left_correct(self) -> bool:
        final = self.reset_to if self.reset_to is not None else self.found
        return final.within(self.specified, TORQUE_BAND)

    def __str__(self) -> str:
        state = "slack" if self.found_slack() else "in band"
        return f"carrier {self.carrier} found {self.found} {state}"


@dataclass(frozen=True)
class RetorqueCycle:
    """The retorque programme for one fixed grip fleet."""

    fleet: CarrierSet
    specified: Torque
    checks: tuple[TorqueCheck, ...]
    base_interval_days: int = BASE_INTERVAL_DAYS

    def __post_init__(self) -> None:
        for carrier in self.fleet:
            if carrier.grip is None or carrier.grip.kind is not GripType.FIXED:
                raise RopeworkError("only a fixed grip fleet is retorqued on a cycle")
        if self.base_interval_days < 1:
            raise RopeworkError("a retorque interval is at least a day")
        on_line = {carrier.number for carrier in self.fleet}
        for check in self.checks:
            if check.carrier not in on_line:
                raise RopeworkError(f"carrier {check.carrier} is not on this lift")

    @classmethod
    def of(
        cls,
        fleet: CarrierSet,
        specified: Torque,
        checks: Sequence[TorqueCheck] = (),
        base_interval_days: int = BASE_INTERVAL_DAYS,
    ) -> RetorqueCycle:
        ordered = tuple(sorted(checks, key=lambda check: (check.checked, check.carrier)))
        return cls(fleet, specified, ordered, base_interval_days)

    def slack_found(self) -> tuple[TorqueCheck, ...]:
        return tuple(check for check in self.checks if check.found_slack())

    def slack_rate(self) -> Decimal:
        if not self.checks:
            return Decimal(0)
        return Decimal(len(self.slack_found())) / len(self.checks)

    def interval_days(self) -> int:
        """The interval in force, halved while the fleet is coming loose."""
        if self.slack_rate() > SLACK_RATE_TRIGGER:
            return self.base_interval_days // 2
        return self.base_interval_days

    def last_check(self, carrier: int) -> TorqueCheck | None:
        for check in reversed(self.checks):
            if check.carrier == carrier:
                return check
        return None

    def due_on(self, carrier: int, from_day: date) -> date:
        """When a grip next falls due, given the interval in force now."""
        last = self.last_check(carrier)
        start = last.checked if last is not None else from_day
        return start + timedelta(days=self.interval_days())

    def never_checked(self) -> tuple[int, ...]:
        return tuple(
            carrier.number for carrier in self.fleet if self.last_check(carrier.number) is None
        )

    def overdue(self, on: date) -> tuple[int, ...]:
        late = []
        for carrier in self.fleet:
            last = self.last_check(carrier.number)
            if last is None:
                continue
            if on > last.checked + timedelta(days=self.interval_days()):
                late.append(carrier.number)
        return tuple(late)

    def left_wrong(self) -> tuple[TorqueCheck, ...]:
        """Checks that ended with the grip still outside the band."""
        return tuple(check for check in self.checks if not check.left_correct())

    def with_check(self, check: TorqueCheck) -> RetorqueCycle:
        return RetorqueCycle.of(
            self.fleet,
            self.specified,
            [*self.checks, check],
            self.base_interval_days,
        )

    def __str__(self) -> str:
        return (
            f"{len(self.checks)} checks, {len(self.slack_found())} slack, "
            f"interval {self.interval_days()} days"
        )
