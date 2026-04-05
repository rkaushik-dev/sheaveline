"""The command line, which is how the office actually works.

The http surface is for the tablets. Everything the office does at a keyboard
happens here: loading a machine from a survey, printing the morning report,
walking the rope, closing a work order at the end of the day.

Two decisions worth stating. The database url comes from the environment first
so the office machine never has it typed into a command, and a refusal is printed
as one sentence and an exit code rather than a traceback, because the person
reading it is a lift mechanic and a traceback tells them nothing they can act on.

A database that will not open counts as a refusal. It is the commonest failure
there is, it is nearly always a path or a permission, and the forty lines of
driver stack under it say nothing the person at the keyboard can use.
"""

from __future__ import annotations

import sys
from collections.abc import Callable
from datetime import date
from functools import wraps
from typing import Any, TypeVar

import click
from sqlalchemy import Engine, inspect
from sqlalchemy.exc import SQLAlchemyError

from sheaveline import __version__
from sheaveline.errors import SheavelineError
from sheaveline.persistence.base import build_engine, create_schema, table_names
from sheaveline.persistence.uow import UnitOfWork

DEFAULT_URL = "sqlite+pysqlite:///sheaveline.sqlite3"
ENVIRONMENT_URL = "SHEAVELINE_DATABASE_URL"

Result = TypeVar("Result")


class Console:
    """What every command is handed: a database and a way to write to the page."""

    def __init__(self, engine: Engine, today: Callable[[], date] | None = None) -> None:
        self.engine = engine
        self._today = today or date.today

    def today(self) -> date:
        return self._today()

    def work(self) -> UnitOfWork:
        return UnitOfWork.on(self.engine)

    def say(self, text: str = "") -> None:
        click.echo(text)

    def page(self, lines: list[str]) -> None:
        for one in lines:
            click.echo(one)

    def problem(self, text: str) -> None:
        click.echo(text, err=True)


def handles_refusals(command: Callable[..., Result]) -> Callable[..., Result]:
    """Print a refusal as a sentence and stop, rather than as a traceback."""

    @wraps(command)
    def run(*args: Any, **kwargs: Any) -> Result:
        try:
            return command(*args, **kwargs)
        except SheavelineError as refusal:
            click.echo(f"{type(refusal).__name__}: {refusal}", err=True)
            raise SystemExit(1) from refusal
        except SQLAlchemyError as broken:
            click.echo(f"the database will not answer: {_first_line(broken)}", err=True)
            raise SystemExit(2) from broken

    return run


def _first_line(error: Exception) -> str:
    """The driver's own sentence, without the stack it usually arrives with."""
    return str(error).strip().splitlines()[0]


@click.group()
@click.option(
    "--database",
    envvar=ENVIRONMENT_URL,
    default=DEFAULT_URL,
    show_default=True,
    help="Where the records live.",
)
@click.version_option(version=__version__, prog_name="sheaveline")
@click.pass_context
def cli(context: click.Context, database: str) -> None:
    """Lift department records for a passenger ropeway operation."""
    context.obj = Console(build_engine(database))


@cli.command()
@click.pass_obj
@handles_refusals
def setup(console: Console) -> None:
    """Build the tables. Safe to run on a database that already has them."""
    create_schema(console.engine)
    console.say(f"{len(table_names())} tables ready")


@cli.command()
@click.pass_obj
@handles_refusals
def tables(console: Console) -> None:
    """List the tables the package knows about."""
    for name in table_names():
        console.say(name)


@cli.command()
@click.pass_obj
@handles_refusals
def check(console: Console) -> None:
    """Say whether the database answers and what is in it."""
    built = set(inspect(console.engine).get_table_names())
    if not built.issuperset(table_names()):
        console.problem(f"{console.engine.url} has no tables yet, run setup")
        raise SystemExit(1)
    with console.work() as work:
        lifts = work.lifts.codes()
        ropes = work.ropes.all()
    console.say(f"database  {console.engine.url}")
    console.say(f"lifts     {len(lifts)}")
    console.say(f"ropes     {len(ropes)}")
    if lifts:
        console.say("codes     " + " ".join(str(one) for one in lifts))


def run() -> None:  # pragma: no cover - the console script entry
    sys.exit(cli())
