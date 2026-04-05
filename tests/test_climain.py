import pytest
from click.testing import CliRunner

from sheaveline import __version__
from sheaveline.cli.main import DEFAULT_URL, ENVIRONMENT_URL, Console, cli
from sheaveline.errors import SheavelineError
from sheaveline.persistence.base import build_engine, create_schema
from sheaveline.plant.classes import PlantError


@pytest.fixture
def runner():
    return CliRunner()


@pytest.fixture
def database(tmp_path):
    return f"sqlite+pysqlite:///{tmp_path / 'office.sqlite3'}"


def test_the_group_prints_its_own_help(runner):
    reply = runner.invoke(cli, ["--help"])
    assert reply.exit_code == 0
    assert "Lift department records" in reply.output
    assert "--database" in reply.output


def test_the_version_is_the_package_version(runner):
    reply = runner.invoke(cli, ["--version"])
    assert reply.exit_code == 0
    assert __version__ in reply.output


def test_setup_builds_the_tables(runner, database):
    reply = runner.invoke(cli, ["--database", database, "setup"])
    assert reply.exit_code == 0
    assert "tables ready" in reply.output


def test_setup_is_safe_to_run_twice(runner, database):
    runner.invoke(cli, ["--database", database, "setup"])
    reply = runner.invoke(cli, ["--database", database, "setup"])
    assert reply.exit_code == 0


def test_the_tables_can_be_listed(runner, database):
    reply = runner.invoke(cli, ["--database", database, "tables"])
    assert reply.exit_code == 0
    assert "lifts" in reply.output
    assert "haul_ropes" in reply.output


def test_check_says_what_is_in_the_database(runner, database):
    runner.invoke(cli, ["--database", database, "setup"])
    reply = runner.invoke(cli, ["--database", database, "check"])
    assert reply.exit_code == 0
    assert "lifts     0" in reply.output
    assert "ropes     0" in reply.output


def test_check_names_the_machines_when_there_are_any(runner, database):
    from hill import john_paul

    runner.invoke(cli, ["--database", database, "setup"])
    engine = build_engine(database)
    from sheaveline.persistence.uow import UnitOfWork

    with UnitOfWork.on(engine) as work:
        work.lifts.add(john_paul())
        work.commit()
    engine.dispose()
    reply = runner.invoke(cli, ["--database", database, "check"])
    assert "lifts     1" in reply.output
    assert "codes     JP" in reply.output


def test_the_database_comes_from_the_environment_when_it_is_set(runner, database, monkeypatch):
    monkeypatch.setenv(ENVIRONMENT_URL, database)
    reply = runner.invoke(cli, ["setup"])
    assert reply.exit_code == 0
    reply = runner.invoke(cli, ["check"])
    assert database.split("///")[-1] in reply.output


def test_an_explicit_database_beats_the_environment(runner, tmp_path, monkeypatch):
    monkeypatch.setenv(ENVIRONMENT_URL, "sqlite+pysqlite:///wrong.sqlite3")
    chosen = f"sqlite+pysqlite:///{tmp_path / 'right.sqlite3'}"
    runner.invoke(cli, ["--database", chosen, "setup"])
    reply = runner.invoke(cli, ["--database", chosen, "check"])
    assert "right.sqlite3" in reply.output


def test_the_default_url_is_a_file_beside_the_office_machine():
    assert DEFAULT_URL.endswith("sheaveline.sqlite3")


def test_a_console_hands_out_a_unit_of_work():
    engine = build_engine()
    create_schema(engine)
    console = Console(engine)
    with console.work() as work:
        assert work.lifts.codes() == []
    engine.dispose()


def test_a_console_can_be_told_what_day_it_is():
    from datetime import date

    engine = build_engine()
    console = Console(engine, today=lambda: date(2025, 1, 15))
    assert console.today() == date(2025, 1, 15)
    engine.dispose()


def test_a_domain_refusal_is_one_sentence_and_an_exit_code(runner):
    from sheaveline.cli.main import handles_refusals

    @handles_refusals
    def refuse() -> None:
        raise PlantError("a quad does not take a six place")

    with pytest.raises(SystemExit) as stopped:
        refuse()
    assert stopped.value.code == 1


def test_anything_that_is_not_a_domain_refusal_is_left_alone():
    from sheaveline.cli.main import handles_refusals

    @handles_refusals
    def burst() -> None:
        raise RuntimeError("something else entirely")

    with pytest.raises(RuntimeError):
        burst()


def test_a_refusal_reaching_a_command_prints_without_a_traceback(runner, tmp_path):
    missing = f"sqlite+pysqlite:///{tmp_path / 'never-set-up.sqlite3'}"
    reply = runner.invoke(cli, ["--database", missing, "check"])
    assert reply.exit_code != 0
    assert "Traceback" not in reply.output
    assert "run setup" in reply.output


def test_every_command_carries_a_line_of_help(runner):
    reply = runner.invoke(cli, ["--help"])
    for name in ("setup", "tables", "check"):
        assert name in reply.output


def test_the_console_writes_every_line_it_is_given(runner, database):
    """Read through a real command, so the test does not depend on how the
    runner happens to split the two streams in this version of click."""
    runner.invoke(cli, ["--database", database, "setup"])
    reply = runner.invoke(cli, ["--database", database, "check"])
    assert reply.exit_code == 0
    assert "database" in reply.output
    assert "lifts" in reply.output
    assert "ropes" in reply.output


def test_a_console_holds_the_engine_it_was_given():
    engine = build_engine()
    console = Console(engine)
    assert console.engine is engine
    engine.dispose()


def test_a_sheaveline_error_subclass_is_caught_by_the_wrapper():
    from sheaveline.cli.main import handles_refusals

    class Odd(SheavelineError):
        pass

    @handles_refusals
    def refuse() -> None:
        raise Odd("something the domain refused")

    with pytest.raises(SystemExit):
        refuse()


def test_a_database_that_will_not_open_is_a_sentence_too(runner, tmp_path):
    nowhere = "sqlite+pysqlite:////no/such/place/office.sqlite3"
    reply = runner.invoke(cli, ["--database", nowhere, "setup"])
    assert reply.exit_code == 2
    assert "the database will not answer" in reply.output
    assert "Traceback" not in reply.output


def test_a_database_that_has_not_been_set_up_says_to_run_setup(runner, tmp_path):
    empty = f"sqlite+pysqlite:///{tmp_path / 'empty.sqlite3'}"
    reply = runner.invoke(cli, ["--database", empty, "check"])
    assert reply.exit_code == 1
    assert "run setup" in reply.output
    assert "Traceback" not in reply.output


def test_a_database_failure_is_told_apart_from_one_that_is_merely_empty(runner, tmp_path):
    nowhere = "sqlite+pysqlite:////no/such/place/office.sqlite3"
    empty = f"sqlite+pysqlite:///{tmp_path / 'empty.sqlite3'}"
    assert runner.invoke(cli, ["--database", nowhere, "check"]).exit_code == 2
    assert runner.invoke(cli, ["--database", empty, "check"]).exit_code == 1


def test_the_driver_sentence_is_one_line(runner):
    from sqlalchemy.exc import OperationalError

    from sheaveline.cli.main import _first_line

    error = OperationalError("select 1", {}, Exception("unable to open database file"))
    assert "\n" not in _first_line(error)
