"""Inspection records, and the defects that come out of them.

A record says a job was done. A defect says what was wrong when it was, and
those have completely different lives. The record is closed the moment it is
written. The defect stays open until somebody fixes it, which may be three
inspections later, and the machine's service status depends on what is still
open rather than on what was found.

Where a defect is matters as much as what it is, because a cracked sheave liner
on tower four and a cracked sheave liner on tower eleven are two defects and the
crew is going to fix them on the same walk.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import date
from decimal import Decimal
from enum import Enum

from sheaveline.plant.identity import LiftCode
from sheaveline.upkeep.interval import Basis, Requirement, UpkeepError
from sheaveline.upkeep.library import requirement


class DefectLevel(Enum):
    """How much a defect costs while it is open."""

    OBSERVATION = "observation"
    DEFECT = "defect"
    SERIOUS = "serious"

    @property
    def stops_the_machine(self) -> bool:
        return self is DefectLevel.SERIOUS

    @property
    def needs_a_work_order(self) -> bool:
        return self is not DefectLevel.OBSERVATION


@dataclass(frozen=True)
class Defect:
    """One thing wrong, where it is and whether it has been dealt with."""

    where: str
    level: DefectLevel
    detail: str
    raised: date
    cleared: date | None = None
    cleared_by: str | None = None

    def __post_init__(self) -> None:
        if not self.where.strip():
            raise UpkeepError("a defect says where it is")
        if not self.detail.strip():
            raise UpkeepError("a defect says what is wrong")
        if self.cleared is not None and self.cleared < self.raised:
            raise UpkeepError("a defect cannot be cleared before it was raised")
        if (self.cleared is None) != (self.cleared_by is None):
            raise UpkeepError("a defect is cleared by somebody on some day, or neither")

    def is_open(self) -> bool:
        return self.cleared is None

    def age_in_days(self, on: date) -> int:
        end = self.cleared or on
        if end < self.raised:
            raise UpkeepError("that day is before the defect was raised")
        return (end - self.raised).days

    def clear(self, on: date, by: str) -> Defect:
        if not self.is_open():
            raise UpkeepError(f"the defect at {self.where} is already cleared")
        if not by.strip():
            raise UpkeepError("a defect is cleared by somebody")
        return Defect(self.where, self.level, self.detail, self.raised, on, by)

    def __str__(self) -> str:
        state = "open" if self.cleared is None else f"cleared {self.cleared.isoformat()}"
        return f"{self.where} {self.level.value}: {self.detail} ({state})"


@dataclass(frozen=True)
class InspectionRecord:
    """One job done to one machine on one day."""

    lift: LiftCode
    code: str
    done: date
    by: str
    hours: Decimal | None = None
    cycles: int | None = None
    defects: tuple[Defect, ...] = ()

    def __post_init__(self) -> None:
        job = requirement(self.code)
        if not self.by.strip():
            raise UpkeepError(f"{self.code} is signed by whoever did it")
        if job.interval.runs_on(Basis.HOURS) and self.hours is None:
            raise UpkeepError(f"{self.code} runs on the hour meter and none was recorded")
        if job.interval.runs_on(Basis.CYCLES) and self.cycles is None:
            raise UpkeepError(f"{self.code} runs on the cycle counter and none was recorded")
        for defect in self.defects:
            if defect.raised < self.done:
                raise UpkeepError("a defect cannot be raised before the inspection that found it")

    @classmethod
    def of(
        cls,
        lift: LiftCode,
        code: str,
        done: date,
        by: str,
        hours: Decimal | None = None,
        cycles: int | None = None,
        defects: Sequence[Defect] = (),
    ) -> InspectionRecord:
        return cls(lift, code.upper(), done, by, hours, cycles, tuple(defects))

    def job(self) -> Requirement:
        return requirement(self.code)

    def open_defects(self) -> tuple[Defect, ...]:
        return tuple(defect for defect in self.defects if defect.is_open())

    def serious(self) -> tuple[Defect, ...]:
        return tuple(defect for defect in self.defects if defect.level is DefectLevel.SERIOUS)

    def open_serious(self) -> tuple[Defect, ...]:
        return tuple(defect for defect in self.open_defects() if defect.level.stops_the_machine)

    def was_clean(self) -> bool:
        return not any(defect.level.needs_a_work_order for defect in self.defects)

    def __str__(self) -> str:
        tail = "clean" if self.was_clean() else f"{len(self.defects)} written up"
        return f"{self.lift} {self.code} on {self.done.isoformat()}, {tail}"


@dataclass(frozen=True)
class RecordBook:
    """Every inspection record on one machine."""

    lift: LiftCode
    records: tuple[InspectionRecord, ...] = ()

    def __post_init__(self) -> None:
        for record in self.records:
            if record.lift != self.lift:
                raise UpkeepError(f"{record.lift} does not belong in a {self.lift} book")

    @classmethod
    def of(cls, lift: LiftCode, records: Sequence[InspectionRecord] = ()) -> RecordBook:
        ordered = tuple(sorted(records, key=lambda record: (record.done, record.code)))
        return cls(lift, ordered)

    def for_code(self, code: str) -> tuple[InspectionRecord, ...]:
        return tuple(record for record in self.records if record.code == code.upper())

    def last_done(self, code: str) -> InspectionRecord | None:
        done = self.for_code(code)
        return done[-1] if done else None

    def open_defects(self) -> tuple[Defect, ...]:
        return tuple(
            defect for record in self.records for defect in record.defects if defect.is_open()
        )

    def open_serious(self) -> tuple[Defect, ...]:
        return tuple(defect for defect in self.open_defects() if defect.level.stops_the_machine)

    def defects_at(self, where: str) -> tuple[Defect, ...]:
        return tuple(defect for defect in self.open_defects() if defect.where == where)

    def with_record(self, record: InspectionRecord) -> RecordBook:
        return RecordBook.of(self.lift, [*self.records, record])

    def __len__(self) -> int:
        return len(self.records)
