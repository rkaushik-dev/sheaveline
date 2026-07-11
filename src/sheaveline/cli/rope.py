"""Commands about the ropes.

The walk command is the one that matters. A mechanic comes in off the hill with
a page of caliper readings and types them in, and the readings are given as
station, crowns, valleys separated by colons because that is the order they are
written on the clipboard and asking somebody to reorder them at the keyboard is
how a reading ends up in the wrong column.
"""

from __future__ import annotations

from datetime import date, datetime

import click

from sheaveline.cli.common import DAY_FORMAT, day_option, haul_rope, printed
from sheaveline.cli.main import Console, cli, handles_refusals
from sheaveline.errors import ParseError
from sheaveline.measures.length import Length
from sheaveline.plant.identity import RopeSerial
from sheaveline.reports.forms import Align, Column, Table
from sheaveline.reports.ropefile import RopeFilePage
from sheaveline.ropework.trend import LEAD_TIME_DAYS, forecast, orderable
from sheaveline.ropework.verdict import assess_wear
from sheaveline.ropework.wear import DiameterReading, WearSurvey


@cli.group()
def rope() -> None:
    """The haul ropes and what has been done to them."""


def parse_reading(text: str) -> DiameterReading:
    """Read station:crowns:valleys, the order it is written on the clipboard."""
    parts = text.split(":")
    if len(parts) != 3:
        raise ParseError(text, "a reading written station:crowns:valleys")
    station, crowns, valleys = parts
    return DiameterReading(
        station=Length.parse(station),
        across_crowns=Length.parse(crowns),
        across_valleys=Length.parse(valleys),
    )


@rope.command("list")
@click.option("--spares", is_flag=True, help="Only the ropes on no machine.")
@click.pass_obj
@handles_refusals
def list_ropes(console: Console, spares: bool) -> None:
    """Every rope on file."""
    with console.work() as work:
        found = work.ropes.spares() if spares else work.ropes.all()
        rows = [
            (
                str(one.serial),
                one.spec.construction.value,
                one.diameter.format_inches(3),
                f"{one.length.feet:.0f}",
                one.installed.isoformat(),
                one.seasons_in_service(console.today()),
            )
            for one in found
        ]
    table = Table.of(
        [
            Column("serial", 16),
            Column("construction", 21),
            Column("diameter", 9, Align.RIGHT),
            Column("length", 7, Align.RIGHT),
            Column("installed", 11),
            Column("seasons", 7, Align.RIGHT),
        ],
        rows,
    )
    console.page(table.lines())


@rope.command("file")
@click.argument("serial")
@click.pass_obj
@handles_refusals
def print_file(console: Console, serial: str) -> None:
    """The rope file, printed the way it goes in the folder."""
    with console.work() as work:
        held = haul_rope(work, serial)
        machine = "in the shop"
        for one in work.lifts.all():
            if one.rope.serial == held.serial:
                machine = f"{one.code} {one.name}"
        page = RopeFilePage.of(
            rope=held,
            on=console.today(),
            machine=machine,
            wear=work.wear_surveys.for_rope(held),
        )
    console.page(page.render().lines())


@rope.command("walk")
@click.argument("serial")
@click.option("--taken", type=click.DateTime([DAY_FORMAT]), required=True)
@click.option("--inspector", required=True)
@click.option(
    "--reading",
    "readings",
    multiple=True,
    required=True,
    metavar="STATION:CROWNS:VALLEYS",
    help="One caliper reading. Give it once a station.",
)
@click.pass_obj
@handles_refusals
def walk_rope(
    console: Console,
    serial: str,
    taken: date,
    inspector: str,
    readings: tuple[str, ...],
) -> None:
    """Record a caliper walk and print what it says about the rope."""
    with console.work() as work:
        held = haul_rope(work, serial)
        survey = WearSurvey.of(
            rope=held,
            taken=taken.date() if hasattr(taken, "date") else taken,
            inspector=inspector,
            diameters=[parse_reading(one) for one in readings],
        )
        work.wear_surveys.add(survey)
        work.commit()
    smallest = survey.smallest()
    console.say(f"{len(survey.diameters)} readings recorded against {held.serial}")
    console.say(
        f"smallest {smallest.mean().format_inches(3)} "
        f"at {smallest.station.feet:.0f} ft, "
        f"{survey.percent_of_nominal(smallest):.1f}% of nominal"
    )
    console.say(f"verdict {assess_wear(survey).value}")


@rope.command("walks")
@click.argument("serial")
@click.pass_obj
@handles_refusals
def list_walks(console: Console, serial: str) -> None:
    """Every caliper walk on one rope."""
    with console.work() as work:
        held = haul_rope(work, serial)
        rows = [
            (
                one.taken.isoformat(),
                one.inspector,
                one.smallest().mean().format_inches(3),
                f"{one.smallest().station.feet:.0f}",
                assess_wear(one).value,
            )
            for one in work.wear_surveys.for_rope(held)
        ]
    table = Table.of(
        [
            Column("taken", 11),
            Column("inspector", 20),
            Column("smallest", 10, Align.RIGHT),
            Column("station", 8, Align.RIGHT),
            Column("verdict", 12),
        ],
        rows,
    )
    console.page(table.lines())


@rope.command("forecast")
@click.argument("serial", required=False)
@click.option(
    "--lead-time",
    default=LEAD_TIME_DAYS,
    show_default=True,
    help="Weeks the mill takes, in days.",
)
@day_option()
@click.pass_obj
@handles_refusals
def wear_forecast(
    console: Console, serial: str | None, lead_time: int, on: datetime | None
) -> None:
    """When a rope reaches the condemning diameter, and when to order the next.

    With no serial it walks every rope on file and prints only the ones whose
    replacement should already be on order, which is the version the office runs
    in September.
    """
    when = on.date() if on else console.today()
    with console.work() as work:
        if serial is not None:
            wanted = work.ropes.get(RopeSerial.parse(serial))
            found = [forecast(wanted, work.wear_surveys.for_rope(wanted), when, lead_time)]
        else:
            found = list(
                orderable(
                    [(one, work.wear_surveys.for_rope(one)) for one in work.ropes.all()],
                    when,
                    lead_time,
                )
            )
    if not found:
        console.say("nothing on the hill wants ordering yet")
        return
    console.page(
        Table.of(
            [
                Column("serial", 16),
                Column("now", 8, Align.RIGHT),
                Column("left", 8, Align.RIGHT),
                Column("comes off", 12),
                Column("order by", 12),
                Column("read from", 12),
            ],
            [
                (
                    str(one.trend.rope.serial),
                    one.trend.last().smallest.format_inches(3),
                    one.trend.left().format_inches(3),
                    printed(one.comes_off(), "not wearing"),
                    printed(one.order_by()),
                    one.confidence(),
                )
                for one in found
            ],
        ).lines()
    )
    for one in found:
        if one.is_overdue_to_order():
            console.say(f"{one.trend.rope.serial} should already be on order")
