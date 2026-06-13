"""A stocked database for the command tests, built once and then copied.

Every command test wants the same thing: a file on disk with the schema in it and
the three machines on the register. Building that costs about a third of a second
between creating the tables and writing eighty four carriers and seven towers, and
the command tests were paying it a hundred and something times.

So it gets built once into a template and copied after that. A copy of a small
sqlite file is a filesystem operation and costs nothing measurable, and the tests
still get their own file that they can write to and throw away. Which is the point
of the fixture: isolation, not construction.

Keyed on what the caller asked to be put in it, because a test that only wants
John Paul should not have to pay for the other two, and because a template with
the wrong machines in it is worse than no template at all.
"""

from __future__ import annotations

import shutil
import tempfile
from collections.abc import Callable
from pathlib import Path

from hill import john_paul, strawberry, wildcat
from sheaveline.persistence.base import build_engine, create_schema
from sheaveline.persistence.uow import UnitOfWork
from sheaveline.plant.lift import Lift

WHOLE_HILL: tuple[Callable[[], Lift], ...] = (john_paul, strawberry, wildcat)

_TEMPLATES: dict[str, Path] = {}
_HOLDING = Path(tempfile.mkdtemp(prefix="sheaveline-templates-"))


def template(name: str, fill: Callable[[UnitOfWork], None]) -> Path:
    """A database with that filling in it, built the first time it is asked for."""
    held = _TEMPLATES.get(name)
    if held is not None:
        return held
    made = _HOLDING / f"{name}.sqlite3"
    engine = build_engine(f"sqlite+pysqlite:///{made}")
    create_schema(engine)
    with UnitOfWork.on(engine) as work:
        fill(work)
        work.commit()
    engine.dispose()
    _TEMPLATES[name] = made
    return made


def copied(tmp_path: Path, name: str, fill: Callable[[UnitOfWork], None]) -> str:
    """A url for a private copy, which the test may write to and throw away."""
    tmp_path.mkdir(parents=True, exist_ok=True)
    taken = tmp_path / "office.sqlite3"
    shutil.copyfile(template(name, fill), taken)
    return f"sqlite+pysqlite:///{taken}"


def stocked(tmp_path: Path, *makers: Callable[[], Lift]) -> str:
    """A database with those machines already on the register."""
    wanted = makers or WHOLE_HILL

    def fill(work: UnitOfWork) -> None:
        for maker in wanted:
            work.lifts.add(maker())

    return copied(tmp_path, "-".join(one.__name__ for one in wanted), fill)


def empty(tmp_path: Path) -> str:
    """A database with the tables in it and nothing on the register."""
    return copied(tmp_path, "empty", lambda work: None)
