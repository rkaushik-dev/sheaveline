"""Turning a day on the hill into rows and back again.

The day log is the awkward one. It owns its checks and its written entries, so
those go down and come back with it, but the stops and the holds do not belong
to a day at all. A wind hold called at four and cleared at nine the next morning
is one hold across two logs, and the loader takes them as arguments rather than
reading them off the row, because only the caller knows how wide a window it
means.

The pre op sheet needs the lift itself and not just its code, since the check
list depends on what class of machine it is. That is why the loader asks for a
Lift and refuses to guess.
"""

from __future__ import annotations

from collections.abc import Sequence

from sheaveline.ops.logbook import DayLog, LogEntry
from sheaveline.ops.meters import MeterReading
from sheaveline.ops.preop import STANDARD_SHEET, CheckResult, PreOpSheet
from sheaveline.ops.stop import StopEvent, StopLog
from sheaveline.persistence.base import StorageError
from sheaveline.persistence.models.ops import (
    DayLogRow,
    HoldRow,
    LogEntryRow,
    MeterReadingRow,
    PreOpResultRow,
    StopRow,
)
from sheaveline.plant.identity import LiftCode
from sheaveline.plant.lift import Lift
from sheaveline.schedule.hours import OperatingWindow
from sheaveline.weather.holdlog import HoldRecord


def day_log_to_row(log: DayLog) -> DayLogRow:
    row = DayLogRow(
        lift_code=log.lift,
        day=log.day,
        first_load=log.window.first_load,
        last_load=log.window.last_load,
        sweep_by=log.window.sweep_by,
        opened=log.opened,
        closed=log.closed,
        mechanic=log.sheet.mechanic if log.sheet else None,
        operator=log.sheet.operator if log.sheet else None,
    )
    for entry in log.entries:
        row.entries.append(LogEntryRow(at=entry.at, kind=entry.kind, text=entry.text, by=entry.by))
    if log.sheet is not None:
        for result in log.sheet.results:
            row.checks.append(
                PreOpResultRow(
                    code=result.item.code,
                    outcome=result.outcome,
                    by=result.by,
                    note=result.note,
                )
            )
    return row


def day_log_from_row(
    row: DayLogRow,
    lift: Lift,
    stops: StopLog | None = None,
    meter: MeterReading | None = None,
) -> DayLog:
    """The day back out of storage, with the parts that outlive it handed in."""
    if row.lift_code != lift.code:
        raise StorageError(f"that log is for {row.lift_code} and not {lift.code}")
    return DayLog(
        lift=lift.code,
        day=row.day,
        window=OperatingWindow(row.first_load, row.last_load, row.sweep_by),
        sheet=_sheet_from_rows(row, lift),
        stops=stops,
        entries=tuple(
            LogEntry(at=one.at, kind=one.kind, text=one.text, by=one.by)
            for one in sorted(row.entries, key=lambda one: one.at)
        ),
        opened=row.opened,
        closed=row.closed,
        meter=meter,
    )


def _sheet_from_rows(row: DayLogRow, lift: Lift) -> PreOpSheet | None:
    if not row.checks:
        return None
    if row.mechanic is None or row.operator is None:
        raise StorageError(f"the sheet for {row.day} has checks on it but nobody signed it")
    items = {one.code: one for one in STANDARD_SHEET if one.applies_to(lift)}
    results = []
    for check in sorted(row.checks, key=lambda one: one.code):
        item = items.get(check.code)
        if item is None:
            raise StorageError(
                f"there is no check called {check.code} on a {lift.lift_class.value}"
            )
        results.append(CheckResult(item=item, outcome=check.outcome, by=check.by, note=check.note))
    return PreOpSheet(
        lift=lift,
        day=row.day,
        results=tuple(results),
        mechanic=row.mechanic,
        operator=row.operator,
    )


def stop_to_row(stop: StopEvent) -> StopRow:
    return StopRow(
        lift_code=stop.lift,
        kind=stop.kind,
        stopped=stop.stopped,
        stopped_by=stop.stopped_by,
        note=stop.note,
        restarted=stop.restarted,
        restarted_by=stop.restarted_by,
        line_checked=stop.line_checked,
    )


def stop_from_row(row: StopRow) -> StopEvent:
    return StopEvent(
        lift=row.lift_code,
        kind=row.kind,
        stopped=row.stopped,
        stopped_by=row.stopped_by,
        note=row.note,
        restarted=row.restarted,
        restarted_by=row.restarted_by,
        line_checked=row.line_checked,
    )


def stop_log_from_rows(lift_code: LiftCode, rows: Sequence[StopRow]) -> StopLog:
    """One machine's stops over whatever window the caller asked the database for."""
    for row in rows:
        if row.lift_code != lift_code:
            raise StorageError(f"that stop is on {row.lift_code} and not {lift_code}")
    return StopLog.of(lift_code, [stop_from_row(one) for one in rows])


def hold_to_row(hold: HoldRecord) -> HoldRow:
    return HoldRow(
        lift_code=hold.lift,
        reason=hold.reason,
        started=hold.started,
        started_by=hold.started_by,
        detail=hold.detail,
        ended=hold.ended,
        ended_by=hold.ended_by,
    )


def hold_from_row(row: HoldRow) -> HoldRecord:
    return HoldRecord(
        lift=row.lift_code,
        reason=row.reason,
        started=row.started,
        started_by=row.started_by,
        detail=row.detail,
        ended=row.ended,
        ended_by=row.ended_by,
    )


def meter_to_row(reading: MeterReading) -> MeterReadingRow:
    return MeterReadingRow(
        lift_code=reading.lift,
        day=reading.day,
        hours=reading.hours,
        cycles=reading.cycles,
        read_by=reading.read_by,
        meter_replaced=reading.meter_replaced,
    )


def meter_from_row(row: MeterReadingRow) -> MeterReading:
    return MeterReading(
        lift=row.lift_code,
        day=row.day,
        hours=row.hours,
        cycles=row.cycles,
        read_by=row.read_by,
        meter_replaced=row.meter_replaced,
    )
