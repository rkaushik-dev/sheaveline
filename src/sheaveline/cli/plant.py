"""Commands about the machines.

These are what the office runs when somebody telephones and asks a question
about a lift. They print the eighty column form rather than a table of json,
because the answer usually ends up pinned to a noticeboard.
"""

from __future__ import annotations

from datetime import datetime
from decimal import Decimal

import click

from sheaveline.capacity.spacing import LineSpacing
from sheaveline.capacity.uphill import UphillCapacity
from sheaveline.cli.common import day_of, day_option, machine
from sheaveline.cli.main import Console, cli, handles_refusals
from sheaveline.errors import ParseError
from sheaveline.measures.angle import Angle
from sheaveline.measures.length import Length
from sheaveline.plant.alignment import AlignmentSurvey, TowerShot
from sheaveline.plant.identity import LiftCode, TowerRef
from sheaveline.reports.forms import Align, Column, Report, Table


@cli.group()
def lift() -> None:
    """The machines on the hill."""


@lift.command("list")
@click.pass_obj
@handles_refusals
def list_lifts(console: Console) -> None:
    """Every machine on file, one a line."""
    with console.work() as work:
        rows = [
            (
                str(one.code),
                one.name,
                one.lift_class.value,
                one.carriers_on_line(),
                one.design_speed.format_fpm(),
            )
            for one in work.lifts.all()
        ]
    table = Table.of(
        [
            Column("code", 6),
            Column("name", 20),
            Column("class", 22),
            Column("carriers", 8, Align.RIGHT),
            Column("speed", 10, Align.RIGHT),
        ],
        rows,
    )
    console.page(table.lines())


@lift.command("show")
@click.argument("code")
@click.pass_obj
@handles_refusals
def show_lift(console: Console, code: str) -> None:
    """Everything about one machine, on one page."""
    with console.work() as work:
        found = machine(work, code)
        line = LineSpacing.of(found)
        figures = UphillCapacity(line, found.carriers.kind.seats)
        page = Report()
        page.heading(f"{found.code} {found.name}")
        page.field("class", found.lift_class.value)
        page.field("commissioned", found.commissioned.isoformat())
        page.field("age", f"{found.age_in_years(console.today())} years")
        page.field("bearing", str(found.bearing))
        page.field("design speed", found.design_speed.format_fpm())
        page.field("horizontal length", f"{found.horizontal_length().feet:.0f} ft")
        page.field("vertical rise", f"{found.vertical_rise().feet:.0f} ft")
        page.field("slope length", f"{found.slope_length().feet:.0f} ft")
        page.field("ride time", found.ride_time().format_clock())
        page.field("carriers", found.carriers_on_line())
        page.field("seats a carrier", found.carriers.kind.seats)
        page.field("carrier interval", line.interval().format_clock())
        page.field("people an hour", f"{figures.persons_per_hour():,}")
        page.field("rope", str(found.rope.serial))
        page.field("rope length agrees", "yes" if found.rope_length_agrees() else "no")
    console.page(page.lines())


@lift.command("towers")
@click.argument("code")
@click.pass_obj
@handles_refusals
def show_towers(console: Console, code: str) -> None:
    """The tower schedule."""
    with console.work() as work:
        found = machine(work, code)
        rows = [
            (
                str(one.ref),
                f"{one.station.feet:.0f}",
                f"{one.base_elevation.feet:.0f}",
                f"{one.height.feet:.0f}",
                f"{one.crossarm_elevation().feet:.0f}",
                one.uphill.kind.value,
                one.sheave_count(),
            )
            for one in found.towers
        ]
    table = Table.of(
        [
            Column("tower", 8),
            Column("station", 8, Align.RIGHT),
            Column("base", 8, Align.RIGHT),
            Column("height", 7, Align.RIGHT),
            Column("crossarm", 9, Align.RIGHT),
            Column("kind", 12),
            Column("sheaves", 8, Align.RIGHT),
        ],
        rows,
    )
    console.page(table.lines())


@lift.command("spans")
@click.argument("code")
@click.pass_obj
@handles_refusals
def show_spans(console: Console, code: str) -> None:
    """The line cut into spans, longest and steepest marked."""
    with console.work() as work:
        found = machine(work, code)
        spans = found.spans()
        longest = spans.longest().index
        steepest = spans.steepest().index
        rows = []
        for span in spans:
            marks = []
            if span.index == longest:
                marks.append("longest")
            if span.index == steepest:
                marks.append("steepest")
            rows.append(
                (
                    span.index,
                    span.label,
                    f"{span.horizontal().feet:.0f}",
                    f"{span.rise().feet:.0f}",
                    span.slope().format_degrees(1),
                    " ".join(marks),
                )
            )
    table = Table.of(
        [
            Column("no", 3, Align.RIGHT),
            Column("between", 28),
            Column("run", 7, Align.RIGHT),
            Column("rise", 7, Align.RIGHT),
            Column("slope", 10, Align.RIGHT),
            Column("note", 18),
        ],
        rows,
    )
    console.page(table.lines())


@lift.command("withdraw")
@click.argument("code")
@click.option("--yes", is_flag=True, help="Do it without asking.")
@click.pass_obj
@handles_refusals
def withdraw_lift(console: Console, code: str, yes: bool) -> None:
    """Take a machine off the register. It takes its towers with it."""
    with console.work() as work:
        found = machine(work, code)
        if not yes:
            click.confirm(f"take {found.code} {found.name} off the register?", abort=True)
        work.lifts.remove(found.code)
        work.commit()
    console.say(f"{code.upper()} withdrawn")


@lift.command("shoot")
@click.argument("code")
@click.option("--surveyor", required=True, help="Who ran the instrument.")
@click.option(
    "--shot",
    "shots",
    multiple=True,
    required=True,
    metavar="TOWER:STATION:CROSSARM:OFFSET",
    help="One tower. Give it once a tower.",
)
@day_option("The day it was shot.")
@click.pass_obj
@handles_refusals
def shoot_towers(
    console: Console, code: str, surveyor: str, shots: tuple[str, ...], on: datetime | None
) -> None:
    """Write an autumn total station survey into the file.

    A shot is tower:station:crossarm:offset, all in feet except the offset, which
    is how far the top is out of plumb and is written in inches because that is
    what it is read to.
    """
    when = day_of(console, on)
    with console.work() as work:
        found = machine(work, code)
        survey = AlignmentSurvey.of(
            line=found.towers,
            taken=when,
            surveyor=surveyor,
            shots=[parse_shot(one) for one in shots],
        )
        work.alignment.add(survey)
        work.commit()
    console.say(f"{found.code} shot {when.isoformat()} by {surveyor}, {len(survey)} towers")
    for line in survey.findings():
        console.say(f"  {line}")
    if survey.is_clean():
        console.say("  every tower is where the drawing says it is")


def parse_shot(text: str) -> TowerShot:
    """Read tower:station:crossarm:offset, the order it comes off the instrument."""
    parts = text.split(":")
    if len(parts) != 4:
        raise ParseError(text, "a shot written tower:station:crossarm:offset")
    ref, station, crossarm, offset = parts
    return TowerShot(
        ref=TowerRef.parse(ref.strip()),
        station=Length.parse(f"{station.strip()} ft"),
        crossarm_elevation=Length.parse(f"{crossarm.strip()} ft"),
        offset_at_top=Length.parse(f"{offset.strip()} in"),
    )


@lift.command("drift")
@click.argument("code")
@click.pass_obj
@handles_refusals
def tower_drift(console: Console, code: str) -> None:
    """How fast each tower is going over, and when it reaches the tolerance."""
    with console.work() as work:
        wanted = LiftCode.parse(code)
        history = work.alignment.history(wanted)
    latest = history.latest()
    console.say(f"{wanted} read across {len(history)} surveys to {latest.taken.isoformat()}")
    console.page(
        Table.of(
            [
                Column("tower", 8),
                Column("lean now", 10, Align.RIGHT),
                Column("a year", 10, Align.RIGHT),
                Column("settled", 10, Align.RIGHT),
                Column("years left", 11, Align.RIGHT),
            ],
            [
                (
                    str(one.ref),
                    latest.lean(one.ref).format_degrees(),
                    _rate(history.lean_a_year(one.ref)),
                    latest.settlement(one.ref).format_inches(2),
                    _years(history.years_until_out_of_plumb(one.ref)),
                )
                for one in latest.shots
            ],
        ).lines()
    )
    for ref in history.moving():
        years = history.years_until_out_of_plumb(ref)
        if years is not None and years <= 5:
            console.say(f"{ref} is out of plumb inside five years at this rate")


def _rate(found: Angle | None) -> str:
    return "flat" if found is None or found.millidegrees == 0 else found.format_degrees()


def _years(found: Decimal | None) -> str:
    return "not moving" if found is None else f"{found:.1f}"
