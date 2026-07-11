"""Commands for a day on the hill.

This is the one part of the package a lift operator touches, and they touch it
in the dark at ten past seven wearing gloves, so every command here takes as few
words as it can get away with and prints back what it just wrote down.

Times are given as times, not timestamps. The operator types 11:30 because that
is what the clock on the wall says, and the day the command is working on
supplies the date. Where a stop runs past midnight the restart takes a flag as
well, because that is the one case where the wall clock is not enough.
"""

from __future__ import annotations

from datetime import date, datetime, timedelta
from decimal import Decimal

import click

from sheaveline.cli.common import day_of, day_option
from sheaveline.cli.main import Console, cli, handles_refusals
from sheaveline.errors import ParseError
from sheaveline.ops.logbook import DayLog
from sheaveline.ops.meters import MeterReading
from sheaveline.ops.preop import STANDARD_SHEET, CheckOutcome, CheckResult, PreOpSheet
from sheaveline.ops.stop import StopEvent, StopKind
from sheaveline.persistence.uow import UnitOfWork
from sheaveline.plant.identity import LiftCode
from sheaveline.reports.forms import Align, Column, Table
from sheaveline.schedule.hours import OperatingWindow, TimeOfDay


@cli.group()
def day() -> None:
    """The day book: opening, stopping, restarting and closing."""


def _on(work: UnitOfWork, code: str, when: date) -> DayLog:
    return work.day_logs.get(LiftCode.parse(code), when)


def _moment(when: date, clock: str) -> datetime:
    """A wall clock time on the day being worked on."""
    return datetime.combine(when, TimeOfDay.parse(clock).to_time())


def _failures(given: tuple[str, ...]) -> dict[str, str]:
    """A failed check comes with its reason, because the sheet says so."""
    failed = {}
    for one in given:
        code, sep, reason = one.partition(":")
        if not sep or not reason.strip():
            raise ParseError(one, "a failed check written CODE:reason")
        failed[code.strip().upper()] = reason.strip()
    return failed


@day.command("start")
@click.argument("code")
@click.option("--first-load", default="09:00", show_default=True)
@click.option("--last-load", default="16:00", show_default=True)
@click.option("--sweep-by", default="16:30", show_default=True)
@click.option("--mechanic", default="", help="Who walked the sheet.")
@click.option("--operator", default="", help="Who is running it.")
@click.option(
    "--fail",
    multiple=True,
    metavar="CODE:REASON",
    help="A check that did not pass. The reason is not optional.",
)
@day_option()
@click.pass_obj
@handles_refusals
def start_day(
    console: Console,
    code: str,
    first_load: str,
    last_load: str,
    sweep_by: str,
    mechanic: str,
    operator: str,
    fail: tuple[str, ...],
    on: datetime | None,
) -> None:
    """Open the book on a machine for the day and walk the pre op sheet."""
    when = day_of(console, on)
    window = OperatingWindow.parse(first_load, last_load, sweep_by)
    with console.work() as work:
        lift = work.lifts.get(LiftCode.parse(code))
        sheet = None
        if mechanic and operator:
            failed = _failures(fail)
            sheet = PreOpSheet.of(
                lift=lift,
                day=when,
                results=[
                    CheckResult(
                        item=item,
                        outcome=CheckOutcome.FAIL if item.code in failed else CheckOutcome.PASS,
                        by=mechanic,
                        note=failed.get(item.code, ""),
                    )
                    for item in STANDARD_SHEET
                    if item.applies_to(lift)
                ],
                mechanic=mechanic,
                operator=operator,
            )
        log = DayLog.start(lift.code, when, window, sheet=sheet)
        work.day_logs.add(log)
        work.commit()
    console.say(f"{lift.code} {when.isoformat()} open, {window.first_load} to {window.sweep_by}")
    if sheet is None:
        console.say("no sheet walked yet, so it may not run")
    for blocker in log.blockers():
        console.say(f"blocker  {blocker}")


@day.command("note")
@click.argument("code")
@click.argument("at")
@click.argument("text")
@click.option("--by", required=True, help="Who wrote it.")
@day_option()
@click.pass_obj
@handles_refusals
def add_note(console: Console, code: str, at: str, text: str, by: str, on: datetime | None) -> None:
    """Write a line in the book."""
    when = day_of(console, on)
    with console.work() as work:
        log = _on(work, code, when)
        work.day_logs.replace(log.note(TimeOfDay.parse(at), text, by))
        work.commit()
    console.say(f"{at}  {text}")


@day.command("stop")
@click.argument("code")
@click.argument("at")
@click.option(
    "--kind",
    type=click.Choice([one.name.lower() for one in StopKind]),
    default="operator",
    show_default=True,
)
@click.option("--by", required=True, help="Who called it.")
@click.option("--note", default="", help="Needed on anything reportable.")
@day_option()
@click.pass_obj
@handles_refusals
def call_stop(
    console: Console,
    code: str,
    at: str,
    kind: str,
    by: str,
    note: str,
    on: datetime | None,
) -> None:
    """Stop the machine and say why."""
    when = day_of(console, on)
    event = StopEvent(
        lift=LiftCode.parse(code),
        kind=StopKind[kind.upper()],
        stopped=_moment(when, at),
        stopped_by=by,
        note=note,
    )
    with console.work() as work:
        work.day_logs.stop(event)
        work.commit()
    console.say(str(event))
    if event.kind.always_needs_a_line_check:
        console.say("the line is walked before this one runs again")


@day.command("restart")
@click.argument("code")
@click.argument("stopped")
@click.argument("at")
@click.option("--by", required=True, help="Who restarted it.")
@click.option("--line-checked", is_flag=True, help="The line has been walked.")
@day_option()
@click.option("--next-day", is_flag=True, help="The restart is the following morning.")
@click.pass_obj
@handles_refusals
def restart_lift(
    console: Console,
    code: str,
    stopped: str,
    at: str,
    by: str,
    line_checked: bool,
    on: datetime | None,
    next_day: bool,
) -> None:
    """Run it again, naming the stop by the time it was called."""
    when = day_of(console, on)
    back = when + timedelta(days=1 if next_day else 0)
    with console.work() as work:
        event = work.day_logs.restart(
            LiftCode.parse(code),
            _moment(when, stopped),
            _moment(back, at),
            by,
            line_checked=line_checked,
        )
        work.commit()
    console.say(f"{event.lift} running again, down {event.length()}")


@day.command("meter")
@click.argument("code")
@click.argument("hours")
@click.argument("cycles", type=int)
@click.option("--by", required=True, help="Who read it.")
@click.option("--replaced", is_flag=True, help="The meter itself was changed.")
@day_option()
@click.pass_obj
@handles_refusals
def read_meter(
    console: Console,
    code: str,
    hours: str,
    cycles: int,
    by: str,
    replaced: bool,
    on: datetime | None,
) -> None:
    """Write down the hour meter and the cycle counter."""
    reading = MeterReading(
        lift=LiftCode.parse(code),
        day=day_of(console, on),
        hours=Decimal(hours),
        cycles=cycles,
        read_by=by,
        meter_replaced=replaced,
    )
    with console.work() as work:
        work.day_logs.read_meter(reading)
        work.commit()
    console.say(str(reading))


@day.command("show")
@click.argument("code")
@day_option()
@click.pass_obj
@handles_refusals
def show_day(console: Console, code: str, on: datetime | None) -> None:
    """Print the book for one machine on one day."""
    when = day_of(console, on)
    with console.work() as work:
        log = _on(work, code, when)
        stops = work.day_logs.stops_on(log.lift, when)
    console.say(str(log))
    console.say(f"scheduled {log.scheduled_time()}, down {stops.downtime()}")
    if log.sheet is not None:
        console.say(f"sheet     {log.sheet}")
    for blocker in log.blockers():
        console.say(f"blocker   {blocker}")
    if log.entries:
        console.say("")
        console.page(
            Table.of(
                [Column("time", 6), Column("kind", 10), Column("entry", 44), Column("by", 14)],
                [(f"{one.at:%H:%M}", one.kind.value, one.text, one.by) for one in log.lines()],
            ).lines()
        )
    if len(stops):
        console.say("")
        console.page(
            Table.of(
                [
                    Column("stopped", 8),
                    Column("kind", 16),
                    Column("down", 10, Align.RIGHT),
                    Column("by", 14),
                ],
                [
                    (
                        f"{one.stopped:%H:%M}",
                        one.kind.value,
                        "open" if one.is_open() else str(one.length()),
                        one.stopped_by,
                    )
                    for one in stops.stops
                ],
            ).lines()
        )


@day.command("open-stops")
@click.option("--lift", "code", default=None, help="Just this machine.")
@click.pass_obj
@handles_refusals
def open_stops(console: Console, code: str | None) -> None:
    """Anything on the hill that is stopped and has not run again."""
    now = console.now()
    with console.work() as work:
        stops = work.day_logs.open_stops(None if code is None else LiftCode.parse(code))
    if not stops:
        console.say("nothing is stopped")
        return
    console.page(
        Table.of(
            [
                Column("lift", 5),
                Column("stopped", 17),
                Column("kind", 16),
                Column("down", 10, Align.RIGHT),
                Column("by", 14),
            ],
            [
                (
                    str(one.lift),
                    f"{one.stopped:%Y-%m-%d %H:%M}",
                    one.kind.value,
                    str(one.length(now)),
                    one.stopped_by,
                )
                for one in stops
            ],
        ).lines()
    )
