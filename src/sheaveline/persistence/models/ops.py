"""The tables a day on the hill lives in.

The day log is the parent and almost everything else on a given day hangs off
it, which is how it should be: an inspector asks for the fifteenth of January on
John Paul and gets one row and everything under it.

Stops and holds are the exceptions and sit against the lift rather than against
the day. A wind hold called at four in the afternoon and cleared at nine the
next morning belongs to two days, and forcing it into one of them loses the
question the board actually asks, which is how long the machine was down.
"""

from __future__ import annotations

from datetime import date, datetime
from decimal import Decimal

from sqlalchemy import (
    Boolean,
    CheckConstraint,
    Date,
    DateTime,
    ForeignKey,
    Index,
    Integer,
    String,
    UniqueConstraint,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from sheaveline.ops.logbook import EntryKind
from sheaveline.ops.preop import CheckOutcome
from sheaveline.ops.shift import Post
from sheaveline.ops.stop import StopKind
from sheaveline.persistence.base import Base
from sheaveline.persistence.models.plant import LiftRow, named_enum
from sheaveline.persistence.types import (
    DecimalColumn,
    LiftCodeColumn,
    TimeOfDayColumn,
)
from sheaveline.plant.identity import LiftCode
from sheaveline.schedule.hours import TimeOfDay
from sheaveline.weather.hold import HoldReason


class DayLogRow(Base):
    __tablename__ = "day_logs"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    lift_code: Mapped[LiftCode] = mapped_column(LiftCodeColumn, ForeignKey("lifts.code"))
    day: Mapped[date] = mapped_column(Date)
    first_load: Mapped[TimeOfDay] = mapped_column(TimeOfDayColumn)
    last_load: Mapped[TimeOfDay] = mapped_column(TimeOfDayColumn)
    sweep_by: Mapped[TimeOfDay] = mapped_column(TimeOfDayColumn)
    opened: Mapped[TimeOfDay | None] = mapped_column(TimeOfDayColumn, nullable=True)
    closed: Mapped[TimeOfDay | None] = mapped_column(TimeOfDayColumn, nullable=True)
    mechanic: Mapped[str | None] = mapped_column(String(64), nullable=True)
    operator: Mapped[str | None] = mapped_column(String(64), nullable=True)

    lift: Mapped[LiftRow] = relationship()
    entries: Mapped[list[LogEntryRow]] = relationship(
        back_populates="day_log", cascade="all, delete-orphan"
    )
    checks: Mapped[list[PreOpResultRow]] = relationship(
        back_populates="day_log", cascade="all, delete-orphan"
    )

    __table_args__ = (
        UniqueConstraint("lift_code", "day", name="one_log_a_day"),
        CheckConstraint("last_load >= first_load", name="loads_in_order"),
        CheckConstraint("sweep_by >= last_load", name="sweep_after_last_load"),
    )


class LogEntryRow(Base):
    __tablename__ = "log_entries"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    day_log_id: Mapped[int] = mapped_column(Integer, ForeignKey("day_logs.id"))
    at: Mapped[datetime] = mapped_column(DateTime)
    kind: Mapped[EntryKind] = mapped_column(named_enum(EntryKind, "entry_kind"))
    text: Mapped[str] = mapped_column(String(500))
    by: Mapped[str] = mapped_column(String(64))

    day_log: Mapped[DayLogRow] = relationship(back_populates="entries")


class PreOpResultRow(Base):
    __tablename__ = "preop_results"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    day_log_id: Mapped[int] = mapped_column(Integer, ForeignKey("day_logs.id"))
    code: Mapped[str] = mapped_column(String(4))
    outcome: Mapped[CheckOutcome] = mapped_column(named_enum(CheckOutcome, "check_outcome"))
    by: Mapped[str] = mapped_column(String(64))
    note: Mapped[str] = mapped_column(String(500), default="")

    day_log: Mapped[DayLogRow] = relationship(back_populates="checks")

    __table_args__ = (UniqueConstraint("day_log_id", "code", name="one_result_an_item"),)


class StopRow(Base):
    __tablename__ = "stops"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    lift_code: Mapped[LiftCode] = mapped_column(LiftCodeColumn, ForeignKey("lifts.code"))
    kind: Mapped[StopKind] = mapped_column(named_enum(StopKind, "stop_kind"))
    stopped: Mapped[datetime] = mapped_column(DateTime)
    stopped_by: Mapped[str] = mapped_column(String(64))
    note: Mapped[str] = mapped_column(String(500), default="")
    restarted: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    restarted_by: Mapped[str | None] = mapped_column(String(64), nullable=True)
    line_checked: Mapped[bool] = mapped_column(Boolean, default=False)

    lift: Mapped[LiftRow] = relationship()

    __table_args__ = (
        UniqueConstraint("lift_code", "stopped", name="one_stop_a_moment"),
        CheckConstraint("restarted is null or restarted >= stopped", name="restart_after_stop"),
        # Asking what is stopped right now is the commonest question on the hill
        # and it was reading every stop of every season to answer it.
        Index("ix_stops_still_open", "restarted", "stopped"),
    )


class HoldRow(Base):
    __tablename__ = "holds"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    lift_code: Mapped[LiftCode] = mapped_column(LiftCodeColumn, ForeignKey("lifts.code"))
    reason: Mapped[HoldReason] = mapped_column(named_enum(HoldReason, "hold_reason"))
    started: Mapped[datetime] = mapped_column(DateTime)
    started_by: Mapped[str] = mapped_column(String(64))
    detail: Mapped[str] = mapped_column(String(500), default="")
    ended: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    ended_by: Mapped[str | None] = mapped_column(String(64), nullable=True)

    lift: Mapped[LiftRow] = relationship()

    __table_args__ = (
        CheckConstraint("ended is null or ended >= started", name="hold_ends_after_it_starts"),
        Index("ix_holds_by_machine", "lift_code", "started"),
    )


class MeterReadingRow(Base):
    __tablename__ = "meter_readings"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    lift_code: Mapped[LiftCode] = mapped_column(LiftCodeColumn, ForeignKey("lifts.code"))
    day: Mapped[date] = mapped_column(Date)
    hours: Mapped[Decimal] = mapped_column(DecimalColumn)
    cycles: Mapped[int] = mapped_column(Integer)
    read_by: Mapped[str] = mapped_column(String(64))
    meter_replaced: Mapped[bool] = mapped_column(Boolean, default=False)

    lift: Mapped[LiftRow] = relationship()

    __table_args__ = (
        UniqueConstraint("lift_code", "day", name="one_reading_a_day"),
        CheckConstraint("cycles >= 0", name="cycles_not_negative"),
        CheckConstraint("hours >= 0", name="hours_not_negative"),
    )


class AssignmentRow(Base):
    __tablename__ = "assignments"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    lift_code: Mapped[LiftCode] = mapped_column(LiftCodeColumn, ForeignKey("lifts.code"))
    day: Mapped[date] = mapped_column(Date)
    person: Mapped[str] = mapped_column(String(64))
    post: Mapped[Post] = mapped_column(named_enum(Post, "post"))
    starts: Mapped[TimeOfDay] = mapped_column(TimeOfDayColumn)
    ends: Mapped[TimeOfDay] = mapped_column(TimeOfDayColumn)

    lift: Mapped[LiftRow] = relationship()

    __table_args__ = (
        UniqueConstraint("lift_code", "day", "person", "starts", name="one_shift_a_start"),
        CheckConstraint("ends > starts", name="shift_runs_forwards"),
    )
