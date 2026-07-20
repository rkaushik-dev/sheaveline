"""Handing the numbers to somebody who is going to open them in a spreadsheet.

The eighty column pages are for reading. This is for the finance office, who
want last season's downtime next to the year before's in a pivot table, and for
the state, who ask once a year for the incident register in a form they can load.

Two rules and they are not negotiable. Every measure goes out as the integer this
system holds it in, with the unit named in the column heading, because a
spreadsheet that reads 1.378" as a date has already lost. And every row is one
thing: a day, a stop, an incident. Nothing is grouped, nothing is subtotalled,
and no cell contains a formula. Whoever opens it is going to group it themselves
and they will do it differently every time.

Written straight to a handle rather than built in memory. A season of ten minute
weather readings across four stations is two hundred thousand rows and there is
no reason for all of it to exist at once.
"""

from __future__ import annotations

import csv
from collections.abc import Iterable, Sequence
from dataclasses import dataclass
from typing import TextIO

from sheaveline.board.incident import Incident
from sheaveline.ops.stop import StopEvent
from sheaveline.plant.identity import FilingNumber
from sheaveline.reports.season import SeasonSummary
from sheaveline.ropework.wear import WearSurvey


@dataclass(frozen=True)
class Sheet:
    """One export: its columns and how a row of the thing becomes a row of text."""

    name: str
    columns: tuple[str, ...]

    def write(self, handle: TextIO, rows: Iterable[Sequence[object]]) -> int:
        """Rows out, count back. The count is what the command prints."""
        writer = csv.writer(handle, lineterminator="\n")
        writer.writerow(self.columns)
        written = 0
        for row in rows:
            if len(row) != len(self.columns):
                raise ValueError(f"{self.name} has {len(self.columns)} columns and got {len(row)}")
            writer.writerow(["" if one is None else one for one in row])
            written += 1
        return written


DAYS = Sheet(
    "days",
    (
        "lift",
        "day",
        "opened",
        "scheduled_seconds",
        "running_seconds",
        "downtime_seconds",
        "chargeable_seconds",
        "availability",
        "stops",
    ),
)

STOPS = Sheet(
    "stops",
    (
        "lift",
        "kind",
        "stopped",
        "stopped_by",
        "restarted",
        "restarted_by",
        "seconds",
        "line_checked",
        "reportable",
        "counts_against_the_machine",
        "note",
    ),
)

INCIDENTS = Sheet(
    "incidents",
    (
        "lift",
        "at",
        "kind",
        "injury",
        "people",
        "due_by",
        "filing",
        "reported_by",
        "narrative",
    ),
)

WEAR = Sheet(
    "wear",
    (
        "rope",
        "taken",
        "inspector",
        "station_mils",
        "crowns_mils",
        "valleys_mils",
        "percent_of_nominal",
    ),
)


def day_rows(summary: SeasonSummary) -> Iterable[Sequence[object]]:
    for record in summary.records:
        yield (
            str(record.lift),
            record.day.isoformat(),
            int(record.opened),
            record.scheduled.seconds,
            record.running.seconds,
            record.downtime.seconds,
            record.chargeable.seconds,
            f"{record.availability():.4f}",
            len(record.stops),
        )


def stop_rows(stops: Iterable[StopEvent]) -> Iterable[Sequence[object]]:
    for one in stops:
        yield (
            str(one.lift),
            one.kind.value,
            one.stopped.isoformat(),
            one.stopped_by,
            None if one.restarted is None else one.restarted.isoformat(),
            one.restarted_by,
            None if one.is_open() else one.length().seconds,
            int(one.line_checked),
            int(one.kind.is_reportable),
            int(one.kind.counts_against_the_machine),
            one.note,
        )


def incident_rows(
    incidents: Iterable[tuple[Incident, FilingNumber | None]],
) -> Iterable[Sequence[object]]:
    for one, filing in incidents:
        yield (
            str(one.lift),
            one.at.isoformat(),
            one.kind.value,
            one.injury.value,
            one.people,
            one.due_by().isoformat(),
            None if filing is None else str(filing),
            one.reported_by,
            one.narrative,
        )


def wear_rows(surveys: Iterable[WearSurvey]) -> Iterable[Sequence[object]]:
    """One row a caliper reading, not one a walk. The pivot is on the station."""
    for survey in surveys:
        for reading in survey.diameters:
            yield (
                str(survey.rope.serial),
                survey.taken.isoformat(),
                survey.inspector,
                reading.station.mils,
                reading.across_crowns.mils,
                reading.across_valleys.mils,
                f"{survey.percent_of_nominal(reading):.2f}",
            )


def write_days(handle: TextIO, summary: SeasonSummary) -> int:
    return DAYS.write(handle, day_rows(summary))


def write_stops(handle: TextIO, stops: Iterable[StopEvent]) -> int:
    return STOPS.write(handle, stop_rows(stops))


def write_incidents(
    handle: TextIO, incidents: Iterable[tuple[Incident, FilingNumber | None]]
) -> int:
    return INCIDENTS.write(handle, incident_rows(incidents))


def write_wear(handle: TextIO, surveys: Iterable[WearSurvey]) -> int:
    return WEAR.write(handle, wear_rows(surveys))


SHEETS: dict[str, Sheet] = {
    DAYS.name: DAYS,
    STOPS.name: STOPS,
    INCIDENTS.name: INCIDENTS,
    WEAR.name: WEAR,
}
