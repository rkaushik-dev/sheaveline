from datetime import date

import pytest
from click.testing import CliRunner

from hill import john_paul, strawberry, wildcat
from sheaveline.cli.main import cli
from sheaveline.persistence.base import build_engine, create_schema
from sheaveline.persistence.uow import UnitOfWork
from sheaveline.reports.forms import PAGE_WIDTH


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
        work.lifts.add(wildcat())
        work.commit()
    engine.dispose()
    return url


def run(runner, database, *args, **kwargs):
    return runner.invoke(cli, ["--database", database, *args], **kwargs)


def test_the_list_names_every_machine(runner, database):
    reply = run(runner, database, "lift", "list")
    assert reply.exit_code == 0
    assert "John Paul" in reply.output
    assert "Strawberry" in reply.output
    assert "Wildcat" in reply.output


def test_the_list_carries_the_class_and_the_carrier_count(runner, database):
    reply = run(runner, database, "lift", "list")
    assert "detachable chairlift" in reply.output
    assert "84" in reply.output
    assert "1000 fpm" in reply.output


def test_the_list_is_headed_and_ruled(runner, database):
    lines = run(runner, database, "lift", "list").output.splitlines()
    assert lines[0].startswith("code")
    assert set(lines[1]) <= {"-", " "}


def test_one_machine_prints_a_full_page(runner, database):
    reply = run(runner, database, "lift", "show", "JP")
    assert reply.exit_code == 0
    text = reply.output
    assert "JP JOHN PAUL" in text
    assert "class                    detachable chairlift" in text
    assert "vertical rise            1540 ft" in text
    assert "rope                     FAT-2019-04412" in text
    assert "rope length agrees       yes" in text


def test_the_page_works_out_the_capacity_rather_than_storing_it(runner, database):
    text = run(runner, database, "lift", "show", "JP").output
    assert "carrier interval" in text
    assert "people an hour" in text


def test_a_lower_case_code_works(runner, database):
    assert run(runner, database, "lift", "show", "jp").exit_code == 0


def test_a_machine_that_is_not_there_is_a_sentence_and_an_exit_code(runner, database):
    reply = run(runner, database, "lift", "show", "ZZ")
    assert reply.exit_code == 1
    assert "Traceback" not in reply.output
    assert "there is no lift ZZ" in reply.output


def test_something_that_is_not_a_code_is_refused_the_same_way(runner, database):
    reply = run(runner, database, "lift", "show", "1A")
    assert reply.exit_code == 1
    assert "a lift code" in reply.output


def test_the_tower_schedule_lists_every_tower(runner, database):
    reply = run(runner, database, "lift", "towers", "JP")
    assert reply.exit_code == 0
    for number in range(1, 8):
        assert f"JP-T{number}" in reply.output


def test_the_tower_schedule_carries_the_crossarm_elevation(runner, database):
    lines = run(runner, database, "lift", "towers", "JP").output.splitlines()
    assert "crossarm" in lines[0]
    assert "6567" in "\n".join(lines)


def test_the_spans_are_numbered_and_marked(runner, database):
    reply = run(runner, database, "lift", "spans", "JP")
    assert reply.exit_code == 0
    assert "longest" in reply.output
    assert "steepest" in reply.output
    assert "John Paul base to" in reply.output


def test_every_printed_line_fits_the_page(runner, database):
    for args in (
        ("lift", "list"),
        ("lift", "show", "JP"),
        ("lift", "towers", "JP"),
        ("lift", "spans", "JP"),
    ):
        reply = run(runner, database, *args)
        assert all(len(one) <= PAGE_WIDTH for one in reply.output.splitlines()), args


def test_withdrawing_asks_first(runner, database):
    reply = run(runner, database, "lift", "withdraw", "WC", input="n\n")
    assert reply.exit_code != 0
    assert run(runner, database, "lift", "list").output.count("Wildcat") == 1


def test_answering_yes_takes_the_machine_off(runner, database):
    reply = run(runner, database, "lift", "withdraw", "WC", input="y\n")
    assert reply.exit_code == 0
    assert "WC withdrawn" in reply.output
    assert "Wildcat" not in run(runner, database, "lift", "list").output


def test_the_flag_skips_the_question(runner, database):
    reply = run(runner, database, "lift", "withdraw", "WC", "--yes")
    assert reply.exit_code == 0
    assert "?" not in reply.output


def test_withdrawing_a_machine_that_is_not_there(runner, database):
    reply = run(runner, database, "lift", "withdraw", "ZZ", "--yes")
    assert reply.exit_code == 1
    assert "there is no lift ZZ" in reply.output


def test_the_group_lists_its_commands(runner, database):
    reply = run(runner, database, "lift", "--help")
    for name in ("list", "show", "towers", "spans", "withdraw"):
        assert name in reply.output


def test_the_page_reads_the_day_the_console_thinks_it_is(runner, database):
    reply = run(runner, database, "lift", "show", "JP")
    assert "age " in reply.output
    assert f"{date.today().year - 2004}" in reply.output or "years" in reply.output
