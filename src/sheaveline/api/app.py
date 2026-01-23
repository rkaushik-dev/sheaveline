"""The application factory.

Built as a factory rather than a module level app because the tests want a
fresh database behind a fresh application on every run, and because the office
server and the laptop pass different engines. Nothing here reaches for a global.
"""

from __future__ import annotations

from collections.abc import Callable, Iterator
from datetime import date
from typing import Final

from fastapi import FastAPI
from sqlalchemy import Engine

from sheaveline.api.errors import install_handlers
from sheaveline.api.security import KeyRing
from sheaveline.persistence.base import build_engine, create_schema
from sheaveline.persistence.uow import UnitOfWork

TITLE: Final = "sheaveline"
VERSION: Final = "0.1.0"
SUMMARY: Final = "Lift department records for a passenger ropeway operation"


class Settings:
    """What one running copy of the application needs to know."""

    def __init__(
        self,
        engine: Engine,
        resort: str = "Ogden Peak",
        keys: KeyRing | None = None,
        today: Callable[[], date] | None = None,
    ) -> None:
        self.engine = engine
        self.resort = resort
        self.keys = keys if keys is not None else KeyRing.of()
        self._today = today or date.today

    def today(self) -> date:
        """The day the running copy thinks it is, so a test can say otherwise."""
        return self._today()

    @classmethod
    def in_memory(
        cls,
        resort: str = "Ogden Peak",
        keys: KeyRing | None = None,
        today: Callable[[], date] | None = None,
    ) -> Settings:
        engine = build_engine()
        create_schema(engine)
        return cls(engine, resort, keys, today)


def build_app(settings: Settings) -> FastAPI:
    app = FastAPI(title=TITLE, version=VERSION, summary=SUMMARY)
    app.state.settings = settings
    install_handlers(app)

    from sheaveline.api.routes import lifts, ropes

    app.include_router(lifts.router)
    app.include_router(ropes.router)

    @app.get("/health", tags=["service"])
    def health() -> dict[str, str]:
        return {"service": TITLE, "version": VERSION, "resort": settings.resort}

    return app


def settings_of(app: FastAPI) -> Settings:
    settings = getattr(app.state, "settings", None)
    if not isinstance(settings, Settings):
        raise RuntimeError("this application was built without settings")
    return settings


def unit_of_work(app: FastAPI) -> Iterator[UnitOfWork]:
    """One unit of work a request, closed on the way out whatever happened."""
    work = UnitOfWork.on(settings_of(app).engine)
    with work:
        yield work
