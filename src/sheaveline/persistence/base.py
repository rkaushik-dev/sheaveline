"""The database floor: one base, one naming convention, one way to get a session.

The naming convention is not decoration. Alembic writes migrations that drop
constraints by name, and sqlite does not name them the same way postgres does,
so without a convention a migration that works on the office server fails on the
laptop the mechanic carries. It goes in once, here, before any table exists.

Sessions are handed out through a scope rather than constructed by callers. Every
write in this system is a small unit of work with an obvious boundary, and the
one thing that must never happen is a half written day log, so the scope commits
or rolls back and there is no third option.
"""

from __future__ import annotations

from collections.abc import Iterator
from contextlib import contextmanager
from typing import Final

from sqlalchemy import Engine, MetaData, create_engine, event
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker

from sheaveline.errors import SheavelineError

NAMING_CONVENTION: Final[dict[str, str]] = {
    "ix": "ix_%(column_0_label)s",
    "uq": "uq_%(table_name)s_%(column_0_name)s",
    "ck": "ck_%(table_name)s_%(constraint_name)s",
    "fk": "fk_%(table_name)s_%(column_0_name)s_%(referred_table_name)s",
    "pk": "pk_%(table_name)s",
}

MEMORY_URL: Final = "sqlite+pysqlite:///:memory:"


class StorageError(SheavelineError):
    """Something went wrong between the domain and the database."""


class Base(DeclarativeBase):
    """Every table in this system hangs off this one."""

    metadata = MetaData(naming_convention=NAMING_CONVENTION)


def build_engine(url: str = MEMORY_URL, echo: bool = False) -> Engine:
    """An engine with sqlite told to enforce the foreign keys it normally ignores."""
    engine = create_engine(url, echo=echo, future=True)
    if engine.dialect.name == "sqlite":
        _enforce_sqlite_foreign_keys(engine)
    return engine


def _enforce_sqlite_foreign_keys(engine: Engine) -> None:
    @event.listens_for(engine, "connect")
    def _set_pragma(connection: object, _record: object) -> None:
        cursor = connection.cursor()  # type: ignore[attr-defined]
        cursor.execute("PRAGMA foreign_keys=ON")
        cursor.close()


def build_sessions(engine: Engine) -> sessionmaker[Session]:
    return sessionmaker(bind=engine, expire_on_commit=False, future=True)


def create_schema(engine: Engine) -> None:
    """Build every table the package knows about. Tests and the first run use this."""
    import sheaveline.persistence.models  # noqa: F401  the models register themselves

    Base.metadata.create_all(engine)


def drop_schema(engine: Engine) -> None:
    Base.metadata.drop_all(engine)


@contextmanager
def session_scope(sessions: sessionmaker[Session]) -> Iterator[Session]:
    """Commit on the way out, roll back on anything raised, close either way."""
    session = sessions()
    try:
        yield session
        session.commit()
    except Exception:
        session.rollback()
        raise
    finally:
        session.close()


def table_names() -> tuple[str, ...]:
    import sheaveline.persistence.models  # noqa: F401

    return tuple(sorted(Base.metadata.tables))
