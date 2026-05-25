import pytest
from click.testing import CliRunner

from hill import john_paul, strawberry
from sheaveline.cli import cli
from sheaveline.persistence.base import build_engine, create_schema
from sheaveline.persistence.uow import UnitOfWork

AT = "2025-01-15 11:30"


@pytest.fixture
def runner():
    return CliRunner()


@pytest.fixture
def database(tmp_path):
    url = f"sqlite+pysqlite:///{tmp_path / 'office.sqlite3'}"
    engine = build_engine(url)
    create_schema(engine)
    with UnitOfWork.on(engine) as work:
        work.lifts.add(john_paul())
        work.lifts.add(strawberry())
        work.commit()
    engine.dispose()
    return url


def run(runner, database, *args):
    return runner.invoke(cli, ["--database", database, *args])


def recorded(runner, database, kind="loading", injury="none", code="JP", at=AT):
    return run(
        runner,
        database,
        "board",
        "record",
        code,
        "skier caught a ski under the bail on loading",
        "--kind",
        kind,
        "--injury",
        injury,
        "--by",
        "D Chin",
        "--at",
        at,
    )


def test_a_loading_bump_goes_in_the_monthly_return(runner, database):
    reply = recorded(runner, database)
    assert reply.exit_code == 0
    assert "incident 1" in reply.output
    assert "file by" in reply.output


def test_a_deropement_has_to_be_filed_at_once_and_stops_the_machine(runner, database):
    reply = recorded(runner, database, kind="deropement")
    assert reply.exit_code == 0
    assert "does not run again until the board says so" in reply.output
    assert "2025-01-15 11:30" in reply.output


def test_a_serious_injury_shortens_the_deadline_on_a_routine_kind(runner, database):
    routine = recorded(runner, database, kind="loading", injury="none").output
    hurt = recorded(
        runner, database, kind="loading", injury="admitted", at="2025-01-15 12:30"
    ).output
    assert "driven by" in hurt
    assert routine != hurt


def test_a_machine_that_is_not_on_the_register(runner, database):
    reply = recorded(runner, database, code="ZZ")
    assert reply.exit_code == 1


def test_what_is_waiting_to_be_filed(runner, database):
    assert "nothing is waiting" in run(runner, database, "board", "unfiled").output
    recorded(runner, database, kind="deropement")
    reply = run(runner, database, "board", "unfiled", "--at", "2025-01-15 12:00")
    assert reply.exit_code == 0
    assert "deropement" in reply.output


def test_an_incident_past_its_deadline_is_marked_late(runner, database):
    recorded(runner, database, kind="deropement")
    reply = run(runner, database, "board", "unfiled", "--at", "2025-02-01 12:00")
    assert "LATE" in reply.output


def test_filing_takes_a_number_and_the_incident_comes_off_the_waiting_list(runner, database):
    recorded(runner, database, kind="deropement")
    filed = run(
        runner,
        database,
        "board",
        "file",
        "1",
        "--kind",
        "incident",
        "--by",
        "R Skaggs",
        "--at",
        "2025-01-15 12:00",
    )
    assert filed.exit_code == 0
    assert "UT-2025-0001" in filed.output
    assert "nothing is waiting" in run(runner, database, "board", "unfiled").output


def test_a_late_filing_says_how_late_it_was(runner, database):
    recorded(runner, database, kind="deropement")
    filed = run(
        runner,
        database,
        "board",
        "file",
        "1",
        "--kind",
        "incident",
        "--by",
        "R Skaggs",
        "--at",
        "2025-01-20 12:00",
    )
    assert "late by" in filed.output


def test_a_rope_change_filing_waits_for_an_acknowledgement(runner, database):
    recorded(runner, database)
    filed = run(
        runner,
        database,
        "board",
        "file",
        "1",
        "--kind",
        "rope_change",
        "--by",
        "R Skaggs",
        "--at",
        "2025-01-16 09:00",
    )
    assert "watch for it coming back" in filed.output


def test_the_same_incident_is_not_filed_twice(runner, database):
    recorded(runner, database)
    args = ("board", "file", "1", "--kind", "incident", "--by", "R Skaggs", "--at", AT)
    run(runner, database, *args)
    again = run(runner, database, *args)
    assert again.exit_code == 1
    assert "already filed" in again.output


def test_filing_something_nobody_recorded(runner, database):
    reply = run(runner, database, "board", "file", "9", "--kind", "incident", "--by", "R Skaggs")
    assert reply.exit_code == 1
    assert "there is no incident 9" in reply.output


def test_one_machines_register(runner, database):
    empty = run(runner, database, "board", "show", "SB")
    assert "nothing is on the register for SB" in empty.output
    recorded(runner, database)
    reply = run(runner, database, "board", "show", "JP")
    assert reply.exit_code == 0
    assert "not filed" in reply.output


def test_the_monthly_return_lists_the_month_it_was_asked_about(runner, database):
    recorded(runner, database, at="2025-01-15 11:30")
    recorded(runner, database, at="2025-02-15 11:30")
    reply = run(runner, database, "board", "return", "2025", "1")
    assert reply.exit_code == 0
    assert "incidents 1" in reply.output


def test_a_december_return_does_not_run_off_the_end_of_the_year(runner, database):
    recorded(runner, database, at="2024-12-20 11:30")
    reply = run(runner, database, "board", "return", "2024", "12")
    assert reply.exit_code == 0
    assert "incidents 1" in reply.output
