"""A whole season through the front doors, with nothing reaching past them.

Every other test in this suite holds something still. This one does not. It
builds the hill through the command line, runs it for a stretch of days, breaks
it, fixes it, reports it to the board and then asks for the annual packet, and it
only ever touches the two surfaces a person touches. Nothing here imports a
repository or builds a domain object.

That is the point. Each piece has been proved on its own and the thing that keeps
going wrong in software like this is the seam between them, so the seams are what
this walks.
"""

from datetime import date, timedelta

import pytest
from click.testing import CliRunner
from fastapi import status

from hill import john_paul, strawberry
from office import stocked
from sheaveline.api.app import Settings
from sheaveline.api.security import Role
from sheaveline.cli import cli
from sheaveline.persistence.base import build_engine
from wire import RING, fresh_client, headers

OPENS = date(2024, 12, 1)
CLOSES = date(2025, 4, 15)
FIRST = date(2025, 1, 13)
DAYS = [FIRST + timedelta(days=one) for one in range(5)]


@pytest.fixture
def runner():
    return CliRunner(mix_stderr=False)


@pytest.fixture
def hill(tmp_path):
    return stocked(tmp_path, john_paul, strawberry)


def run(runner, hill, *args):
    reply = runner.invoke(cli, ["--database", hill, *args])
    assert reply.exit_code == 0, f"{args}\n{reply.output}\n{reply.stderr}"
    return reply


def refuse(runner, hill, *args, code=1):
    reply = runner.invoke(cli, ["--database", hill, *args])
    assert reply.exit_code == code, f"{args} was allowed\n{reply.output}"
    return reply


def ladder_jobs(runner, hill, code="JP", on=None):
    """The job codes off the printed table, which is what a mechanic reads too."""
    when = (on or OPENS - timedelta(days=1)).isoformat()
    printed = run(runner, hill, "upkeep", "due", code, "--on", when).output
    found = []
    for line in printed.splitlines():
        first = line.split(" ")[0]
        if len(first) == 2 and first[0].isupper() and first[1].isdigit():
            found.append(first)
    return found


def sign_off_the_ladder(runner, hill, code="JP", on=None):
    """Every job on the machine's ladder done the day before the season opens."""
    when = (on or OPENS - timedelta(days=1)).isoformat()
    for job in ladder_jobs(runner, hill, code, on):
        run(
            runner,
            hill,
            "upkeep",
            "record",
            code,
            job,
            "--by",
            "R Skaggs",
            "--hours",
            "40000.0",
            "--cycles",
            "1400000",
            "--on",
            when,
        )
    run(
        runner,
        hill,
        "evac",
        "drill",
        code,
        "--team",
        "alpha",
        "--team",
        "bravo",
        "--carriers",
        "8",
        "--minutes",
        "52",
        "--observer",
        "M Vetter",
        "--on",
        when,
    )


def open_the_day(runner, hill, day, code="JP"):
    run(
        runner,
        hill,
        "day",
        "start",
        code,
        "--mechanic",
        "R Skaggs",
        "--operator",
        "D Chin",
        "--on",
        day.isoformat(),
    )
    run(
        runner,
        hill,
        "day",
        "meter",
        code,
        f"{40000 + (day - FIRST).days * 8}.0",
        str(1_400_000 + (day - FIRST).days * 4000),
        "--by",
        "R Skaggs",
        "--on",
        day.isoformat(),
    )


def test_a_season_runs_from_an_empty_register_to_a_signed_packet(runner, hill):
    # December: the ladder is signed off and the machine is drilled.
    sign_off_the_ladder(runner, hill, "JP")
    sign_off_the_ladder(runner, hill, "SB")

    # January: five operating days, one of which goes wrong.
    for day in DAYS:
        open_the_day(runner, hill, day)
    bad = DAYS[2]
    run(
        runner,
        hill,
        "day",
        "stop",
        "JP",
        "11:30",
        "--kind",
        "safety_circuit",
        "--by",
        "D Chin",
        "--note",
        "tower six stop opened, nothing found",
        "--on",
        bad.isoformat(),
    )
    run(
        runner,
        hill,
        "day",
        "restart",
        "JP",
        "11:30",
        "12:20",
        "--by",
        "R Skaggs",
        "--line-checked",
        "--on",
        bad.isoformat(),
    )

    # The walk that afternoon turns up a defect, which becomes a work order.
    run(
        runner,
        hill,
        "upkeep",
        "record",
        "JP",
        "W1",
        "--by",
        "R Skaggs",
        "--defect",
        "JP-T6:defect:stop circuit terminal corroded",
        "--on",
        bad.isoformat(),
    )
    raised = run(runner, hill, "upkeep", "raise", "JP", "JP-T6", "--by", "R Skaggs")
    assert "due by" in raised.output

    # The board hears about the stop, because a safety circuit stop with a hold is not routine.
    run(
        runner,
        hill,
        "board",
        "record",
        "JP",
        "stop circuit opened with the public on the line",
        "--kind",
        "electrical",
        "--by",
        "D Chin",
        "--at",
        f"{bad.isoformat()} 11:30",
    )
    filed = run(
        runner,
        hill,
        "board",
        "file",
        "1",
        "--kind",
        "incident",
        "--by",
        "M Vetter",
        "--at",
        f"{bad.isoformat()} 16:00",
    )
    assert "UT-2025-0001" in filed.output

    # The work is done and the order closes.
    board = run(runner, hill, "upkeep", "board", "--on", DAYS[3].isoformat())
    assert "corroded" in board.output
    run(runner, hill, "upkeep", "close", "1", "--by", "R Skaggs", "--on", DAYS[3].isoformat())
    assert "the board is clear" in run(runner, hill, "upkeep", "board").output

    # May: the season is added up and the packet goes to the state.
    summary = run(
        runner,
        hill,
        "report",
        "season",
        "2024-25",
        "--opens",
        OPENS.isoformat(),
        "--closes",
        CLOSES.isoformat(),
    )
    assert "SEASON SUMMARY 2024-25" in summary.output
    assert "JP" in summary.output

    packet = runner.invoke(
        cli,
        [
            "--database",
            hill,
            "report",
            "packet",
            "2024-25",
            "--opens",
            OPENS.isoformat(),
            "--closes",
            CLOSES.isoformat(),
            "--by",
            "M Vetter",
            "--on",
            "2025-05-12",
        ],
    )
    assert "ANNUAL PACKET 2024-25" in packet.output
    assert "UT-2025-0001" in packet.output
    assert "NOT FILED" not in packet.output


@pytest.fixture
def tablet(hill, runner):
    """The http surface reading the same database the commands just wrote to."""
    engine = build_engine(hill)
    built = Settings(engine, keys=RING, today=lambda: FIRST)
    with fresh_client(built) as opened:
        yield opened
    engine.dispose()


def test_what_the_office_typed_is_what_the_terminal_tablet_reads(runner, hill, tablet):
    sign_off_the_ladder(runner, hill, "JP")
    open_the_day(runner, hill, FIRST)

    day = tablet.get(f"/lifts/JP/days/{FIRST.isoformat()}", headers=headers())
    assert day.status_code == status.HTTP_200_OK
    assert day.json()["sheet_walked"]
    assert day.json()["may_open"]
    assert day.json()["meter"]["cycles"] == 1_400_000

    listed = tablet.get("/lifts/JP/days", headers=headers())
    assert listed.json()["items"] == [FIRST.isoformat()]
    assert listed.json()["total"] == 1


def test_what_the_tablet_writes_is_what_the_office_reads_back(runner, hill, tablet):
    sign_off_the_ladder(runner, hill, "JP")
    open_the_day(runner, hill, FIRST)

    stopped = tablet.post(
        "/lifts/JP/stops",
        json={
            "kind": "loading gate",
            "stopped": f"{FIRST.isoformat()}T11:30:00",
            "stopped_by": "D Chin",
        },
        headers=headers(Role.OPERATOR),
    )
    assert stopped.status_code == status.HTTP_201_CREATED
    assert tablet.get("/stops/open", headers=headers()).json()[0]["lift"] == "JP"

    shown = run(runner, hill, "day", "open-stops")
    assert "JP" in shown.output
    assert "loading gate" in shown.output

    restarted = tablet.post(
        "/lifts/JP/stops/restart",
        json={
            "stopped": f"{FIRST.isoformat()}T11:30:00",
            "restarted": f"{FIRST.isoformat()}T11:45:00",
            "restarted_by": "D Chin",
        },
        headers=headers(Role.OPERATOR),
    )
    assert restarted.json()["length"] == "0:15:00"
    assert "nothing is stopped" in run(runner, hill, "day", "open-stops").output


def test_the_season_arithmetic_is_the_same_whichever_door_it_came_in_by(runner, hill, tablet):
    sign_off_the_ladder(runner, hill, "JP")
    for day in DAYS[:3]:
        open_the_day(runner, hill, day)
    stopped = tablet.post(
        "/lifts/JP/stops",
        json={
            "kind": "loading gate",
            "stopped": f"{DAYS[1].isoformat()}T11:00:00",
            "stopped_by": "D Chin",
        },
        headers=headers(Role.OPERATOR),
    )
    assert stopped.status_code == status.HTTP_201_CREATED
    # Fifteen minutes, because at twenty the line has to be walked before it runs.
    restarted = tablet.post(
        "/lifts/JP/stops/restart",
        json={
            "stopped": f"{DAYS[1].isoformat()}T11:00:00",
            "restarted": f"{DAYS[1].isoformat()}T11:15:00",
            "restarted_by": "D Chin",
        },
        headers=headers(Role.OPERATOR),
    )
    assert restarted.status_code == status.HTTP_200_OK
    summary = run(
        runner,
        hill,
        "report",
        "season",
        "2024-25",
        "--opens",
        OPENS.isoformat(),
        "--closes",
        CLOSES.isoformat(),
    ).output
    assert "0.2 h" in summary
    assert "loading gate           1" in summary


def test_a_machine_that_has_not_been_drilled_is_named_in_the_packet(runner, hill):
    sign_off_the_ladder(runner, hill, "JP")
    packet = refuse(
        runner,
        hill,
        "report",
        "packet",
        "2024-25",
        "--opens",
        OPENS.isoformat(),
        "--closes",
        CLOSES.isoformat(),
        "--by",
        "M Vetter",
        "--on",
        "2025-05-12",
    )
    assert "SB has" in packet.output


def test_the_export_holds_the_same_numbers_the_page_printed(runner, hill, tmp_path):
    sign_off_the_ladder(runner, hill, "JP")
    for day in DAYS[:2]:
        open_the_day(runner, hill, day)
    made = tmp_path / "days.csv"
    run(
        runner,
        hill,
        "report",
        "export",
        "days",
        "--opens",
        OPENS.isoformat(),
        "--closes",
        CLOSES.isoformat(),
        "--to",
        str(made),
    )
    rows = made.read_text().splitlines()
    assert rows[0].startswith("lift,day,opened")
    assert len(rows) == 3
    assert all(one.startswith("JP,2025-01-1") for one in rows[1:])
