import pytest
from click.testing import CliRunner

from hill import john_paul, strawberry
from sheaveline.cli import cli
from sheaveline.ops.logbook import DayLog
from sheaveline.ops.preop import STANDARD_SHEET, CheckOutcome, CheckResult, PreOpSheet
from sheaveline.persistence.base import build_engine, create_schema
from sheaveline.persistence.uow import UnitOfWork
from sheaveline.reports.forms import PAGE_WIDTH
from sheaveline.schedule.hours import OperatingWindow

ON = "2025-01-15"
WINDOW = OperatingWindow.parse("09:00", "16:00", "16:30")


@pytest.fixture
def runner():
    return CliRunner()


@pytest.fixture
def database(tmp_path):
    url = f"sqlite+pysqlite:///{tmp_path / 'office.sqlite3'}"
    engine = build_engine(url)
    create_schema(engine)
    with UnitOfWork.on(engine) as work:
        for machine in (john_paul(), strawberry()):
            work.lifts.add(machine)
        work.commit()
    engine.dispose()
    return url


def run(runner, database, *args):
    return runner.invoke(cli, ["--database", database, *args])


def write_up(database, machine, day):
    engine = build_engine(database)
    with UnitOfWork.on(engine) as work:
        sheet = PreOpSheet.of(
            lift=machine,
            day=day,
            results=[
                CheckResult(item=item, outcome=CheckOutcome.PASS, by="R Skaggs")
                for item in STANDARD_SHEET
                if item.applies_to(machine)
            ],
            mechanic="R Skaggs",
            operator="D Chin",
        )
        work.day_logs.add(DayLog.start(machine.code, day, WINDOW, sheet=sheet))
        work.commit()
    engine.dispose()


def test_the_morning_page_prints_eighty_columns(runner, database):
    reply = run(runner, database, "report", "morning", "--on", ON)
    assert reply.exit_code == 0
    assert "John Paul" in reply.output
    assert max(len(one) for one in reply.output.splitlines()) <= PAGE_WIDTH


def test_the_morning_page_names_what_is_stopping_a_machine(runner, database):
    reply = run(runner, database, "report", "morning", "--on", ON)
    assert "morning walk not signed" in reply.output


def test_the_snow_report_goes_on_the_page(runner, database):
    reply = run(runner, database, "report", "morning", "--on", ON, "--snow", "eight overnight")
    assert "eight overnight" in reply.output


def test_the_line_walk_is_asked_for_by_naming_a_tension(runner, database):
    plain = run(runner, database, "report", "morning", "--on", ON)
    walked = run(runner, database, "report", "morning", "--on", ON, "--tension", "34500 lb")
    assert plain.exit_code == 0
    assert walked.exit_code == 0
    assert walked.output != plain.output


def test_a_tension_that_will_not_read(runner, database):
    reply = run(runner, database, "report", "morning", "--on", ON, "--tension", "hard")
    assert reply.exit_code == 1
    assert "hard" in reply.output


def test_the_season_page_adds_up_what_was_written_down(runner, database):
    from datetime import date, timedelta

    for offset in range(3):
        write_up(database, john_paul(), date(2025, 1, 15) + timedelta(days=offset))
    reply = run(
        runner,
        database,
        "report",
        "season",
        "2024-25",
        "--opens",
        "2024-12-01",
        "--closes",
        "2025-04-15",
    )
    assert reply.exit_code == 0
    assert "SEASON SUMMARY 2024-25" in reply.output
    assert "by machine" in reply.output
    assert max(len(one) for one in reply.output.splitlines()) <= PAGE_WIDTH


def test_a_season_that_closes_before_it_opens(runner, database):
    reply = run(
        runner,
        database,
        "report",
        "season",
        "2024-25",
        "--opens",
        "2025-04-15",
        "--closes",
        "2024-12-01",
    )
    assert reply.exit_code == 1
    assert "closes before it opens" in reply.output


def test_the_rope_file_still_prints_from_its_own_group(runner, database):
    reply = run(runner, database, "rope", "list")
    assert reply.exit_code == 0
