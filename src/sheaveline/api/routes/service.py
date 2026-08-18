"""What a load balancer asks, and what somebody on the telephone asks.

Two endpoints that look the same and are not. Health says the process is up and
answering, which is what a container orchestrator polls every few seconds and
what must never touch the database, because a health check that opens a
connection is a health check that takes the service down when the disk is slow.

Readiness says the database is there and has the tables in it, which is the thing
that is actually wrong when the office rings up. It costs a query, so it is not
on the fast path and it says plainly which check failed.

Neither needs a key. A service that will not say whether it is alive without
being authenticated is a service nobody can monitor.
"""

from __future__ import annotations

from datetime import date
from typing import Final

from fastapi import APIRouter, Request, status
from fastapi.responses import JSONResponse
from pydantic import BaseModel
from sqlalchemy import inspect

from sheaveline import __version__
from sheaveline.api.deps import settings_of_request
from sheaveline.persistence.base import table_names
from sheaveline.persistence.uow import UnitOfWork

router = APIRouter(tags=["service"])

SERVICE: Final = "sheaveline"


class HealthOut(BaseModel):
    """The cheap one. Nothing here reads a row."""

    service: str
    version: str
    resort: str


class ReadyOut(BaseModel):
    """The one that costs a query and says what it found."""

    ready: bool
    database: str
    tables: int
    missing: list[str]
    lifts: int
    today: date
    detail: str


@router.get("/health", response_model=HealthOut)
def health(request: Request) -> HealthOut:
    """Up and answering. Deliberately does not touch the database."""
    settings = settings_of_request(request)
    return HealthOut(service=SERVICE, version=__version__, resort=settings.resort)


@router.get("/ready", response_model=ReadyOut, responses={503: {"model": ReadyOut}})
def ready(request: Request) -> JSONResponse:
    """The database is there, has the tables in it, and answers a count."""
    settings = settings_of_request(request)
    url = str(settings.engine.url)
    try:
        built = set(inspect(settings.engine).get_table_names())
    except Exception as broken:  # noqa: BLE001  any driver failure is the same answer
        return _not_ready(url, [], 0, settings.today(), f"the database will not open: {broken}")
    missing = sorted(set(table_names()) - built)
    if missing:
        return _not_ready(
            url, missing, 0, settings.today(), f"{len(missing)} tables are not built yet"
        )
    with UnitOfWork.on(settings.engine) as work:
        machines = len(work.lifts.codes())
    body = ReadyOut(
        ready=True,
        database=url,
        tables=len(built),
        missing=[],
        lifts=machines,
        today=settings.today(),
        detail="ready",
    )
    return JSONResponse(status_code=status.HTTP_200_OK, content=body.model_dump(mode="json"))


def _not_ready(url: str, missing: list[str], lifts: int, today: date, detail: str) -> JSONResponse:
    body = ReadyOut(
        ready=False,
        database=url,
        tables=0,
        missing=missing,
        lifts=lifts,
        today=today,
        detail=detail,
    )
    return JSONResponse(
        status_code=status.HTTP_503_SERVICE_UNAVAILABLE, content=body.model_dump(mode="json")
    )
