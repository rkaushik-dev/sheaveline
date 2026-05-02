"""Stations, readings and the hold decision that comes off them.

The hold route picks its own station. The caller says which machine, and the
station nearest the top terminal is the one that answers, because that is where
the wind is and asking the base what the summit is doing has put people on a
chairlift in forty knots more than once.

It also refuses a stale reading rather than answering from it. An operator who
gets told there is no fresh reading goes and looks; an operator who gets told
the machine is clear on a reading from forty minutes ago does not.
"""

from __future__ import annotations

from datetime import date, datetime

from fastapi import APIRouter, Query, status

from sheaveline.api.deps import Manager, Operator, Reader, Today, Work
from sheaveline.api.lookups import Look, Lookup
from sheaveline.api.schemas.weather import (
    HoldOut,
    ObservationIn,
    ObservationOut,
    StationIn,
    StationOut,
)
from sheaveline.api.security import ApiKey
from sheaveline.measures.duration import Duration
from sheaveline.persistence.uow import UnitOfWork
from sheaveline.weather.hold import WeatherHold

router = APIRouter(tags=["weather"])


@router.post("/weather/stations", response_model=StationOut, status_code=status.HTTP_201_CREATED)
def add_station(body: StationIn, work: UnitOfWork = Work, key: ApiKey = Manager) -> StationOut:
    station = body.to_domain()
    work.weather.add_station(station)
    work.commit()
    return StationOut.of(station)


@router.get("/weather/stations", response_model=list[StationOut])
def list_stations(work: UnitOfWork = Work, key: ApiKey = Reader) -> list[StationOut]:
    return [StationOut.of(one) for one in work.weather.stations()]


@router.post(
    "/weather/observations",
    response_model=ObservationOut,
    status_code=status.HTTP_201_CREATED,
)
def record_observation(
    body: ObservationIn, work: UnitOfWork = Work, look: Lookup = Look, key: ApiKey = Operator
) -> ObservationOut:
    station = look.station(body.station)
    seen = body.to_domain(station)
    work.weather.record(seen)
    work.commit()
    return ObservationOut.of(seen)


@router.get("/weather/observations", response_model=list[ObservationOut])
def list_observations(
    station: str | None = Query(default=None),
    since: datetime | None = Query(default=None),
    work: UnitOfWork = Work,
    look: Lookup = Look,
    key: ApiKey = Reader,
) -> list[ObservationOut]:
    where = None
    if station is not None:
        where = look.station(station)
    moment = since or datetime.min
    return [ObservationOut.of(one) for one in work.weather.since(moment, where)]


@router.get("/lifts/{code}/hold", response_model=HoldOut)
def read_hold(
    code: str,
    now: datetime | None = Query(default=None),
    stale_after_minutes: int = Query(default=20, ge=1),
    work: UnitOfWork = Work,
    look: Lookup = Look,
    key: ApiKey = Reader,
    today: date = Today,
) -> HoldOut:
    lift = look.lift(code)
    moment = now or datetime.combine(today, datetime.min.time())
    station = work.weather.nearest_to(lift.top.rope_elevation)
    seen = work.weather.latest_fresh(station, moment, Duration.from_minutes(stale_after_minutes))
    return HoldOut.of(WeatherHold(lift, seen), moment)
