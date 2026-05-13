"""Reading the database into the shapes the reports want.

The reports deliberately refuse to look anything up, because a renderer that
goes and asks the database can only ever print today, and the manager who wants
to know why the summit quad was down on the eighth of February is asking about a
Tuesday two months gone.

So the fetching lives here instead, in one place, where it can be read against
the questions it is answering. Every function takes a unit of work and a day and
hands back something the report constructor already understands.

The one judgement in the file is what counts as the machine's status. It is not a
column anywhere. It falls out of four independent sources, and the assessment
object is the thing that knows how to weigh them, so this module's job is to put
the four in front of it and get out of the way.
"""

from __future__ import annotations

from datetime import date, datetime, time, timedelta
from typing import Final

from sheaveline.capacity.spacing import LineSpacing
from sheaveline.line.catenary import SagModel, line_weight
from sheaveline.line.geometry import GeometryVerdict, LineGeometry
from sheaveline.measures.length import Length
from sheaveline.measures.load import Load
from sheaveline.ops.stop import StopKind
from sheaveline.persistence.uow import UnitOfWork
from sheaveline.plant.identity import LiftCode
from sheaveline.plant.lift import Lift
from sheaveline.reports.morning import MachineLine, Morning
from sheaveline.reports.season import DayRecord, SeasonSummary
from sheaveline.ropework.verdict import assess_wear
from sheaveline.schedule.season import Season
from sheaveline.upkeep.record import RecordBook
from sheaveline.upkeep.schedule import UpkeepSchedule
from sheaveline.upkeep.status import ServiceAssessment
from sheaveline.weather.hold import Hold, WeatherHold

WARN_WITHIN_DAYS = 14
BARE_GROUND: Final = Length(0)


def assess(
    work: UnitOfWork, lift: Lift, on: date, tension: Load | None = None
) -> ServiceAssessment:
    """The sources a machine's fitness is read from, put in one place.

    The line walk is the one that has to be asked for. Clearance depends on how
    hard the rope is pulled and how much snow is under it, and neither of those
    is a column anywhere, so a caller that has not measured them gets a reading
    without a line verdict rather than a line verdict off a made up tension.
    """
    book = RecordBook.of(lift.code, work.inspections.for_lift(lift.code))
    schedule = UpkeepSchedule(
        lift=lift.code,
        lift_class=lift.lift_class,
        commissioned=lift.commissioned,
        book=book,
        meter=work.day_logs.meters(lift.code),
    )
    surveys = work.wear_surveys.for_rope(lift.rope)
    return ServiceAssessment(
        lift=lift.code,
        on=on,
        schedule=schedule,
        rope=assess_wear(surveys[-1]) if surveys else None,
        geometry=None if tension is None else _line_verdict(lift, tension),
    )


def _line_verdict(lift: Lift, tension: Load, snow: Length = BARE_GROUND) -> GeometryVerdict:
    """The line walked at the tension the caller says the rope is carrying."""
    spacing = LineSpacing.of(lift)
    weight = line_weight(
        lift.rope.spec.weight,
        lift.carriers.on_line()[0].gross(),
        spacing.spacing(),
    )
    return LineGeometry(lift, SagModel(weight, tension), snow).verdict()


def weather_holds(work: UnitOfWork, lift: Lift, on: date) -> tuple[Hold, ...]:
    """What the nearest station says about this machine right now."""
    try:
        station = work.weather.nearest_to(lift.top.rope_elevation)
        seen = work.weather.latest(station)
    except Exception:  # noqa: BLE001  a hill with no anemometer is a normal hill
        return ()
    if seen.taken.date() < on - timedelta(days=1):
        return ()
    return WeatherHold(lift, seen).holds()


def machine_line(
    work: UnitOfWork, lift: Lift, on: date, tension: Load | None = None
) -> MachineLine:
    assessment = assess(work, lift, on, tension)
    log = work.day_logs.find_log(lift.code, on)
    walked = False
    blockers: tuple[str, ...] = ()
    if log is not None:
        book = work.day_logs.get(lift.code, on)
        walked = book.sheet is not None and book.sheet.is_complete()
        blockers = book.blockers()
    return MachineLine(
        lift=lift.code,
        name=lift.name,
        status=assessment.status(),
        sheet_walked=walked,
        blockers=tuple(blockers) + tuple(str(one) for one in assessment.blocking()),
        holds=weather_holds(work, lift, on),
        orders=tuple(work.work_orders.open_orders(lift.code)),
        upkeep_due=tuple(str(one) for one in assessment.schedule.due_within(on, WARN_WITHIN_DAYS)),
    )


def morning(
    work: UnitOfWork,
    on: date,
    resort: str,
    snow: str = "",
    tension: Load | None = None,
) -> Morning:
    """The seven o'clock page, gathered off the register."""
    return Morning.of(
        day=on,
        resort=resort,
        snow=snow,
        machines=[machine_line(work, lift, on, tension) for lift in work.lifts.all()],
    )


def day_record(work: UnitOfWork, lift: LiftCode, on: date) -> DayRecord | None:
    """One machine on one day, as the season summary counts it."""
    log = work.day_logs.find_log(lift, on)
    if log is None:
        return None
    book = work.day_logs.get(lift, on)
    stops = work.day_logs.stops_on(lift, on)
    scheduled = book.scheduled_time()
    return DayRecord(
        lift=lift,
        day=on,
        opened=book.is_open() or book.opened is not None,
        scheduled=scheduled,
        running=scheduled - stops.downtime(),
        downtime=stops.downtime(),
        chargeable=stops.chargeable_downtime(),
        stops=tuple(one.kind for one in stops.stops),
    )


def season_summary(work: UnitOfWork, season: Season) -> SeasonSummary:
    """Every machine, every day it was written up, added up once."""
    records = []
    for code in work.lifts.codes():
        for day in work.day_logs.days_logged(code):
            if not season.covers(day):
                continue
            found = day_record(work, code, day)
            if found is not None:
                records.append(found)
    return SeasonSummary.of(season, records)


def stops_between(work: UnitOfWork, lift: LiftCode, start: date, end: date) -> dict[StopKind, int]:
    """How often each kind was called over a stretch, which the board asks about."""
    counted: dict[StopKind, int] = {}
    for one in work.day_logs.stops_on(lift, end).stops:
        counted[one.kind] = counted.get(one.kind, 0) + 1
    for day in work.day_logs.days_logged(lift):
        if not start <= day <= end:
            continue
        for one in work.day_logs.stops_on(lift, day).stops:
            counted[one.kind] = counted.get(one.kind, 0) + 1
    return counted


def opening_window(work: UnitOfWork, on: date) -> tuple[datetime, datetime]:
    """The earliest first load and the latest sweep across the hill."""
    starts: list[datetime] = []
    ends: list[datetime] = []
    for code in work.lifts.codes():
        log = work.day_logs.find_log(code, on)
        if log is None:
            continue
        starts.append(datetime.combine(on, log.first_load.to_time()))
        ends.append(datetime.combine(on, log.sweep_by.to_time()))
    if not starts:
        return (datetime.combine(on, time.min), datetime.combine(on, time.min))
    return (min(starts), max(ends))
