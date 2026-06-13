import pytest
from click.testing import CliRunner

from hill import john_paul, strawberry
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


def started(runner, database, code="JP", *extra):
    return run(
        runner,
        database,
        "day",
        "start",
        code,
        "--on",
        ON,
        "--mechanic",
        "R Skaggs",
        "--operator",
        "D Chin",
        *extra,
    )


def test_starting_the_day_opens_the_book(runner, database):
    reply = started(runner, database)
    assert reply.exit_code == 0
    assert "JP 2025-01-15 open, 9:00 to 16:30" in reply.output


def test_a_day_started_without_a_sheet_says_it_may_not_run(runner, database):
    reply = run(runner, database, "day", "start", "JP", "--on", ON)
    assert reply.exit_code == 0
    assert "no sheet walked yet" in reply.output


def test_a_failed_critical_check_is_named_as_a_blocker(runner, database):
    reply = started(runner, database, "JP", "--fail", "b1:sheave train two is dry")
    assert reply.exit_code == 0
    assert "blocker" in reply.output
    assert "B1 failed" in reply.output


def test_a_failed_check_will_not_go_in_without_a_reason(runner, database):
    reply = started(runner, database, "JP", "--fail", "b1")
    assert reply.exit_code == 1
    assert "CODE:reason" in reply.output


def test_the_same_day_will_not_be_started_twice(runner, database):
    started(runner, database)
    reply = started(runner, database)
    assert reply.exit_code == 1
    assert "already has a log" in reply.output


def test_a_machine_that_is_not_on_the_register(runner, database):
    reply = started(runner, database, "ZZ")
    assert reply.exit_code == 1
    assert "there is no lift ZZ" in reply.output


def test_a_note_goes_in_the_book_and_comes_back_out(runner, database):
    started(runner, database)
    reply = run(
        runner,
        database,
        "day",
        "note",
        "JP",
        "10:15",
        "ramp regroomed",
        "--by",
        "D Chin",
        "--on",
        ON,
    )
    assert reply.exit_code == 0
    shown = run(runner, database, "day", "show", "JP", "--on", ON)
    assert "ramp regroomed" in shown.output
    assert "10:15" in shown.output


def test_a_stop_and_a_restart(runner, database):
    started(runner, database)
    stopped = run(runner, database, "day", "stop", "JP", "11:30", "--by", "D Chin", "--on", ON)
    assert stopped.exit_code == 0
    back = run(
        runner, database, "day", "restart", "JP", "11:30", "11:45", "--by", "D Chin", "--on", ON
    )
    assert back.exit_code == 0
    assert "JP running again, down 0:15:00" in back.output


def test_a_stop_long_enough_to_ice_a_grip_wants_the_line_walked(runner, database):
    started(runner, database)
    run(runner, database, "day", "stop", "JP", "11:30", "--by", "D Chin", "--on", ON)
    refused = run(
        runner, database, "day", "restart", "JP", "11:30", "12:05", "--by", "D Chin", "--on", ON
    )
    assert refused.exit_code == 1
    assert "line check" in refused.output
    walked = run(
        runner,
        database,
        "day",
        "restart",
        "JP",
        "11:30",
        "12:05",
        "--by",
        "D Chin",
        "--on",
        ON,
        "--line-checked",
    )
    assert walked.exit_code == 0


def test_a_deropement_says_the_line_gets_walked(runner, database):
    started(runner, database)
    reply = run(
        runner,
        database,
        "day",
        "stop",
        "JP",
        "11:30",
        "--kind",
        "deropement",
        "--by",
        "D Chin",
        "--note",
        "grip off at tower four",
        "--on",
        ON,
    )
    assert reply.exit_code == 0
    assert "the line is walked before this one runs again" in reply.output
    refused = run(
        runner, database, "day", "restart", "JP", "11:30", "12:05", "--by", "R Skaggs", "--on", ON
    )
    assert refused.exit_code == 1
    assert "line check" in refused.output


def test_a_reportable_stop_will_not_go_in_without_a_write_up(runner, database):
    started(runner, database)
    reply = run(
        runner,
        database,
        "day",
        "stop",
        "JP",
        "11:30",
        "--kind",
        "evacuation",
        "--by",
        "D Chin",
        "--on",
        ON,
    )
    assert reply.exit_code == 1
    assert "written up" in reply.output


def test_a_stop_that_ran_overnight_restarts_the_next_morning(runner, database):
    started(runner, database)
    run(
        runner,
        database,
        "day",
        "stop",
        "JP",
        "16:05",
        "--kind",
        "power",
        "--by",
        "D Chin",
        "--on",
        ON,
    )
    reply = run(
        runner,
        database,
        "day",
        "restart",
        "JP",
        "16:05",
        "08:40",
        "--by",
        "R Skaggs",
        "--on",
        ON,
        "--next-day",
        "--line-checked",
    )
    assert reply.exit_code == 0
    assert "16:35:00" in reply.output


def test_the_meter_is_written_down(runner, database):
    started(runner, database)
    reply = run(
        runner, database, "day", "meter", "JP", "40120.5", "1402000", "--by", "R Skaggs", "--on", ON
    )
    assert reply.exit_code == 0
    assert "40120.5" in reply.output


def test_what_is_stopped_right_now(runner, database):
    started(runner, database)
    assert "nothing is stopped" in run(runner, database, "day", "open-stops").output
    run(runner, database, "day", "stop", "JP", "11:30", "--by", "D Chin", "--on", ON)
    reply = run(runner, database, "day", "open-stops")
    assert reply.exit_code == 0
    assert "JP" in reply.output
    assert "2025-01-15 11:30" in reply.output


def test_open_stops_can_be_asked_about_one_machine(runner, database):
    run(runner, database, "day", "stop", "JP", "11:30", "--by", "D Chin", "--on", ON)
    reply = run(runner, database, "day", "open-stops", "--lift", "SB")
    assert "nothing is stopped" in reply.output


def test_showing_a_day_nobody_wrote_up(runner, database):
    reply = run(runner, database, "day", "show", "JP", "--on", ON)
    assert reply.exit_code == 1
    assert "there is no log for JP on 2025-01-15" in reply.output
