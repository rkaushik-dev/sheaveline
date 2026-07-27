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


def drilled(runner, database, code="JP", kind="full_line", carriers=6, on=ON, minutes=48):
    return run(
        runner,
        database,
        "evac",
        "drill",
        code,
        "--kind",
        kind,
        "--team",
        "alpha",
        "--team",
        "bravo",
        "--carriers",
        str(carriers),
        "--minutes",
        str(minutes),
        "--observer",
        "R Skaggs",
        "--on",
        on,
    )


def test_a_full_line_drill_carries_the_annual(runner, database):
    reply = drilled(runner, database)
    assert reply.exit_code == 0
    assert "the annual now runs to 2026-01-15" in reply.output


def test_a_drill_that_worked_too_few_carriers_does_not(runner, database):
    reply = drilled(runner, database, carriers=2)
    assert reply.exit_code == 0
    assert "does not satisfy the annual" in reply.output


def test_a_tabletop_does_not_satisfy_the_annual_either(runner, database):
    reply = drilled(runner, database, kind="tabletop", carriers=0)
    assert reply.exit_code == 0
    assert "does not satisfy the annual" in reply.output


def test_the_same_machine_is_not_drilled_twice_in_a_day(runner, database):
    drilled(runner, database)
    again = drilled(runner, database)
    assert again.exit_code == 1
    assert "already drilled" in again.output


def test_a_machine_that_is_not_on_the_register(runner, database):
    reply = drilled(runner, database, code="ZZ")
    assert reply.exit_code == 2


def test_a_hill_that_has_never_drilled_is_out_of_date(runner, database):
    reply = run(runner, database, "evac", "check", "--on", ON)
    assert reply.exit_code == 1
    assert "OUT OF DATE" in reply.output
    assert "does not carry the public" in reply.output
    assert "never" in reply.output


def test_a_drilled_machine_reads_current(runner, database):
    drilled(runner, database, code="JP")
    drilled(runner, database, code="SB")
    reply = run(runner, database, "evac", "check", "--on", ON)
    assert reply.exit_code == 0
    assert "OUT OF DATE" not in reply.output
    assert reply.output.count("current") == 2


def test_a_drill_that_has_run_out_reads_out_of_date_again(runner, database):
    drilled(runner, database, code="JP", on="2024-01-15")
    reply = run(runner, database, "evac", "check", "--on", ON)
    assert reply.exit_code == 1
    assert "OUT OF DATE" in reply.output


def test_the_staging_plan_parks_every_team_on_its_own_run(runner, database):
    reply = run(
        runner,
        database,
        "evac",
        "staging",
        "JP",
        "--team",
        "alpha:24:200",
        "--team",
        "bravo:24:200",
        "--on",
        ON,
    )
    assert reply.exit_code == 0
    assert "2 teams, 84 carriers" in reply.output
    assert "alpha" in reply.output
    assert "bravo" in reply.output


def test_a_team_written_the_wrong_way(runner, database):
    reply = run(runner, database, "evac", "staging", "JP", "--team", "alpha 24 200", "--on", ON)
    assert reply.exit_code == 1
    assert "name:ladder:rope" in reply.output


def test_a_ladder_alone_does_not_reach_a_chair_hanging_forty_feet_up(runner, database):
    """A dash means no rope on the truck, and on this line that is the whole job."""
    reply = run(runner, database, "evac", "staging", "JP", "--team", "alpha:24:-", "--on", ON)
    assert reply.exit_code == 1
    assert "nobody can work this line" in reply.output
    assert "nobody reaches it" in reply.output


def test_the_same_team_with_a_rope_on_the_truck_can_work_it(runner, database):
    reply = run(runner, database, "evac", "staging", "JP", "--team", "alpha:24:200", "--on", ON)
    assert reply.exit_code == 0
    assert "alpha" in reply.output
    assert "1 teams, 84 carriers" in reply.output


def test_a_short_team_cannot_reach_the_whole_line(runner, database):
    reply = run(runner, database, "evac", "staging", "JP", "--team", "alpha:8:-", "--on", ON)
    assert reply.exit_code == 1
    assert "nobody reaches it" in reply.output


def test_more_seats_filled_makes_the_teams_busier(runner, database):
    args = ("evac", "staging", "JP", "--team", "alpha:24:200", "--on", ON)
    quiet = run(runner, database, *args, "--seats", "2")
    busy = run(runner, database, *args, "--seats", "4")
    assert quiet.exit_code == 0
    assert busy.exit_code == 0
    assert quiet.output != busy.output
