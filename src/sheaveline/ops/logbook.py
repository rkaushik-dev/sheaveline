"""The day log, which is the one document an inspector always asks for.

Everything else in here is a record of one thing. This is the record of a day on
one machine, and it is the only place the pieces meet: the morning walk, the
time the first chair went out, every stop, every hold, the meter reading at the
end and whatever the operators thought was worth writing down.

Opening is the interesting part. A lift opens when four separate things are all
true at once, and the reason to put that in one place is that three of them are
usually fine and the fourth is the one that changes.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass, replace
from datetime import date, datetime
from enum import Enum

from sheaveline.measures.duration import Duration
from sheaveline.ops.meters import MeterReading
from sheaveline.ops.preop import OperationsError, PreOpSheet
from sheaveline.ops.stop import StopLog
from sheaveline.plant.identity import LiftCode
from sheaveline.schedule.hours import OperatingWindow, TimeOfDay
from sheaveline.weather.holdlog import HoldRegister


class EntryKind(Enum):
    """What sort of line this is in the book."""

    NOTE = "note"
    OPENED = "opened"
    CLOSED = "closed"
    HANDOVER = "handover"
    VISITOR = "visitor"

    @property
    def is_structural(self) -> bool:
        """Lines the machine writes itself rather than lines somebody types."""
        return self in {EntryKind.OPENED, EntryKind.CLOSED}


@dataclass(frozen=True)
class LogEntry:
    """One line in the book."""

    at: datetime
    kind: EntryKind
    text: str
    by: str

    def __post_init__(self) -> None:
        if not self.text.strip():
            raise OperationsError("a log line says something")
        if not self.by.strip():
            raise OperationsError("a log line is signed")

    def __str__(self) -> str:
        return f"{self.at:%H:%M} {self.by}: {self.text}"


@dataclass(frozen=True)
class DayLog:
    """One machine, one day, and everything that happened on it."""

    lift: LiftCode
    day: date
    window: OperatingWindow
    sheet: PreOpSheet | None = None
    holds: HoldRegister = HoldRegister()
    stops: StopLog | None = None
    entries: tuple[LogEntry, ...] = ()
    opened: TimeOfDay | None = None
    closed: TimeOfDay | None = None
    meter: MeterReading | None = None

    def __post_init__(self) -> None:
        if self.sheet is not None and self.sheet.lift.code != self.lift:
            raise OperationsError("that sheet is for another machine")
        if self.stops is not None and self.stops.lift != self.lift:
            raise OperationsError("that stop log is for another machine")
        if self.closed is not None and self.opened is None:
            raise OperationsError("a machine that never opened cannot close")
        if self.opened is not None and self.closed is not None and self.closed < self.opened:
            raise OperationsError("a machine cannot close before it opens")

    def stop_log(self) -> StopLog:
        return self.stops if self.stops is not None else StopLog.of(self.lift)

    def blockers(self) -> tuple[str, ...]:
        """Everything standing between this machine and the first chair."""
        reasons: list[str] = []
        if self.sheet is None:
            reasons.append("no pre opening sheet")
        else:
            reasons.extend(self.sheet.blockers())
        for record in self.holds.open_holds(self.lift):
            reasons.append(f"{record.reason.value} hold open")
        for stop in self.stop_log().open_stops():
            reasons.append(f"{stop.kind.value} not cleared")
        return tuple(reasons)

    def may_open(self) -> bool:
        return not self.blockers()

    def is_open(self) -> bool:
        return self.opened is not None and self.closed is None

    def open_at(self, at: TimeOfDay, by: str) -> DayLog:
        if self.opened is not None:
            raise OperationsError(f"{self.lift} already opened at {self.opened}")
        if not self.may_open():
            raise OperationsError(f"{self.lift} is not clear to open: {self.blockers()[0]}")
        entry = LogEntry(
            at=datetime.combine(self.day, at.to_time()),
            kind=EntryKind.OPENED,
            text=f"first load at {at}",
            by=by,
        )
        return replace(self, opened=at, entries=(*self.entries, entry))

    def close_at(self, at: TimeOfDay, by: str) -> DayLog:
        if self.opened is None:
            raise OperationsError(f"{self.lift} never opened")
        if self.closed is not None:
            raise OperationsError(f"{self.lift} already closed at {self.closed}")
        entry = LogEntry(
            at=datetime.combine(self.day, at.to_time()),
            kind=EntryKind.CLOSED,
            text=f"swept at {at}",
            by=by,
        )
        return replace(self, closed=at, entries=(*self.entries, entry))

    def note(self, at: TimeOfDay, text: str, by: str) -> DayLog:
        entry = LogEntry(
            at=datetime.combine(self.day, at.to_time()),
            kind=EntryKind.NOTE,
            text=text,
            by=by,
        )
        return replace(self, entries=(*self.entries, entry))

    def with_stops(self, stops: StopLog) -> DayLog:
        return replace(self, stops=stops)

    def with_meter(self, meter: MeterReading) -> DayLog:
        if meter.lift != self.lift or meter.day != self.day:
            raise OperationsError("that meter reading is for another machine or another day")
        return replace(self, meter=meter)

    def opened_late(self) -> bool:
        return self.opened is not None and self.opened > self.window.first_load

    def swept_late(self) -> bool:
        return self.closed is not None and self.closed > self.window.sweep_by

    def scheduled_time(self) -> Duration:
        return self.window.first_load.until(self.window.sweep_by)

    def open_time(self) -> Duration:
        if self.opened is None or self.closed is None:
            raise OperationsError("the machine has not run a full day yet")
        return self.opened.until(self.closed)

    def running_time(self) -> Duration:
        return self.open_time() - self.stop_log().downtime()

    def lines(self) -> tuple[LogEntry, ...]:
        return tuple(sorted(self.entries, key=lambda entry: entry.at))

    def notes(self) -> tuple[LogEntry, ...]:
        return tuple(entry for entry in self.lines() if not entry.kind.is_structural)

    @classmethod
    def start(
        cls,
        lift: LiftCode,
        day: date,
        window: OperatingWindow,
        sheet: PreOpSheet | None = None,
        holds: HoldRegister | None = None,
        entries: Sequence[LogEntry] = (),
    ) -> DayLog:
        return cls(
            lift=lift,
            day=day,
            window=window,
            sheet=sheet,
            holds=holds or HoldRegister(),
            entries=tuple(entries),
        )

    def __str__(self) -> str:
        if self.opened is None:
            return f"{self.lift} {self.day.isoformat()}, never opened"
        tail = f" to {self.closed}" if self.closed is not None else ", still running"
        return f"{self.lift} {self.day.isoformat()}, {self.opened}{tail}"
