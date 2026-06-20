"""The pages the office prints.

Every command here writes eighty columns of plain text to standard output, which
is what the printer in the lift shop takes and what a mail message carries
without anybody having to open anything. There is deliberately no other format.

The morning page is the one that runs on a timer. The season summary is run once
in May and then argued about until August.
"""

from __future__ import annotations

from datetime import datetime

import click

from sheaveline.cli.main import Console, cli, handles_refusals
from sheaveline.measures.load import Load
from sheaveline.reports.gather import board_packet, morning, season_summary
from sheaveline.schedule.season import Season

DAY_FORMAT = "%Y-%m-%d"


@cli.group()
def report() -> None:
    """Print a page."""


@report.command("morning")
@click.option("--on", "on", type=click.DateTime([DAY_FORMAT]), default=None)
@click.option("--resort", default="Ogden Peak", show_default=True)
@click.option("--snow", default="", help="What the patrol reported overnight.")
@click.option(
    "--tension",
    default=None,
    help="What the rope is carrying, if the line is to be walked as well.",
)
@click.pass_obj
@handles_refusals
def morning_page(
    console: Console,
    on: datetime | None,
    resort: str,
    snow: str,
    tension: str | None,
) -> None:
    """The seven o'clock page: what is opening and what is stopping the rest."""
    when = on.date() if on else console.today()
    with console.work() as work:
        page = morning(
            work,
            when,
            resort,
            snow,
            tension=None if tension is None else Load.parse(tension),
        )
    console.page(page.render().lines())


@report.command("season")
@click.argument("label")
@click.option("--opens", type=click.DateTime([DAY_FORMAT]), required=True)
@click.option("--closes", type=click.DateTime([DAY_FORMAT]), required=True)
@click.pass_obj
@handles_refusals
def season_page(console: Console, label: str, opens: datetime, closes: datetime) -> None:
    """Add up a whole season, machine by machine."""
    season = Season(label=label, opens=opens.date(), closes=closes.date())
    with console.work() as work:
        page = season_summary(work, season)
    console.page(page.render().lines())


@report.command("packet")
@click.argument("label")
@click.option("--opens", type=click.DateTime([DAY_FORMAT]), required=True)
@click.option("--closes", type=click.DateTime([DAY_FORMAT]), required=True)
@click.option("--resort", default="Ogden Peak", show_default=True)
@click.option("--by", required=True, help="Who is signing it.")
@click.option("--on", "on", type=click.DateTime([DAY_FORMAT]), default=None)
@click.pass_obj
@handles_refusals
def board_page(
    console: Console,
    label: str,
    opens: datetime,
    closes: datetime,
    resort: str,
    by: str,
    on: datetime | None,
) -> None:
    """The annual packet the board reads, which is the one page that leaves here."""
    season = Season(label=label, opens=opens.date(), closes=closes.date())
    prepared = on.date() if on else console.today()
    with console.work() as work:
        packet = board_packet(work, season, resort, prepared, by)
    console.page(packet.render().lines())
    if not packet.is_clean():
        raise SystemExit(1)
