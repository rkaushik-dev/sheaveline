from decimal import Decimal

import pytest
from click.testing import CliRunner

from hill import john_paul, jp_towers, strawberry
from office import stocked
from sheaveline.cli import cli

LINE = jp_towers()
AUTUMN = "2024-10-01"


@pytest.fixture
def runner():
    return CliRunner(mix_stderr=False)


@pytest.fixture
def database(tmp_path):
    return stocked(tmp_path, john_paul, strawberry)


def run(runner, database, *args):
    return runner.invoke(cli, ["--database", database, *args])


def shots(offsets=None, settled=None):
    offsets = offsets or {}
    settled = settled or {}
    given = []
    for tower in LINE:
        ref = str(tower.ref)
        crossarm = tower.crossarm_elevation().feet - Decimal(settled.get(ref, 0)) / 12
        given += ["--shot", f"{ref}:{tower.station.feet:.0f}:{crossarm:.3f}:{offsets.get(ref, 0)}"]
    return given


def shoot(runner, database, on=AUTUMN, offsets=None, settled=None, code="JP"):
    return run(
        runner,
        database,
        "lift",
        "shoot",
        code,
        "--surveyor",
        "H Ogilvie",
        "--on",
        on,
        *shots(offsets, settled),
    )


def test_a_line_shot_where_the_drawing_says_it_is_reads_clean(runner, database):
    reply = shoot(runner, database)
    assert reply.exit_code == 0
    assert "7 towers" in reply.output
    assert "every tower is where the drawing says it is" in reply.output


def test_a_tower_out_of_plumb_is_named_on_the_way_in(runner, database):
    reply = shoot(runner, database, offsets={"JP-T4": 18})
    assert reply.exit_code == 0
    assert "JP-T4 leans" in reply.output


def test_a_tower_that_has_settled_is_named_too(runner, database):
    reply = shoot(runner, database, settled={"JP-T5": 4})
    assert reply.exit_code == 0
    assert "JP-T5 has settled" in reply.output


def test_a_shot_written_the_wrong_way(runner, database):
    reply = run(
        runner,
        database,
        "lift",
        "shoot",
        "JP",
        "--surveyor",
        "H Ogilvie",
        "--shot",
        "JP-T4 1200 7040 0",
    )
    assert reply.exit_code == 1
    assert "tower:station:crossarm:offset" in reply.stderr


def test_the_same_machine_is_not_shot_twice_in_a_day(runner, database):
    shoot(runner, database)
    again = shoot(runner, database)
    assert again.exit_code == 1
    assert "already shot" in again.stderr


def test_a_tower_from_another_machine_does_not_belong_in_the_survey(runner, database):
    reply = run(
        runner,
        database,
        "lift",
        "shoot",
        "JP",
        "--surveyor",
        "H Ogilvie",
        "--shot",
        "SB-T2:600:6500:0",
    )
    assert reply.exit_code == 1
    assert "not on JP" in reply.stderr


def test_a_machine_nobody_has_shot(runner, database):
    reply = run(runner, database, "lift", "drift", "JP")
    assert reply.exit_code == 1
    assert "has never been shot" in reply.stderr


def test_one_survey_carries_no_rate(runner, database):
    shoot(runner, database)
    reply = run(runner, database, "lift", "drift", "JP")
    assert reply.exit_code == 0
    assert "read across 1 surveys" in reply.output
    assert "not moving" in reply.output


def test_two_surveys_give_a_rate_and_a_projection(runner, database):
    shoot(runner, database, on="2020-10-01")
    shoot(runner, database, on="2024-10-01", offsets={"JP-T4": 4})
    reply = run(runner, database, "lift", "drift", "JP")
    assert reply.exit_code == 0
    assert "read across 2 surveys" in reply.output
    assert "JP-T4" in reply.output
    assert "flat" in reply.output


def test_a_tower_going_over_fast_is_called_out(runner, database):
    shoot(runner, database, on="2023-10-01")
    shoot(runner, database, on="2024-10-01", offsets={"JP-T4": 10})
    reply = run(runner, database, "lift", "drift", "JP")
    assert "JP-T4 is out of plumb inside five years at this rate" in reply.output


def test_a_line_that_has_not_moved_is_not_called_out(runner, database):
    shoot(runner, database, on="2020-10-01")
    shoot(runner, database, on="2024-10-01")
    reply = run(runner, database, "lift", "drift", "JP")
    assert "out of plumb inside five years" not in reply.output
