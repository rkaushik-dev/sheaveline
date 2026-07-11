"""Commands for the tramway board.

Utah's board wants to hear about some things immediately and about others in the
monthly return, and the deadline is the shorter of what the kind of incident asks
for and what the injury asks for. Nobody at the keyboard works that out. The
incident does, and these commands print the answer.

Filing takes the next number in the series and never reuses one. A gap in the
series is the sort of thing an auditor asks about for an afternoon, so the number
comes off a counter row in the same transaction as the filing.
"""

from __future__ import annotations

from datetime import date, datetime

import click

from sheaveline.board.filing import FilingKind
from sheaveline.board.incident import Incident, IncidentKind, InjuryLevel
from sheaveline.cli.common import moment_of, moment_option
from sheaveline.cli.main import Console, cli, handles_refusals
from sheaveline.persistence.base import StorageError
from sheaveline.plant.identity import LiftCode
from sheaveline.reports.forms import Align, Column, Table


@cli.group()
def board() -> None:
    """Reportable incidents and what has been filed about them."""


@board.command("record")
@click.argument("code")
@click.argument("narrative")
@click.option(
    "--kind",
    type=click.Choice([one.name.lower() for one in IncidentKind]),
    required=True,
)
@click.option(
    "--injury",
    type=click.Choice([one.name.lower() for one in InjuryLevel]),
    default="none",
    show_default=True,
)
@click.option("--people", default=1, show_default=True, help="How many were involved.")
@click.option("--by", required=True, help="Who is reporting it.")
@moment_option()
@click.pass_obj
@handles_refusals
def record_incident(
    console: Console,
    code: str,
    narrative: str,
    kind: str,
    injury: str,
    people: int,
    by: str,
    at: datetime | None,
) -> None:
    """Write an incident into the register and print when it has to be filed."""
    incident = Incident(
        lift=LiftCode.parse(code),
        at=moment_of(console, at),
        kind=IncidentKind[kind.upper()],
        injury=InjuryLevel[injury.upper()],
        people=people,
        narrative=narrative,
        reported_by=by,
    )
    with console.work() as work:
        if not work.lifts.exists(incident.lift):
            raise StorageError(f"there is no lift {incident.lift}")
        number = work.incidents.record(incident)
        work.commit()
    console.say(f"incident {number}  {incident}")
    console.say(f"file by   {incident.due_by():%Y-%m-%d %H:%M}")
    console.say(f"driven by {incident.deadline_driver()}")
    if incident.holds_the_machine():
        console.say(f"{incident.lift} does not run again until the board says so")


@board.command("file")
@click.argument("incident_id", type=int)
@click.option(
    "--kind",
    type=click.Choice([one.name.lower() for one in FilingKind]),
    required=True,
)
@click.option("--by", required=True, help="Who is filing it.")
@moment_option()
@click.pass_obj
@handles_refusals
def file_incident(
    console: Console, incident_id: int, kind: str, by: str, at: datetime | None
) -> None:
    """File one incident with the board and take the next number in the series."""
    when = moment_of(console, at)
    with console.work() as work:
        incident, _ = work.incidents.get(incident_id)
        filing = work.incidents.file(incident_id, FilingKind[kind.upper()], when, by)
        work.commit()
    console.say(str(filing))
    if filing.was_late(incident):
        console.say(f"late by   {filing.lateness(incident)}")
    if filing.kind.needs_an_acknowledgement:
        console.say("the board acknowledges this one, so watch for it coming back")


@board.command("unfiled")
@moment_option()
@click.pass_obj
@handles_refusals
def unfiled(console: Console, at: datetime | None) -> None:
    """Everything on the register that has not gone to the board yet, worst first."""
    now = moment_of(console, at)
    with console.work() as work:
        waiting = work.incidents.numbered_unfiled()
    if not waiting:
        console.say("nothing is waiting to be filed")
        return
    waiting.sort(key=lambda one: one[1].due_by())
    console.page(
        Table.of(
            [
                Column("no", 4, Align.RIGHT),
                Column("lift", 5),
                Column("what", 20),
                Column("happened", 17),
                Column("file by", 17),
                Column("state", 8),
            ],
            [
                (
                    number,
                    str(incident.lift),
                    incident.kind.value,
                    f"{incident.at:%Y-%m-%d %H:%M}",
                    f"{incident.due_by():%Y-%m-%d %H:%M}",
                    "LATE" if incident.is_overdue(now) else "waiting",
                )
                for number, incident, _ in waiting
            ],
        ).lines()
    )


@board.command("show")
@click.argument("code")
@click.pass_obj
@handles_refusals
def show_lift(console: Console, code: str) -> None:
    """Every incident on one machine, filed or not."""
    lift = LiftCode.parse(code)
    with console.work() as work:
        rows = work.incidents.numbered_for_lift(lift)
    if not rows:
        console.say(f"nothing is on the register for {lift}")
        return
    console.page(
        Table.of(
            [
                Column("no", 4, Align.RIGHT),
                Column("happened", 17),
                Column("what", 20),
                Column("injury", 12),
                Column("filed as", 16),
            ],
            [
                (
                    number,
                    f"{incident.at:%Y-%m-%d %H:%M}",
                    incident.kind.value,
                    incident.injury.value,
                    "not filed" if filing is None else str(filing),
                )
                for number, incident, filing in rows
            ],
        ).lines()
    )


@board.command("return")
@click.argument("year", type=int)
@click.argument("month", type=int)
@click.pass_obj
@handles_refusals
def monthly_return(console: Console, year: int, month: int) -> None:
    """The monthly return, which is every incident that happened in that month."""
    start = date(year, month, 1)
    end = date(year + (month == 12), month % 12 + 1, 1)
    with console.work() as work:
        found = work.incidents.between(start, end)
    listed = [one for one in found if one.goes_in_the_monthly_return()]
    console.say(f"monthly return {year}-{month:02d}")
    console.say(f"incidents {len(found)}")
    console.say(f"returned  {len(listed)}")
    if not listed:
        return
    console.page(
        Table.of(
            [
                Column("lift", 5),
                Column("happened", 17),
                Column("what", 20),
                Column("injury", 12),
                Column("people", 7, Align.RIGHT),
            ],
            [
                (
                    str(one.lift),
                    f"{one.at:%Y-%m-%d %H:%M}",
                    one.kind.value,
                    one.injury.value,
                    one.people,
                )
                for one in listed
            ],
        ).lines()
    )
