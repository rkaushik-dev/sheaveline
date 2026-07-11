"""Commands about the ladder: what is due, what was found, what is being done.

The department's word for the schedule of required inspections is the ladder,
and the point of these commands is that a mechanic can answer three questions
without opening the binder. What is due on this machine. What did the last walk
turn up that has not been dealt with. What is on the board for me today.

Raising a work order takes a defect and not a priority. The priority falls out of
how bad the defect is, and a mechanic who could type the priority in would
eventually type the convenient one.
"""

from __future__ import annotations

from datetime import date, datetime
from decimal import Decimal

import click

from sheaveline.cli.common import day_of, day_option
from sheaveline.cli.main import Console, cli, handles_refusals
from sheaveline.errors import ParseError
from sheaveline.persistence.base import StorageError
from sheaveline.plant.identity import LiftCode
from sheaveline.reports.forms import Align, Column, Table
from sheaveline.reports.gather import assess
from sheaveline.upkeep.record import Defect, DefectLevel, InspectionRecord
from sheaveline.upkeep.workorder import Priority, WorkOrder


@cli.group()
def upkeep() -> None:
    """The inspection ladder, its findings and the work that comes off it."""


@upkeep.command("due")
@click.argument("code")
@click.option("--within", default=30, show_default=True, help="Days ahead to look.")
@day_option()
@click.pass_obj
@handles_refusals
def due(console: Console, code: str, within: int, on: datetime | None) -> None:
    """What the ladder wants on one machine, soonest first."""
    when = day_of(console, on)
    with console.work() as work:
        lift = work.lifts.get(LiftCode.parse(code))
        reading = assess(work, lift, when)
    schedule = reading.schedule
    overdue = schedule.overdue(when)
    soon = [one for one in schedule.due_within(when, within) if one not in overdue]
    console.say(f"{lift.code} {lift.name}, ladder read on {when.isoformat()}")
    console.say(f"status    {reading.status().value}")
    if not overdue and not soon:
        console.say("nothing due")
        return
    console.page(
        Table.of(
            [
                Column("job", 5),
                Column("what", 38),
                Column("when", 12),
                Column("days", 6, Align.RIGHT),
                Column("state", 9),
            ],
            [
                (
                    one.job.code,
                    one.job.description,
                    "never" if one.due_on is None else one.due_on.isoformat(),
                    "" if one.days_until(when) is None else one.days_until(when),
                    "overdue" if one in overdue else "due",
                )
                for one in [*overdue, *soon]
            ],
        ).lines()
    )


def parse_defect(text: str, raised: date) -> Defect:
    """Read where:level:what, which is the order it is written on the walk sheet."""
    parts = text.split(":", 2)
    if len(parts) != 3:
        raise ParseError(text, "a defect written where:level:what")
    where, level, detail = parts
    try:
        found = DefectLevel(level.strip().lower())
    except ValueError as wrong:
        wanted = ", ".join(one.value for one in DefectLevel)
        raise ParseError(level, f"a defect level, one of {wanted}") from wrong
    return Defect(where=where.strip(), level=found, detail=detail.strip(), raised=raised)


@upkeep.command("record")
@click.argument("code")
@click.argument("job")
@click.option("--by", required=True, help="Who did it.")
@click.option("--hours", default=None, help="The hour meter at the time.")
@click.option("--cycles", default=None, type=int, help="The cycle counter at the time.")
@click.option("--defect", multiple=True, metavar="WHERE:LEVEL:WHAT")
@day_option()
@click.pass_obj
@handles_refusals
def record_job(
    console: Console,
    code: str,
    job: str,
    by: str,
    hours: str | None,
    cycles: int | None,
    defect: tuple[str, ...],
    on: datetime | None,
) -> None:
    """Sign off one job on the ladder and write down what it turned up."""
    when = day_of(console, on)
    found = [parse_defect(one, when) for one in defect]
    record = InspectionRecord.of(
        lift=LiftCode.parse(code),
        code=job,
        done=when,
        by=by,
        hours=None if hours is None else Decimal(hours),
        cycles=cycles,
        defects=found,
    )
    with console.work() as work:
        work.inspections.add(record)
        work.commit()
    console.say(f"{record.lift} {record.code} done {when.isoformat()} by {by}")
    for one in found:
        console.say(f"  {one.level.value:12} {one.where}  {one.detail}")
        if one.level.needs_a_work_order:
            console.say("    this one wants a work order raising")


@upkeep.command("defects")
@click.option("--lift", "code", default=None, help="Just this machine.")
@click.pass_obj
@handles_refusals
def open_defects(console: Console, code: str | None) -> None:
    """Everything found and not yet cleared."""
    with console.work() as work:
        found = work.inspections.open_defects(None if code is None else LiftCode.parse(code))
    if not found:
        console.say("nothing open")
        return
    console.page(
        Table.of(
            [
                Column("where", 14),
                Column("level", 12),
                Column("what", 34),
                Column("raised", 12),
            ],
            [(one.where, one.level.value, one.detail, one.raised.isoformat()) for one in found],
        ).lines()
    )


@upkeep.command("raise")
@click.argument("code")
@click.argument("where")
@click.option("--by", required=True, help="Who is raising it.")
@click.pass_obj
@handles_refusals
def raise_order(console: Console, code: str, where: str, by: str) -> None:
    """Raise a work order against an open defect.

    The order is dated from the defect rather than from today, because the clock
    the priority is measured against started when somebody found the thing.
    """
    lift = LiftCode.parse(code)
    with console.work() as work:
        found = [one for one in work.inspections.open_defects(lift) if one.where == where]
        if not found:
            raise StorageError(f"{lift} has no open defect at {where}")
        defect = found[0]
        order = WorkOrder.from_defect(
            number=work.work_orders.next_number(),
            lift=lift,
            defect=defect,
            raised_by=by,
        )
        work.work_orders.add(order)
        work.commit()
    console.say(str(order))
    console.say(f"due by    {order.due_by().isoformat()}")
    if order.priority.stops_the_machine:
        console.say(f"{lift} does not carry the public until this is closed")


@upkeep.command("board")
@click.option("--lift", "code", default=None, help="Just this machine.")
@click.option("--priority", type=click.Choice([one.name.lower() for one in Priority]), default=None)
@day_option()
@click.pass_obj
@handles_refusals
def order_board(
    console: Console, code: str | None, priority: str | None, on: datetime | None
) -> None:
    """The work orders that are still open, worst first."""
    when = day_of(console, on)
    with console.work() as work:
        if priority is not None:
            orders = work.work_orders.of_priority(Priority[priority.upper()])
        else:
            orders = work.work_orders.open_orders(None if code is None else LiftCode.parse(code))
    orders = [one for one in orders if one.is_open()]
    if not orders:
        console.say("the board is clear")
        return
    orders.sort(key=lambda one: (one.due_by(), one.number))
    console.page(
        Table.of(
            [
                Column("no", 5, Align.RIGHT),
                Column("lift", 5),
                Column("what", 32),
                Column("priority", 10),
                Column("due", 12),
                Column("state", 8),
            ],
            [
                (
                    one.number,
                    str(one.lift),
                    one.detail,
                    one.priority.value,
                    one.due_by().isoformat(),
                    "overdue" if one.is_overdue(when) else "open",
                )
                for one in orders
            ],
        ).lines()
    )


@upkeep.command("close")
@click.argument("number", type=int)
@click.option("--by", required=True, help="Who finished it.")
@day_option()
@click.pass_obj
@handles_refusals
def close_order(console: Console, number: int, by: str, on: datetime | None) -> None:
    """Close a work order."""
    when = day_of(console, on)
    with console.work() as work:
        closed = work.work_orders.close(number, when, by)
        work.commit()
    console.say(str(closed))
