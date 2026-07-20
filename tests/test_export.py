import csv
import io
from datetime import date, datetime, timedelta

import pytest

from hill import JOHN_PAUL, john_paul, the_season
from sheaveline.board.incident import Incident, IncidentKind, InjuryLevel
from sheaveline.measures.duration import Duration
from sheaveline.measures.length import Length
from sheaveline.ops.stop import StopEvent, StopKind
from sheaveline.plant.identity import FilingNumber
from sheaveline.reports.export import (
    DAYS,
    SHEETS,
    STOPS,
    Sheet,
    write_days,
    write_incidents,
    write_stops,
    write_wear,
)
from sheaveline.reports.season import DayRecord, SeasonSummary
from sheaveline.ropework.wear import DiameterReading, WearSurvey

ROPE = john_paul().rope
ON = date(2025, 1, 15)


def written(call, *args):
    handle = io.StringIO()
    count = call(handle, *args)
    handle.seek(0)
    return count, list(csv.reader(handle))


def record(day=ON, charged=1800, stops=(StopKind.OPERATOR,)):
    scheduled = Duration.from_hours(7)
    return DayRecord(
        lift=JOHN_PAUL,
        day=day,
        opened=True,
        scheduled=scheduled,
        running=scheduled - Duration.from_seconds(charged),
        downtime=Duration.from_seconds(charged),
        chargeable=Duration.from_seconds(charged),
        stops=tuple(stops),
    )


def stop(hour=11, restarted=True, kind=StopKind.OPERATOR, note=""):
    return StopEvent(
        lift=JOHN_PAUL,
        kind=kind,
        stopped=datetime(2025, 1, 15, hour, 30),
        stopped_by="D Chin",
        note=note,
        restarted=datetime(2025, 1, 15, hour, 45) if restarted else None,
        restarted_by="D Chin" if restarted else None,
        line_checked=True,
    )


def test_a_sheet_writes_its_heading_first():
    count, rows = written(DAYS.write, [])
    assert count == 0
    assert rows == [list(DAYS.columns)]


def test_a_row_with_the_wrong_number_of_columns_is_refused():
    made = Sheet("small", ("one", "two"))
    with pytest.raises(ValueError, match="2 columns and got 3"):
        made.write(io.StringIO(), [(1, 2, 3)])


def test_nothing_becomes_an_empty_cell_rather_than_the_word_none():
    count, rows = written(write_stops, [stop(restarted=False)])
    assert count == 1
    assert rows[1][4] == ""
    assert rows[1][5] == ""
    assert rows[1][6] == ""


def test_the_days_go_out_as_the_integers_they_are_held_in():
    summary = SeasonSummary.of(the_season(), [record()])
    count, rows = written(write_days, summary)
    assert count == 1
    body = dict(zip(DAYS.columns, rows[1], strict=True))
    assert body["lift"] == "JP"
    assert body["scheduled_seconds"] == "25200"
    assert body["chargeable_seconds"] == "1800"
    assert body["availability"] == "0.9286"
    assert body["opened"] == "1"


def test_every_day_in_the_season_gets_a_row():
    days = [record(day=ON + timedelta(days=one)) for one in range(4)]
    count, rows = written(write_days, SeasonSummary.of(the_season(), days))
    assert count == 4
    assert len(rows) == 5


def test_a_stop_carries_what_the_kind_decides_about_it():
    count, rows = written(write_stops, [stop(kind=StopKind.POWER)])
    body = dict(zip(STOPS.columns, rows[1], strict=True))
    assert body["seconds"] == "900"
    assert body["counts_against_the_machine"] == "0"
    assert body["reportable"] == "0"
    assert body["line_checked"] == "1"


def test_a_deropement_is_marked_reportable():
    made = stop(kind=StopKind.DEROPEMENT, note="grip off at tower four")
    _, rows = written(write_stops, [made])
    body = dict(zip(STOPS.columns, rows[1], strict=True))
    assert body["reportable"] == "1"
    assert body["note"] == "grip off at tower four"


def test_an_incident_carries_its_deadline_and_its_filing():
    one = Incident(
        lift=JOHN_PAUL,
        at=datetime(2025, 1, 15, 11, 30),
        kind=IncidentKind.DEROPEMENT,
        injury=InjuryLevel.NONE,
        people=3,
        narrative="grip off at tower four",
        reported_by="D Chin",
    )
    count, rows = written(write_incidents, [(one, FilingNumber.parse("UT-2025-0001")), (one, None)])
    assert count == 2
    assert rows[1][6] == "UT-2025-0001"
    assert rows[2][6] == ""
    assert rows[1][5] == one.due_by().isoformat()


def test_a_wear_walk_becomes_one_row_a_station():
    survey = WearSurvey.of(
        ROPE,
        ON,
        "R Halvorsen",
        [
            DiameterReading(
                station=Length.from_feet(one * 600),
                across_crowns=Length.from_inches("1.354"),
                across_valleys=Length.from_inches("1.350"),
            )
            for one in range(1, 4)
        ],
    )
    count, rows = written(write_wear, [survey])
    assert count == 3
    assert rows[1][3] == "7200000"
    assert rows[1][4] == "1354"
    assert rows[1][5] == "1350"


def test_a_diameter_never_leaves_with_an_inch_mark_on_it():
    """A spreadsheet reads 1.354" as a date, and then the column is text."""
    survey = WearSurvey.of(
        ROPE,
        ON,
        "R Halvorsen",
        [
            DiameterReading(
                station=Length.from_feet(600),
                across_crowns=Length.from_inches("1.354"),
                across_valleys=Length.from_inches("1.350"),
            )
        ],
    )
    _, rows = written(write_wear, [survey])
    assert all('"' not in cell for cell in rows[1])
    assert all("'" not in cell for cell in rows[1])


def test_every_sheet_is_reachable_by_name():
    assert sorted(SHEETS) == ["days", "incidents", "stops", "wear"]
    assert all(name == sheet.name for name, sheet in SHEETS.items())


def test_no_two_sheets_share_a_column_layout_by_accident():
    layouts = [sheet.columns for sheet in SHEETS.values()]
    assert len(set(layouts)) == len(layouts)


def test_the_rows_end_the_same_way_on_every_machine():
    handle = io.StringIO()
    write_stops(handle, [stop()])
    assert "\r\n" not in handle.getvalue()
    assert handle.getvalue().endswith("\n")
