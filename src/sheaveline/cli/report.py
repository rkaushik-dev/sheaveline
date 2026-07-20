"""The pages the office prints.

Every command here writes eighty columns of plain text to standard output, which
is what the printer in the lift shop takes and what a mail message carries
without anybody having to open anything. There is deliberately no other format.

The morning page is the one that runs on a timer. The season summary is run once
in May and then argued about until August.
"""

from __future__ import annotations

from datetime import datetime
from pathlib import Path
from typing import TextIO

import click

from sheaveline.cli.common import DAY_FORMAT, day_option
from sheaveline.cli.main import Console, cli, handles_refusals
from sheaveline.measures.load import Load
from sheaveline.ops.stop import StopEvent
from sheaveline.persistence.uow import UnitOfWork
from sheaveline.reports.export import (
    SHEETS,
    write_days,
    write_incidents,
    write_stops,
    write_wear,
)
from sheaveline.reports.gather import board_packet, morning, season_summary
from sheaveline.schedule.season import Season


@cli.group()
def report() -> None:
    """Print a page."""


@report.command("morning")
@day_option()
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
@day_option()
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


@report.command("export")
@click.argument("sheet", type=click.Choice(sorted(SHEETS)))
@click.option("--opens", type=click.DateTime([DAY_FORMAT]), required=True)
@click.option("--closes", type=click.DateTime([DAY_FORMAT]), required=True)
@click.option("--label", default="season", show_default=True)
@click.option(
    "--to",
    "destination",
    type=click.Path(dir_okay=False, writable=True, path_type=Path),
    default=None,
    help="Where to write it. Standard output if you leave it off.",
)
@click.pass_obj
@handles_refusals
def export_sheet(
    console: Console,
    sheet: str,
    opens: datetime,
    closes: datetime,
    label: str,
    destination: Path | None,
) -> None:
    """Write one table as comma separated text, for a spreadsheet.

    Every measure goes out as the integer this system holds it in, with the unit
    in the column heading. A spreadsheet that reads a rope diameter as a date has
    already lost, and it does that to anything written with an inch mark on it.
    """
    season = Season(label=label, opens=opens.date(), closes=closes.date())
    handle = (
        destination.open("w", encoding="utf-8", newline="")
        if destination is not None
        else click.get_text_stream("stdout")
    )
    try:
        with console.work() as work:
            written = _export(work, sheet, season, handle)
    finally:
        if destination is not None:
            handle.close()
    if destination is not None:
        console.say(f"{written} rows to {destination}")


def _export(work: UnitOfWork, sheet: str, season: Season, handle: TextIO) -> int:
    if sheet == "days":
        return write_days(handle, season_summary(work, season))
    if sheet == "stops":
        stops = [
            one
            for code in work.lifts.codes()
            for day in work.day_logs.days_logged(code)
            if season.covers(day)
            for one in work.day_logs.stops_on(code, day).stops
        ]
        return write_stops(handle, _once(stops))
    if sheet == "incidents":
        return write_incidents(
            handle,
            [
                (incident, filing)
                for code in work.lifts.codes()
                for _, incident, filing in work.incidents.numbered_for_lift(code)
                if season.covers(incident.at.date())
            ],
        )
    surveys = [one for rope in work.ropes.all() for one in work.wear_surveys.for_rope(rope)]
    return write_wear(handle, [one for one in surveys if season.covers(one.taken)])


def _once(stops: list[StopEvent]) -> list[StopEvent]:
    """A stop that ran overnight belongs to two days and to one row."""
    seen: dict[tuple[str, str], StopEvent] = {}
    for one in stops:
        seen.setdefault((str(one.lift), one.stopped.isoformat()), one)
    return sorted(seen.values(), key=lambda one: (one.stopped, str(one.lift)))
