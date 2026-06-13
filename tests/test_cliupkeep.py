import pytest
from click.testing import CliRunner

from hill import JOHN_PAUL, john_paul, strawberry
from office import stocked
from sheaveline.cli import cli

ON = "2025-01-15"


@pytest.fixture
def runner():
    return CliRunner()


@pytest.fixture
def database(tmp_path):
    return stocked(tmp_path, john_paul, strawberry)


def run(runner, database, *args):
    return runner.invoke(cli, ["--database", database, *args])


def found(runner, database, defect, job="A1"):
    return run(
        runner,
        database,
        "upkeep",
        "record",
        "JP",
        job,
        "--by",
        "R Skaggs",
        "--defect",
        defect,
        "--on",
        ON,
    )


def test_a_machine_with_no_records_has_the_whole_ladder_due(runner, database):
    reply = run(runner, database, "upkeep", "due", "JP", "--on", ON)
    assert reply.exit_code == 0
    assert "overdue" in reply.output
    assert "out of service" in reply.output


def test_a_job_signed_off_comes_off_the_overdue_list(runner, database):
    before = run(runner, database, "upkeep", "due", "JP", "--on", ON).output
    run(runner, database, "upkeep", "record", "JP", "A1", "--by", "R Skaggs", "--on", ON)
    after = run(runner, database, "upkeep", "due", "JP", "--on", ON).output
    assert before.count("overdue") > after.count("overdue")


def test_a_job_that_is_not_on_this_machine(runner, database):
    reply = run(runner, database, "upkeep", "record", "JP", "ZZ", "--by", "R Skaggs", "--on", ON)
    assert reply.exit_code == 1
    assert "there is no job ZZ" in reply.output


def test_a_defect_is_written_down_with_the_job(runner, database):
    reply = found(runner, database, "JP-T4:defect:liner cracked on the outer sheave")
    assert reply.exit_code == 0
    assert "liner cracked" in reply.output
    assert "wants a work order raising" in reply.output


def test_an_observation_does_not_want_a_work_order(runner, database):
    reply = found(runner, database, "JP-T2:observation:paint going at the base")
    assert reply.exit_code == 0
    assert "wants a work order raising" not in reply.output


def test_a_defect_that_is_not_written_the_way_the_sheet_writes_them(runner, database):
    reply = found(runner, database, "JP-T4 liner cracked")
    assert reply.exit_code == 1
    assert "where:level:what" in reply.output


def test_a_level_nobody_uses(runner, database):
    reply = found(runner, database, "JP-T4:awful:liner cracked")
    assert reply.exit_code == 1
    assert "observation" in reply.output


def test_the_open_defects_are_listed(runner, database):
    found(runner, database, "JP-T4:defect:liner cracked on the outer sheave")
    reply = run(runner, database, "upkeep", "defects")
    assert reply.exit_code == 0
    assert "JP-T4" in reply.output
    assert "liner cracked" in reply.output


def test_nothing_open_says_so(runner, database):
    assert "nothing open" in run(runner, database, "upkeep", "defects").output


def test_a_serious_defect_raises_an_order_that_stops_the_machine(runner, database):
    found(runner, database, "JP-DRV:serious:service brake will not hold")
    reply = run(runner, database, "upkeep", "raise", "JP", "JP-DRV", "--by", "R Skaggs")
    assert reply.exit_code == 0
    assert "does not carry the public" in reply.output


def test_an_ordinary_defect_raises_an_order_that_does_not(runner, database):
    found(runner, database, "JP-T4:defect:liner cracked on the outer sheave")
    reply = run(runner, database, "upkeep", "raise", "JP", "JP-T4", "--by", "R Skaggs")
    assert reply.exit_code == 0
    assert "does not carry the public" not in reply.output
    assert "due by" in reply.output


def test_raising_against_a_defect_nobody_found(runner, database):
    reply = run(runner, database, "upkeep", "raise", "JP", "JP-T9", "--by", "R Skaggs")
    assert reply.exit_code == 1
    assert "no open defect at JP-T9" in reply.output


def test_the_board_shows_the_open_orders_and_then_clears(runner, database):
    assert "the board is clear" in run(runner, database, "upkeep", "board").output
    found(runner, database, "JP-T4:defect:liner cracked on the outer sheave")
    run(runner, database, "upkeep", "raise", "JP", "JP-T4", "--by", "R Skaggs")
    board = run(runner, database, "upkeep", "board", "--on", ON)
    assert board.exit_code == 0
    assert "liner cracked" in board.output
    closed = run(runner, database, "upkeep", "close", "1", "--by", "R Skaggs", "--on", ON)
    assert closed.exit_code == 0
    assert "the board is clear" in run(runner, database, "upkeep", "board").output


def test_the_board_can_be_asked_about_one_machine(runner, database):
    found(runner, database, "JP-T4:defect:liner cracked on the outer sheave")
    run(runner, database, "upkeep", "raise", "JP", "JP-T4", "--by", "R Skaggs")
    assert "liner cracked" not in run(runner, database, "upkeep", "board", "--lift", "SB").output


def test_closing_an_order_nobody_raised(runner, database):
    reply = run(runner, database, "upkeep", "close", "9", "--by", "R Skaggs", "--on", ON)
    assert reply.exit_code == 1
    assert "there is no work order 9" in reply.output


def test_the_ladder_reading_names_the_machine_it_read(runner, database):
    reply = run(runner, database, "upkeep", "due", "JP", "--on", ON)
    assert "JP John Paul" in reply.output
    assert str(JOHN_PAUL) in reply.output
