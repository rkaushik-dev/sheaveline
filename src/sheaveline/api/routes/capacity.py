"""Capacity and line geometry, worked out against a machine on file.

Every route here is a question with conditions attached, and the conditions are
query parameters because that is what they are: what if we slow it to six
hundred, what if we take twenty chairs off, what does the line look like with
eight feet of snow on it. The department asks these on the telephone and the
answer has to come back while the person is still holding.

Nothing is stored. Two people can ask different what ifs about the same machine
at the same moment and neither sees the other's.
"""

from __future__ import annotations

from decimal import Decimal

from fastapi import APIRouter, Query
from pydantic import BaseModel

from sheaveline.api.deps import Reader
from sheaveline.api.lookups import Look, Lookup
from sheaveline.api.schemas.measures import (
    DurationText,
    LengthFeet,
    LoadText,
    SpeedText,
)
from sheaveline.api.security import ApiKey
from sheaveline.capacity.spacing import MINIMUM_INTERVAL, LineSpacing
from sheaveline.capacity.uphill import UphillCapacity
from sheaveline.line.catenary import SagModel, line_weight
from sheaveline.line.geometry import LineGeometry
from sheaveline.measures.length import Length
from sheaveline.measures.load import Load
from sheaveline.measures.speed import Speed

router = APIRouter(prefix="/lifts/{code}", tags=["capacity"])


class SpacingOut(BaseModel):
    carriers: int
    speed: SpeedText
    spacing: LengthFeet
    interval: DurationText
    class_floor: DurationText
    meets_the_floor: bool
    shortfall: DurationText
    most_carriers: int


class CapacityOut(BaseModel):
    carriers: int
    speed: SpeedText
    occupancy: str
    carriers_per_hour: int
    seats_per_hour: int
    persons_per_hour: int
    vertical_feet_per_hour: int


class FindingOut(BaseModel):
    where: str
    severity: str
    detail: str


class GeometryOut(BaseModel):
    verdict: str
    may_run: bool
    snow: LengthFeet
    tension: LoadText
    worst_clearance: LengthFeet
    worst_margin: LengthFeet
    worst_station: LengthFeet
    findings: list[FindingOut]


@router.get("/spacing", response_model=SpacingOut)
def read_spacing(
    code: str,
    carriers: int | None = Query(default=None, ge=1),
    speed: str | None = Query(default=None),
    look: Lookup = Look,
    key: ApiKey = Reader,
) -> SpacingOut:
    lift = look.lift(code)
    line = LineSpacing.of(lift, None if speed is None else Speed.parse(speed))
    if carriers is not None:
        line = line.with_carriers(carriers)
    return SpacingOut(
        carriers=line.carriers,
        speed=line.speed,
        spacing=line.spacing(),
        interval=line.interval(),
        class_floor=MINIMUM_INTERVAL[lift.lift_class],
        meets_the_floor=line.meets(lift.lift_class),
        shortfall=line.shortfall(lift.lift_class),
        most_carriers=line.most_carriers_for(lift.lift_class),
    )


@router.get("/capacity", response_model=CapacityOut)
def read_capacity(
    code: str,
    carriers: int | None = Query(default=None, ge=1),
    speed: str | None = Query(default=None),
    occupancy: str = Query(default="0.85"),
    look: Lookup = Look,
    key: ApiKey = Reader,
) -> CapacityOut:
    lift = look.lift(code)
    line = LineSpacing.of(lift, None if speed is None else Speed.parse(speed))
    if carriers is not None:
        line = line.with_carriers(carriers)
    figures = UphillCapacity(line, lift.carriers.kind.seats, Decimal(occupancy))
    return CapacityOut(
        carriers=line.carriers,
        speed=line.speed,
        occupancy=occupancy,
        carriers_per_hour=figures.carriers_per_hour(),
        seats_per_hour=figures.seats_per_hour(),
        persons_per_hour=figures.persons_per_hour(),
        vertical_feet_per_hour=figures.vertical_feet_per_hour(lift.vertical_rise()),
    )


@router.get("/geometry", response_model=GeometryOut)
def read_geometry(
    code: str,
    snow: str = Query(default="0 ft"),
    tension: str = Query(default="34500 lb"),
    look: Lookup = Look,
    key: ApiKey = Reader,
) -> GeometryOut:
    lift = look.lift(code)
    line = LineSpacing.of(lift)
    weight = line_weight(
        lift.rope.spec.weight,
        lift.carriers.on_line()[0].gross(),
        line.spacing(),
    )
    sag = SagModel(weight, Load.parse(tension))
    walk = LineGeometry(lift, sag, Length.parse(snow))
    worst = walk.worst_clearance()
    return GeometryOut(
        verdict=walk.verdict().value,
        may_run=walk.verdict().may_run,
        snow=walk.snow,
        tension=sag.tension,
        worst_clearance=worst.available,
        worst_margin=worst.margin,
        worst_station=worst.station,
        findings=[
            FindingOut(where=one.where, severity=one.severity.value, detail=one.detail)
            for one in walk.findings()
        ],
    )
