"""The day book over http, which is what the terminal tablets write into.

Stopping and restarting are the two calls that get made in anger, so both are
short and both refuse rather than guess. A restart names the stop by the moment
it was called, not by an id, because the operator has the time written on the
clipboard in front of them and does not have an id.

Nothing here invents a day. A stop can be posted against a machine that has no
log written up, because that is a real Tuesday, and the day log route will happily
say there is no log for that day rather than making one up to hang the stop on.
"""

from __future__ import annotations

from datetime import date

from fastapi import APIRouter, status

from sheaveline.api.deps import Mechanic, Operator, Reader, Work
from sheaveline.api.lookups import Look, Lookup
from sheaveline.api.paging import Page, Paged, Paging
from sheaveline.api.schemas.ops import (
    DayLogOut,
    MeterOut,
    RestartIn,
    StopIn,
    StopLogOut,
    StopOut,
)
from sheaveline.api.security import ApiKey
from sheaveline.ops.stop import StopEvent
from sheaveline.persistence.uow import UnitOfWork

router = APIRouter(tags=["operations"])


@router.get("/lifts/{code}/days/{day}", response_model=DayLogOut)
def read_day(
    code: str,
    day: date,
    work: UnitOfWork = Work,
    look: Lookup = Look,
    key: ApiKey = Reader,
) -> DayLogOut:
    return DayLogOut.of(work.day_logs.get(look.lift_code(code), day))


@router.get("/lifts/{code}/days", response_model=Paged[str])
def list_days(
    code: str,
    work: UnitOfWork = Work,
    look: Lookup = Look,
    paging: Paging = Page,
    key: ApiKey = Reader,
) -> Paged[str]:
    """Paged, because a machine that has run for ten seasons has fifteen hundred."""
    days = work.day_logs.days_logged(look.lift_code(code))
    return Paged.of([one.isoformat() for one in days], paging)


@router.get("/lifts/{code}/days/{day}/stops", response_model=StopLogOut)
def read_stops(
    code: str,
    day: date,
    work: UnitOfWork = Work,
    look: Lookup = Look,
    key: ApiKey = Reader,
) -> StopLogOut:
    """Every stop that touched that day, which is not every stop that began on it."""
    return StopLogOut.of(work.day_logs.stops_on(look.lift_code(code), day))


@router.post(
    "/lifts/{code}/stops",
    response_model=StopOut,
    status_code=status.HTTP_201_CREATED,
)
def call_stop(
    code: str,
    body: StopIn,
    work: UnitOfWork = Work,
    look: Lookup = Look,
    key: ApiKey = Operator,
) -> StopOut:
    """Stop the machine. Everything that follows from the kind is worked out here."""
    event = StopEvent(
        lift=look.lift_code(code),
        kind=body.kind,
        stopped=body.stopped,
        stopped_by=body.stopped_by,
        note=body.note,
    )
    work.day_logs.stop(event)
    work.commit()
    return StopOut.of(event)


@router.post("/lifts/{code}/stops/restart", response_model=StopOut)
def restart(
    code: str,
    body: RestartIn,
    work: UnitOfWork = Work,
    look: Lookup = Look,
    key: ApiKey = Operator,
) -> StopOut:
    """Run it again. The domain decides whether that is allowed, not the route."""
    event = work.day_logs.restart(
        look.lift_code(code),
        body.stopped,
        body.restarted,
        body.restarted_by,
        line_checked=body.line_checked,
    )
    work.commit()
    return StopOut.of(event)


@router.get("/stops/open", response_model=list[StopOut])
def open_stops(
    lift: str | None = None,
    work: UnitOfWork = Work,
    look: Lookup = Look,
    key: ApiKey = Reader,
) -> list[StopOut]:
    """Everything on the hill that is stopped and has not run again."""
    code = None if lift is None else look.lift_code(lift)
    return [StopOut.of(one) for one in work.day_logs.open_stops(code)]


@router.get("/lifts/{code}/meters", response_model=Paged[MeterOut])
def read_meters(
    code: str,
    work: UnitOfWork = Work,
    look: Lookup = Look,
    paging: Paging = Page,
    key: ApiKey = Mechanic,
) -> Paged[MeterOut]:
    """The hour meter and cycle counter as they were read, oldest first."""
    history = work.day_logs.meters(look.lift_code(code))
    readings = [] if history is None else list(history.readings)
    return Paged.of([MeterOut.of(one) for one in readings], paging)
