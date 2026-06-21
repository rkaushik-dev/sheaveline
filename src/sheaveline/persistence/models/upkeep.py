"""The tables inspections, defects and work orders live in.

The shape here is the one thing worth reading. A defect hangs off the record
that found it, so it can never exist without an inspection to point at, and a
work order does not hang off the defect at all. It carries the place and the
detail as its own columns.

That looks like duplication and is not. An order is raised from a defect once,
at one moment, with the priority the defect level implied at that moment. If the
order read through to the defect, somebody downgrading a defect in April would
quietly rewrite the priority of a job that was done in January, and the record
of what the department actually decided would be gone.
"""

from __future__ import annotations

from datetime import date
from decimal import Decimal

from sqlalchemy import (
    CheckConstraint,
    Date,
    ForeignKey,
    Index,
    Integer,
    String,
    UniqueConstraint,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from sheaveline.persistence.base import Base
from sheaveline.persistence.models.plant import LiftRow, named_enum
from sheaveline.persistence.types import DecimalColumn, LiftCodeColumn
from sheaveline.plant.identity import LiftCode
from sheaveline.upkeep.record import DefectLevel
from sheaveline.upkeep.workorder import Priority


class InspectionRecordRow(Base):
    __tablename__ = "inspection_records"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    lift_code: Mapped[LiftCode] = mapped_column(LiftCodeColumn, ForeignKey("lifts.code"))
    code: Mapped[str] = mapped_column(String(4))
    done: Mapped[date] = mapped_column(Date)
    by: Mapped[str] = mapped_column(String(64))
    hours: Mapped[Decimal | None] = mapped_column(DecimalColumn, nullable=True)
    cycles: Mapped[int | None] = mapped_column(Integer, nullable=True)

    lift: Mapped[LiftRow] = relationship()
    defects: Mapped[list[DefectRow]] = relationship(
        back_populates="record", cascade="all, delete-orphan"
    )

    __table_args__ = (UniqueConstraint("lift_code", "code", "done", name="one_record_a_job_a_day"),)


class DefectRow(Base):
    __tablename__ = "defects"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    record_id: Mapped[int] = mapped_column(Integer, ForeignKey("inspection_records.id"))
    where: Mapped[str] = mapped_column(String(32))
    level: Mapped[DefectLevel] = mapped_column(named_enum(DefectLevel, "defect_level"))
    detail: Mapped[str] = mapped_column(String(500))
    raised: Mapped[date] = mapped_column(Date)
    cleared: Mapped[date | None] = mapped_column(Date, nullable=True)
    cleared_by: Mapped[str | None] = mapped_column(String(64), nullable=True)

    record: Mapped[InspectionRecordRow] = relationship(back_populates="defects")

    __table_args__ = (
        CheckConstraint("cleared is null or cleared >= raised", name="cleared_after_raised"),
        # What is still open is asked on every morning page and once a machine.
        Index("ix_defects_still_open", "cleared", "raised"),
    )


class WorkOrderRow(Base):
    __tablename__ = "work_orders"

    number: Mapped[int] = mapped_column(Integer, primary_key=True)
    lift_code: Mapped[LiftCode] = mapped_column(LiftCodeColumn, ForeignKey("lifts.code"))
    raised: Mapped[date] = mapped_column(Date)
    raised_by: Mapped[str] = mapped_column(String(64))
    detail: Mapped[str] = mapped_column(String(500))
    priority: Mapped[Priority] = mapped_column(named_enum(Priority, "work_priority"))
    where: Mapped[str | None] = mapped_column(String(32), nullable=True)
    closed: Mapped[date | None] = mapped_column(Date, nullable=True)
    closed_by: Mapped[str | None] = mapped_column(String(64), nullable=True)

    lift: Mapped[LiftRow] = relationship()

    __table_args__ = (
        CheckConstraint("number >= 1", name="orders_from_one"),
        CheckConstraint("closed is null or closed >= raised", name="closed_after_raised"),
        Index("ix_work_orders_open_by_machine", "lift_code", "closed"),
    )
