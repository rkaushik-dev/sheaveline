from datetime import date, datetime, timedelta
from decimal import Decimal

import pytest

from hill import JOHN_PAUL, STRAWBERRY, john_paul, strawberry, the_season
from sheaveline.measures.length import Length
from sheaveline.measures.load import Load
from sheaveline.measures.temperature import Temperature
from sheaveline.measures.wind import Wind
from sheaveline.ops.logbook import DayLog
from sheaveline.ops.meters import MeterReading
from sheaveline.ops.preop import STANDARD_SHEET, CheckOutcome, CheckResult, PreOpSheet
from sheaveline.ops.stop import StopEvent, StopKind
from sheaveline.persistence.base import build_engine, create_schema
from sheaveline.persistence.uow import UnitOfWork
from sheaveline.reports.gather import assess, machine_line, morning, season_summary
from sheaveline.schedule.hours import OperatingWindow, TimeOfDay
from sheaveline.upkeep.library import codes_for
from sheaveline.upkeep.record import InspectionRecord
from sheaveline.upkeep.status import ServiceStatus
from sheaveline.weather.observation import Observation, Station

ON = date(2025, 1, 15)
WINDOW = OperatingWindow.parse("09:00", "16:00", "16:30")
SUMMIT = Station("summit", Length.from_feet(7981))


@pytest.fixture
def engine():
    made = build_engine()
    create_schema(made)
    with UnitOfWork.on(made) as work:
        work.lifts.add(john_paul())
        work.lifts.add(strawberry())
        work.commit()
    yield made
    made.dispose()


@pytest.fixture
def work(engine):
    with UnitOfWork.on(engine) as opened:
        yield opened


def sheet(machine, day=ON, failing=()):
    return PreOpSheet.of(
        lift=machine,
        day=day,
        results=[
            CheckResult(
                item=item,
                outcome=CheckOutcome.FAIL if item.code in failing else CheckOutcome.PASS,
                by="R Skaggs",
                note="dry" if item.code in failing else "",
            )
            for item in STANDARD_SHEET
            if item.applies_to(machine)
        ],
        mechanic="R Skaggs",
        operator="D Chin",
    )


def logged(work, machine, day=ON, failing=(), opened=True):
    log = DayLog.start(machine.code, day, WINDOW, sheet=sheet(machine, day, failing))
    if opened and not failing:
        log = log.open_at(TimeOfDay.parse("09:00"), "D Chin")
    work.day_logs.add(log)
    return log


def up_to_date(work, machine, on=ON):
    """Every job on the ladder signed off yesterday, so the ladder is not the story."""
    for code in codes_for(machine.lift_class):
        work.inspections.add(
            InspectionRecord.of(
                machine.code,
                code,
                on - timedelta(days=1),
                "R Skaggs",
                hours=Decimal("40000.0"),
                cycles=1_400_000,
            )
        )
    work.day_logs.read_meter(
        MeterReading(machine.code, on, Decimal("40000.5"), 1_400_100, "R Skaggs")
    )
    return machine


def blowing(work, gust="ENE 30G60"):
    work.weather.add_station(SUMMIT)
    work.weather.record(
        Observation(
            station=SUMMIT,
            taken=datetime.combine(ON, datetime.min.time()).replace(hour=6),
            wind=Wind.parse(gust),
            temperature=Temperature.from_degrees_f(8),
        )
    )


def test_a_machine_nobody_has_touched_reads_as_not_walked(work):
    line = machine_line(work, john_paul(), ON)
    assert not line.sheet_walked
    assert not line.opening()
    assert "morning walk not signed" in line.reasons()


def test_a_walked_sheet_opens_the_machine(work):
    up_to_date(work, john_paul())
    logged(work, john_paul())
    line = machine_line(work, john_paul(), ON)
    assert line.sheet_walked
    assert line.opening()
    assert line.verdict() == "opening"


def test_a_failed_critical_check_keeps_it_shut(work):
    logged(work, john_paul(), failing={"B1"})
    line = machine_line(work, john_paul(), ON)
    assert not line.opening()
    assert any("B1" in one for one in line.reasons())


def test_a_wind_reading_over_the_limit_holds_the_machine(work):
    up_to_date(work, john_paul())
    logged(work, john_paul())
    blowing(work)
    line = machine_line(work, john_paul(), ON)
    assert line.holds
    assert line.verdict() == "held"


def test_a_stale_reading_is_not_used_to_hold_anything(work):
    logged(work, john_paul())
    work.weather.add_station(SUMMIT)
    work.weather.record(
        Observation(
            station=SUMMIT,
            taken=datetime(2024, 12, 1, 6, 0),
            wind=Wind.parse("ENE 30G60"),
            temperature=Temperature.from_degrees_f(8),
        )
    )
    assert machine_line(work, john_paul(), ON).holds == ()


def test_a_hill_with_no_anemometer_is_a_normal_hill(work):
    logged(work, john_paul())
    assert machine_line(work, john_paul(), ON).holds == ()


def test_the_line_is_only_walked_when_a_tension_is_given(work):
    lift = john_paul()
    assert assess(work, lift, ON).geometry is None
    assert assess(work, lift, ON, Load.from_pounds(34500)).geometry is not None


def test_the_assessment_reads_the_ladder_off_what_was_recorded(work):
    reading = assess(work, john_paul(), ON)
    assert reading.status() is not ServiceStatus.IN_SERVICE
    assert reading.schedule.never_done()


def test_the_morning_page_covers_every_machine_on_the_register(work):
    logged(work, john_paul())
    logged(work, strawberry())
    page = morning(work, ON, "Ogden Peak", "eight inches overnight")
    assert [one.lift for one in page.machines] == [JOHN_PAUL, STRAWBERRY]
    assert "eight inches overnight" in page.render().text()


def test_the_season_adds_up_the_days_that_were_written_up(work):
    season = the_season()
    for offset in range(3):
        day = season.opens + timedelta(days=offset)
        logged(work, john_paul(), day=day)
    summary = season_summary(work, season)
    assert summary.days_open(JOHN_PAUL) == 3
    assert summary.scheduled(JOHN_PAUL).hours == 22.5


def test_a_day_outside_the_season_is_not_counted(work):
    season = the_season()
    logged(work, john_paul(), day=season.opens)
    logged(work, john_paul(), day=season.closes + timedelta(days=30))
    assert season_summary(work, season).days_open(JOHN_PAUL) == 1


def test_the_stops_on_a_day_come_through_to_the_season_arithmetic(work):
    season = the_season()
    day = season.opens
    logged(work, john_paul(), day=day)
    work.day_logs.stop(
        StopEvent(
            lift=JOHN_PAUL,
            kind=StopKind.GATE,
            stopped=datetime.combine(day, datetime.min.time()).replace(hour=11),
            stopped_by="D Chin",
            restarted=datetime.combine(day, datetime.min.time()).replace(hour=11, minute=30),
            restarted_by="D Chin",
            line_checked=True,
        )
    )
    summary = season_summary(work, season)
    assert summary.downtime(JOHN_PAUL).minutes == 30
    assert summary.chargeable(JOHN_PAUL).minutes == 30
    assert summary.stops_by_kind(JOHN_PAUL) == {StopKind.GATE: 1}


def test_a_power_cut_is_downtime_but_not_the_machines_fault(work):
    season = the_season()
    day = season.opens
    logged(work, john_paul(), day=day)
    work.day_logs.stop(
        StopEvent(
            lift=JOHN_PAUL,
            kind=StopKind.POWER,
            stopped=datetime.combine(day, datetime.min.time()).replace(hour=11),
            stopped_by="D Chin",
            restarted=datetime.combine(day, datetime.min.time()).replace(hour=12),
            restarted_by="D Chin",
            line_checked=True,
        )
    )
    summary = season_summary(work, season)
    assert summary.downtime(JOHN_PAUL).minutes == 60
    assert summary.chargeable(JOHN_PAUL).is_zero()
    assert summary.availability(JOHN_PAUL) == 1
