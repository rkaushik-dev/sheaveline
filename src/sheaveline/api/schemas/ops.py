"""A day on the hill, on the wire.

The stop bodies are the interesting ones. A client posts a stop with a time and a
kind and nothing else that matters, because everything the department cares about
after that is worked out: whether it is reportable, whether the line has to be
walked before it runs again, and whether it counts against the machine. A client
that could send those would eventually send the convenient answer.

Times go out as clock strings on purpose. The tablet in the terminal shows them
straight through without knowing anything about time zones, and this hill has
never had a machine that ran across one.
"""

from __future__ import annotations

from datetime import date, datetime

from pydantic import BaseModel, Field

from sheaveline.api.schemas.measures import ClockText, DurationText, LiftCodeText
from sheaveline.ops.logbook import DayLog, LogEntry
from sheaveline.ops.meters import MeterReading
from sheaveline.ops.stop import StopEvent, StopKind, StopLog


class StopIn(BaseModel):
    """What an operator sends when they push the button."""

    kind: StopKind
    stopped: datetime
    stopped_by: str = Field(min_length=1)
    note: str = ""


class RestartIn(BaseModel):
    stopped: datetime
    restarted: datetime
    restarted_by: str = Field(min_length=1)
    line_checked: bool = False


class StopOut(BaseModel):
    lift: LiftCodeText
    kind: str
    stopped: datetime
    stopped_by: str
    note: str
    restarted: datetime | None
    restarted_by: str | None
    line_checked: bool
    open: bool
    reportable: bool
    counts_against_the_machine: bool
    length: DurationText | None

    @classmethod
    def of(cls, stop: StopEvent) -> StopOut:
        return cls(
            lift=stop.lift,
            kind=stop.kind.value,
            stopped=stop.stopped,
            stopped_by=stop.stopped_by,
            note=stop.note,
            restarted=stop.restarted,
            restarted_by=stop.restarted_by,
            line_checked=stop.line_checked,
            open=stop.is_open(),
            reportable=stop.kind.is_reportable,
            counts_against_the_machine=stop.kind.counts_against_the_machine,
            length=None if stop.is_open() else stop.length(),
        )


class StopLogOut(BaseModel):
    lift: LiftCodeText
    stops: list[StopOut]
    downtime: DurationText
    chargeable: DurationText

    @classmethod
    def of(cls, log: StopLog) -> StopLogOut:
        return cls(
            lift=log.lift,
            stops=[StopOut.of(one) for one in log.stops],
            downtime=log.downtime(),
            chargeable=log.chargeable_downtime(),
        )


class EntryOut(BaseModel):
    at: datetime
    kind: str
    text: str
    by: str

    @classmethod
    def of(cls, entry: LogEntry) -> EntryOut:
        return cls(at=entry.at, kind=entry.kind.value, text=entry.text, by=entry.by)


class MeterOut(BaseModel):
    lift: LiftCodeText
    day: date
    hours: str
    cycles: int
    read_by: str
    meter_replaced: bool

    @classmethod
    def of(cls, reading: MeterReading) -> MeterOut:
        return cls(
            lift=reading.lift,
            day=reading.day,
            hours=str(reading.hours),
            cycles=reading.cycles,
            read_by=reading.read_by,
            meter_replaced=reading.meter_replaced,
        )


class DayLogOut(BaseModel):
    """One machine on one day, with the answers already worked out."""

    lift: LiftCodeText
    day: str
    first_load: ClockText
    last_load: ClockText
    sweep_by: ClockText
    opened: ClockText | None
    closed: ClockText | None
    sheet_walked: bool
    may_open: bool
    blockers: list[str]
    entries: list[EntryOut]
    meter: MeterOut | None
    scheduled: DurationText

    @classmethod
    def of(cls, log: DayLog) -> DayLogOut:
        return cls(
            lift=log.lift,
            day=log.day.isoformat(),
            first_load=log.window.first_load,
            last_load=log.window.last_load,
            sweep_by=log.window.sweep_by,
            opened=log.opened,
            closed=log.closed,
            sheet_walked=log.sheet is not None and log.sheet.is_complete(),
            may_open=log.may_open(),
            blockers=list(log.blockers()),
            entries=[EntryOut.of(one) for one in log.lines()],
            meter=None if log.meter is None else MeterOut.of(log.meter),
            scheduled=log.scheduled_time(),
        )
