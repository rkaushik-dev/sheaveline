"""Service status, which is the one word the mountain manager wants.

Four separate bodies of evidence decide whether a machine is fit to carry the
public, and they are held in four different places because they are gathered by
four different people. The ladder says what is overdue. The record book says
what defects are still open. The rope file says what the last survey made of the
haul rope. The line walk says whether the rope clears the ground today.

None of them outranks the others. Any one of them can take a machine out, and a
machine is only in service when all four agree it is. What this module adds is
that the answer comes with its reasons attached, because "out of service" with
no reason is an argument rather than a decision.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from enum import Enum

from sheaveline.line.geometry import GeometryVerdict
from sheaveline.plant.identity import LiftCode
from sheaveline.ropework.wear import WearVerdict
from sheaveline.upkeep.record import RecordBook
from sheaveline.upkeep.schedule import UpkeepSchedule


class ServiceStatus(Enum):
    """What the machine may do today."""

    IN_SERVICE = "in service"
    RESTRICTED = "restricted"
    OUT_OF_SERVICE = "out of service"

    @property
    def may_carry_public(self) -> bool:
        return self is not ServiceStatus.OUT_OF_SERVICE

    @property
    def needs_a_note_on_the_sheet(self) -> bool:
        return self is not ServiceStatus.IN_SERVICE


@dataclass(frozen=True)
class StatusReason:
    """One piece of evidence, and what it costs."""

    source: str
    status: ServiceStatus
    detail: str

    def __str__(self) -> str:
        return f"{self.source}: {self.detail}"


@dataclass(frozen=True)
class ServiceAssessment:
    """One machine on one day, read against everything that is known about it."""

    lift: LiftCode
    on: date
    schedule: UpkeepSchedule
    rope: WearVerdict | None = None
    geometry: GeometryVerdict | None = None

    def __post_init__(self) -> None:
        if self.schedule.lift != self.lift:
            raise ValueError(f"that ladder is for {self.schedule.lift}")

    @property
    def book(self) -> RecordBook:
        return self.schedule.book

    def ladder_reasons(self) -> tuple[StatusReason, ...]:
        found = []
        for item in self.schedule.overdue(self.on):
            status = ServiceStatus.OUT_OF_SERVICE if item.job.critical else ServiceStatus.RESTRICTED
            found.append(
                StatusReason("ladder", status, f"{item.job.code} overdue, {item.job.description}")
            )
        return tuple(found)

    def defect_reasons(self) -> tuple[StatusReason, ...]:
        found = []
        for defect in self.book.open_serious():
            found.append(
                StatusReason(
                    "record book",
                    ServiceStatus.OUT_OF_SERVICE,
                    f"{defect.where} {defect.detail}",
                )
            )
        return tuple(found)

    def rope_reasons(self) -> tuple[StatusReason, ...]:
        if self.rope is None or self.rope is WearVerdict.SERVICEABLE:
            return ()
        if self.rope is WearVerdict.CONDEMNED:
            return (StatusReason("rope file", ServiceStatus.OUT_OF_SERVICE, "rope condemned"),)
        return (StatusReason("rope file", ServiceStatus.RESTRICTED, "rope on watch"),)

    def geometry_reasons(self) -> tuple[StatusReason, ...]:
        if self.geometry is None or self.geometry is GeometryVerdict.FIT:
            return ()
        if self.geometry is GeometryVerdict.UNFIT:
            return (StatusReason("line walk", ServiceStatus.OUT_OF_SERVICE, "line unfit"),)
        return (StatusReason("line walk", ServiceStatus.RESTRICTED, "line restricted"),)

    def reasons(self) -> tuple[StatusReason, ...]:
        return (
            *self.ladder_reasons(),
            *self.defect_reasons(),
            *self.rope_reasons(),
            *self.geometry_reasons(),
        )

    def status(self) -> ServiceStatus:
        """The worst thing any of the four sources says, and nothing else."""
        found = {reason.status for reason in self.reasons()}
        if ServiceStatus.OUT_OF_SERVICE in found:
            return ServiceStatus.OUT_OF_SERVICE
        if ServiceStatus.RESTRICTED in found:
            return ServiceStatus.RESTRICTED
        return ServiceStatus.IN_SERVICE

    def blocking(self) -> tuple[StatusReason, ...]:
        return tuple(
            reason for reason in self.reasons() if reason.status is ServiceStatus.OUT_OF_SERVICE
        )

    def sources_reporting(self) -> frozenset[str]:
        return frozenset(reason.source for reason in self.reasons())

    def may_open(self) -> bool:
        return self.status().may_carry_public

    def __str__(self) -> str:
        count = len(self.reasons())
        if not count:
            return f"{self.lift} in service"
        return f"{self.lift} {self.status().value}, {count} reasons"
