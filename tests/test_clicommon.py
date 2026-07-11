"""The option sixteen commands were each writing out for themselves."""

import ast
import pathlib
from datetime import date, datetime

import click
from click.testing import CliRunner

from sheaveline.cli.common import (
    DAY_FORMAT,
    MOMENT_FORMAT,
    day_of,
    day_option,
    moment_of,
    moment_option,
    printed,
)

CLI = pathlib.Path(__file__).resolve().parents[1] / "src" / "sheaveline" / "cli"
TODAY = date(2025, 1, 15)
NOW = datetime(2025, 1, 15, 11, 30)


class Clock:
    def today(self):
        return TODAY

    def now(self):
        return NOW


def test_a_day_that_was_given_wins():
    assert day_of(Clock(), datetime(2024, 12, 1, 9, 0)) == date(2024, 12, 1)


def test_a_day_that_was_not_given_falls_back_to_the_console():
    assert day_of(Clock(), None) == TODAY


def test_a_moment_falls_back_the_same_way():
    assert moment_of(Clock(), None) == NOW
    assert moment_of(Clock(), datetime(2024, 12, 1, 9, 0)) == datetime(2024, 12, 1, 9, 0)


def test_a_date_on_a_page_or_a_word_saying_there_is_not_one():
    assert printed(date(2025, 1, 15)) == "2025-01-15"
    assert printed(None) == ""
    assert printed(None, "never") == "never"


def test_the_day_option_takes_the_form_a_board_form_takes():
    @click.command()
    @day_option()
    def one(on):
        click.echo(printed(day_of(Clock(), on), "nothing given"))

    runner = CliRunner()
    assert runner.invoke(one, ["--on", "2024-12-01"]).output.strip() == "2024-12-01"
    assert runner.invoke(one, []).output.strip() == TODAY.isoformat()
    assert runner.invoke(one, ["--on", "1 Dec 2024"]).exit_code == 2


def test_the_moment_option_takes_a_whole_day_as_well():
    @click.command()
    @moment_option()
    def one(at):
        click.echo(moment_of(Clock(), at).isoformat())

    runner = CliRunner()
    assert runner.invoke(one, ["--at", "2024-12-01 09:15"]).output.strip() == (
        "2024-12-01T09:15:00"
    )
    assert runner.invoke(one, ["--at", "2024-12-01"]).output.strip() == "2024-12-01T00:00:00"
    assert runner.invoke(one, []).output.strip() == NOW.isoformat()


def test_the_help_can_be_said_differently_per_command():
    @click.command()
    @day_option("The day the walk was done.")
    def one(on):
        pass

    assert "The day the walk was done." in CliRunner().invoke(one, ["--help"]).output


def test_the_formats_are_what_a_tramway_board_form_uses():
    assert DAY_FORMAT == "%Y-%m-%d"
    assert MOMENT_FORMAT == "%Y-%m-%d %H:%M"


def test_no_command_module_declares_the_formats_for_itself_any_more():
    for path in sorted(CLI.glob("*.py")):
        if path.name == "common.py":
            continue
        source = path.read_text()
        assert 'DAY_FORMAT = "%Y-%m-%d"' not in source, path.name
        assert 'MOMENT_FORMAT = "%Y-%m-%d %H:%M"' not in source, path.name


def test_no_command_module_writes_the_on_or_at_option_out_by_hand():
    for path in sorted(CLI.glob("*.py")):
        source = path.read_text()
        assert '@click.option("--on", "on"' not in source, path.name
        assert '@click.option("--at", "at"' not in source, path.name


def test_the_two_loaders_live_in_one_place():
    from sheaveline.cli.common import haul_rope, machine

    assert machine.__module__ == "sheaveline.cli.common"
    assert haul_rope.__module__ == "sheaveline.cli.common"
    for path in sorted(CLI.glob("*.py")):
        assert "def _load(" not in path.read_text(), path.name


def test_no_two_command_modules_define_the_same_private_helper():
    seen: dict[str, str] = {}
    for path in sorted(CLI.glob("*.py")):
        for node in ast.parse(path.read_text()).body:
            if not isinstance(node, ast.FunctionDef) or not node.name.startswith("_"):
                continue
            assert node.name not in seen, f"{node.name} in {path.name} and {seen[node.name]}"
            seen[node.name] = path.name


def test_the_clock_is_asked_for_by_shape_rather_than_by_type():
    """Otherwise this module would have to import the console, which imports it."""
    source = (CLI / "common.py").read_text()
    assert "Protocol" in source
    assert "from sheaveline.cli.main import" not in source
