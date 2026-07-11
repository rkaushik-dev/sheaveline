"""Commands about the machines.

These are what the office runs when somebody telephones and asks a question
about a lift. They print the eighty column form rather than a table of json,
because the answer usually ends up pinned to a noticeboard.
"""

from __future__ import annotations

import click

from sheaveline.capacity.spacing import LineSpacing
from sheaveline.capacity.uphill import UphillCapacity
from sheaveline.cli.common import machine
from sheaveline.cli.main import Console, cli, handles_refusals
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
