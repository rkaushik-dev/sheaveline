"""Incidents, and how long the department has before the board hears about them.

Two things decide the deadline and they are read independently. What happened to
the machine, and what happened to the people. A deropement with nobody hurt is
still a two hour telephone call, because the board wants somebody looking at the
tower before the evidence is groomed over. A twisted ankle at the unload ramp of
a machine that behaved perfectly is a line in the monthly return.

When both fire, the shorter deadline wins. That is the only rule here that is
not a lookup, and it is the one people get wrong, because the instinct is to
file under the more dramatic heading rather than the more urgent one.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timedelta
from enum import Enum
from typing import Final

from sheaveline.errors import SheavelineError
from sheaveline.measures.duration import Duration
from sheaveline.plant.identity import LiftCode

IMMEDIATE: Final = Duration.from_hours(2)
NEXT_DAY: Final = Duration.from_hours(24)
MONTHLY: Final = Duration.from_hours(24 * 30)


class BoardError(SheavelineError):
    """A filing was built or read in a way that cannot hold."""


class InjuryLevel(Enum):
    """What happened to the people, worst first."""

    NONE = "none"
    FIRST_AID = "first aid"
    TRANSPORTED = "transported"
    ADMITTED = "admitted to hospital"
    FATAL = "fatal"

    @property
    def deadline(self) -> Duration:
        return _INJURY_DEADLINE[self]

    @property
    def is_serious(self) -> bool:
        return self in {InjuryLevel.ADMITTED, InjuryLevel.FATAL}


_INJURY_DEADLINE: Final[dict[InjuryLevel, Duration]] = {
    InjuryLevel.NONE: MONTHLY,
    InjuryLevel.FIRST_AID: MONTHLY,
    InjuryLevel.TRANSPORTED: NEXT_DAY,
    InjuryLevel.ADMITTED: IMMEDIATE,
    InjuryLevel.FATAL: IMMEDIATE,
}


class IncidentKind(Enum):
    """What the machine did, or what was done to it."""

    LOADING = "loading"
    UNLOADING = "unloading"
    FALL_FROM_CARRIER = "fall from carrier"
    DEROPEMENT = "deropement"
    ROLLBACK = "rollback"
    ENTANGLEMENT = "entanglement"
    EVACUATION = "evacuation"
    STRUCTURAL = "structural failure"
    ELECTRICAL = "electrical failure"

    @property
    def deadline(self) -> Duration:
        return _KIND_DEADLINE[self]

    @property
    def stops_the_machine(self) -> bool:
        """The machine does not run again until somebody has looked at it."""
        return self in {
            IncidentKind.DEROPEMENT,
            IncidentKind.ROLLBACK,
            IncidentKind.STRUCTURAL,
            IncidentKind.FALL_FROM_CARRIER,
        }


_KIND_DEADLINE: Final[dict[IncidentKind, Duration]] = {
    IncidentKind.LOADING: MONTHLY,
    IncidentKind.UNLOADING: MONTHLY,
    IncidentKind.FALL_FROM_CARRIER: IMMEDIATE,
    IncidentKind.DEROPEMENT: IMMEDIATE,
    IncidentKind.ROLLBACK: IMMEDIATE,
    IncidentKind.ENTANGLEMENT: NEXT_DAY,
    IncidentKind.EVACUATION: NEXT_DAY,
    IncidentKind.STRUCTURAL: IMMEDIATE,
    IncidentKind.ELECTRICAL: NEXT_DAY,
}


@dataclass(frozen=True)
class Incident:
    """One thing that happened on one machine."""

    lift: LiftCode
    at: datetime
    kind: IncidentKind
    injury: InjuryLevel
    people: int
    narrative: str
    reported_by: str

    def __post_init__(self) -> None:
        if self.people < 0:
            raise BoardError("an incident cannot involve fewer than nobody")
        if self.injury is not InjuryLevel.NONE and self.people < 1:
            raise BoardError("somebody was hurt, so somebody was involved")
        if not self.narrative.strip():
            raise BoardError("an incident carries a narrative")
        if not self.reported_by.strip():
            raise BoardError("an incident is signed by whoever reported it")

    def deadline(self) -> Duration:
        """The shorter of the two, which is the one people get wrong."""
        by_kind = self.kind.deadline
        by_injury = self.injury.deadline
        return by_kind if by_kind <= by_injury else by_injury

    def deadline_driver(self) -> str:
        by_kind = self.kind.deadline
        by_injury = self.injury.deadline
        if by_kind < by_injury:
            return "kind"
        if by_injury < by_kind:
            return "injury"
        return "both"

    def due_by(self) -> datetime:
        return self.at + timedelta(seconds=self.deadline().seconds)

    def is_immediate(self) -> bool:
        return self.deadline() == IMMEDIATE

    def goes_in_the_monthly_return(self) -> bool:
        return self.deadline() == MONTHLY

    def holds_the_machine(self) -> bool:
        return self.kind.stops_the_machine or self.injury.is_serious

    def is_overdue(self, now: datetime, filed: datetime | None = None) -> bool:
        against = filed if filed is not None else now
        return against > self.due_by()

    def __str__(self) -> str:
        return f"{self.lift} {self.kind.value} at {self.at:%Y-%m-%d %H:%M}, {self.injury.value}"
