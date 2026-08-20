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
from dataclasses import replace
from datetime import date, datetime, time
from functools import wraps
from typing import Any, TypeVar

import click
from sqlalchemy import Engine, inspect
from sqlalchemy.exc import SQLAlchemyError

from sheaveline import __version__
from sheaveline.config import DATABASE, JOURNAL, KEYS, Configuration
from sheaveline.errors import SheavelineError
from sheaveline.journal import Journal, Level
from sheaveline.persistence.base import build_engine, create_schema, table_names
from sheaveline.persistence.uow import UnitOfWork

Result = TypeVar("Result")


class Console:
    """What every command is handed: a database, a clock and a way to write.

    It used to carry the database url as well, and the journal level, and its own
    two constants for where the records live by default. All four of those are
    settings and there is a module that reads settings, so the console holds the
    configuration it was built from and stops keeping a second copy of half of it.
    """

    def __init__(
        self,
        engine: Engine,
        now: Callable[[], datetime] | None = None,
        today: Callable[[], date] | None = None,
        journal: Journal | None = None,
        configuration: Configuration | None = None,
    ) -> None:
        self.engine = engine
        self._now = now
        self._today = today
        self.journal = journal if journal is not None else Journal.silent()
        self.configuration = configuration if configuration is not None else Configuration()

    @classmethod
    def of(cls, read: Configuration, journal: Journal | None = None) -> Console:
        """The one place a running copy is built from what it was told."""
        return cls(
            build_engine(read.database),
            journal=journal if journal is not None else _journal_for(read),
            configuration=read,
        )

    @property
    def resort(self) -> str:
        return self.configuration.resort

    def now(self) -> datetime:
        """One clock, because a command that reads both wants them to agree."""
        if self._now is not None:
            return self._now()
        if self._today is not None:
            return datetime.combine(self._today(), time.min)
        return datetime.now()

    def today(self) -> date:
        return self.now().date()

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
            _journal_of(args).warn(
                "refused", command=command.__name__, error=type(refusal).__name__
            )
            click.echo(f"{type(refusal).__name__}: {refusal}", err=True)
            raise SystemExit(1) from refusal
        except SQLAlchemyError as broken:
            _journal_of(args).error(
                "database", command=command.__name__, error=type(broken).__name__
            )
            click.echo(f"the database will not answer: {_first_line(broken)}", err=True)
            raise SystemExit(2) from broken

    return run


def _journal_of(args: tuple[Any, ...]) -> Journal:
    """The console is the first argument of every command, or nothing is."""
    first = args[0] if args else None
    return first.journal if isinstance(first, Console) else Journal.silent()


def _first_line(error: Exception) -> str:
    """The driver's own sentence, without the stack it usually arrives with."""
    return str(error).strip().splitlines()[0]


def _journal_for(read: Configuration) -> Journal:
    """Silent unless it was asked for, because reports go to standard output."""
    if read.journal is Level.OFF:
        return Journal.silent()
    return Journal.to_stderr(read.journal)


@click.group()
@click.option(
    "--database",
    "database",
    default=None,
    metavar="URL",
    help=f"Where the records live. ${DATABASE} if you leave it off.",
)
@click.option(
    "--journal",
    "level",
    type=click.Choice([one.name.lower() for one in Level]),
    default=None,
    help=f"What the software writes about itself, on standard error. ${JOURNAL} otherwise.",
)
@click.version_option(version=__version__, prog_name="sheaveline")
@click.pass_context
@handles_refusals
def cli(context: click.Context, database: str | None, level: str | None) -> None:
    """Lift department records for a passenger ropeway operation.

    Everything is read from the environment and the flags override it, so the box
    in the equipment room is configured once and a person at a keyboard can still
    point one command somewhere else.

    The journal is off unless it is asked for. These commands print reports to
    standard output and a stray line in the middle of an eighty column page is a
    page nobody can read.

    This one is wrapped like every other command, because a port the environment
    cannot mean is a refusal and deserves a sentence rather than a traceback.
    """
    read = Configuration.from_environment()
    if database is not None:
        read = replace(read, database=database)
    read = replace(read, journal=Level.OFF if level is None else Level.named(level))
    context.obj = Console.of(read)
    context.obj.journal.debug("started", database=read.database)


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


@cli.command()
@click.pass_obj
@handles_refusals
def settings(console: Console) -> None:
    """Print what the environment says, so the box in the equipment room can be checked.

    Nothing printed here is a secret. The key file is named and not opened, and
    the database url is the one that was going to be used anyway.
    """
    read = console.configuration
    for name, value in read.described():
        console.say(f"{name:26} {value}")
    console.say("")
    console.say(str(read))
    if not read.serves_keys():
        console.say(f"{KEYS} is not set, so the http surface would refuse every request")
