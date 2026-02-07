"""What the upkeep book looks like on the wire.

A work order is raised from a defect rather than typed, so the body a client
sends names the defect and nothing else. The priority is not a field anybody can
fill in, which is the whole point: an order that came off a serious defect is
immediate whatever the person raising it thought.
"""

from __future__ import annotations

from datetime import date

from pydantic import BaseModel, Field

from sheaveline.api.schemas.measures import LiftCodeText
from sheaveline.upkeep.record import Defect, DefectLevel, InspectionRecord
from sheaveline.upkeep.workorder import WorkOrder


class DefectIn(BaseModel):
    where: str = Field(min_length=1)
    level: DefectLevel
    detail: str = Field(min_length=1)

    def to_domain(self, raised: date) -> Defect:
        return Defect(where=self.where, level=self.level, detail=self.detail, raised=raised)


class InspectionIn(BaseModel):
    """What a mechanic signs off after walking one job."""

    code: str = Field(min_length=1, max_length=4)
    done: date
    by: str = Field(min_length=1)
    hours: str | None = None
    cycles: int | None = None
    defects: list[DefectIn] = Field(default_factory=list)


class DefectOut(BaseModel):
    where: str
    level: str
    detail: str
    raised: date
    cleared: date | None
    open: bool

    @classmethod
    def of(cls, defect: Defect) -> DefectOut:
        return cls(
            where=defect.where,
            level=defect.level.value,
            detail=defect.detail,
            raised=defect.raised,
            cleared=defect.cleared,
            open=defect.is_open(),
        )


class InspectionOut(BaseModel):
    lift: LiftCodeText
    code: str
    done: date
    by: str
    clean: bool
    defects: list[DefectOut]

    @classmethod
    def of(cls, record: InspectionRecord) -> InspectionOut:
        return cls(
            lift=record.lift,
            code=record.code,
            done=record.done,
            by=record.by,
            clean=record.was_clean(),
            defects=[DefectOut.of(one) for one in record.defects],
        )


class WorkOrderIn(BaseModel):
    """Raise an order off a defect that is already in the book."""

    where: str = Field(min_length=1)
    detail: str = Field(min_length=1)
    level: DefectLevel
    raised: date
    raised_by: str = Field(min_length=1)


class CloseOrderIn(BaseModel):
    closed: date
    closed_by: str = Field(min_length=1)


class WorkOrderOut(BaseModel):
    number: int
    lift: LiftCodeText
    raised: date
    raised_by: str
    detail: str
    priority: str
    where: str | None
    due_by: date
    open: bool
    closed: date | None
    holds_the_machine: bool

    @classmethod
    def of(cls, order: WorkOrder) -> WorkOrderOut:
        return cls(
            number=order.number,
            lift=order.lift,
            raised=order.raised,
            raised_by=order.raised_by,
            detail=order.detail,
            priority=order.priority.value,
            where=order.where,
            due_by=order.due_by(),
            open=order.is_open(),
            closed=order.closed,
            holds_the_machine=order.is_open() and order.priority.stops_the_machine,
        )
