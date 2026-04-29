from datetime import date, datetime, timedelta
from decimal import Decimal

import pytest
from sqlalchemy.orm import Session

from hill import JOHN_PAUL, STRAWBERRY, john_paul, strawberry
from sheaveline.ops.logbook import DayLog, EntryKind
from sheaveline.ops.meters import MeterReading
from sheaveline.ops.preop import (
    STANDARD_SHEET,
    CheckOutcome,
    CheckResult,
    OperationsError,
    PreOpSheet,
)
from sheaveline.ops.stop import StopEvent, StopKind, StopLog
from sheaveline.persistence.base import StorageError, build_engine, create_schema
from sheaveline.persistence.repositories import DayLogRepository, LiftRepository
from sheaveline.schedule.hours import OperatingWindow, TimeOfDay
from sheaveline.weather.hold import HoldReason
from sheaveline.weather.holdlog import HoldRecord

DAY = date(2025, 1, 15)
WINDOW = OperatingWindow.parse("09:00", "16:00", "16:30")


@pytest.fixture
def session():
    engine = build_engine()
    create_schema(engine)
    with Session(engine) as opened:
        lifts = LiftRepository(opened)
        lifts.add(john_paul())
        lifts.add(strawberry())
        opened.commit()
        yield opened
    engine.dispose()


@pytest.fixture
def logs(session):
    return DayLogRepository(session)


def sheet(machine=None, day=DAY, failing=()):
    machine = machine or john_paul()
    results = [
        CheckResult(
            item=item,
            outcome=CheckOutcome.FAIL if item.code in failing else CheckOutcome.PASS,
            by="R Skaggs",
        )
        for item in STANDARD_SHEET
        if item.applies_to(machine)
    ]
    return PreOpSheet.of(machine, day, results, "R Skaggs", "D Chin")


def log(day=DAY, machine=None, with_sheet=True):
    machine = machine or john_paul()
    made = sheet(machine, day) if with_sheet else None
    started = DayLog.start(machine.code, day, WINDOW, sheet=made)
    return started.note(TimeOfDay.parse("08:40"), "line ridden, all clear", "R Skaggs")


def stop(at=datetime(2025, 1, 15, 11, 30), kind=StopKind.OPERATOR, lift=JOHN_PAUL, **rest):
    return StopEvent(lift=lift, kind=kind, stopped=at, stopped_by="D Chin", **rest)


def test_a_day_goes_in_and_comes_back_out(logs, session):
    logs.add(log())
    session.commit()
    found = logs.get(JOHN_PAUL, DAY)
    assert found.day == DAY
    assert found.window == WINDOW
    assert len(found.entries) == 1
    assert found.entries[0].kind is EntryKind.NOTE


def test_the_sheet_comes_back_with_the_day_and_knows_its_machine(logs, session):
    logs.add(log())
    session.commit()
    found = logs.get(JOHN_PAUL, DAY)
    assert found.sheet is not None
    assert found.sheet.mechanic == "R Skaggs"
    assert found.sheet.is_complete()
    assert found.may_open()


def test_a_day_with_no_sheet_comes_back_without_one(logs, session):
    logs.add(log(with_sheet=False))
    session.commit()
    found = logs.get(JOHN_PAUL, DAY)
    assert found.sheet is None
    assert not found.may_open()


def test_the_same_day_is_not_logged_twice(logs, session):
    logs.add(log())
    session.commit()
    with pytest.raises(StorageError):
        logs.add(log())


def test_a_day_nobody_wrote_up(logs):
    with pytest.raises(StorageError):
        logs.get(JOHN_PAUL, DAY)
    assert logs.find_log(JOHN_PAUL, DAY) is None


def test_the_days_a_machine_has_been_written_up(logs, session):
    for offset in range(3):
        logs.add(log(day=DAY + timedelta(days=offset)))
    logs.add(log(machine=strawberry()))
    session.commit()
    assert logs.days_logged(JOHN_PAUL) == [DAY, DAY + timedelta(days=1), DAY + timedelta(days=2)]
    assert logs.days_logged(STRAWBERRY) == [DAY]


def test_a_stretch_of_days_comes_back_in_order(logs, session):
    for offset in (2, 0, 1):
        logs.add(log(day=DAY + timedelta(days=offset)))
    session.commit()
    found = logs.between(JOHN_PAUL, DAY, DAY + timedelta(days=1))
    assert [one.day for one in found] == [DAY, DAY + timedelta(days=1)]


def test_rewriting_a_day_replaces_what_was_there(logs, session):
    logs.add(log())
    session.commit()
    signed = logs.get(JOHN_PAUL, DAY).note(TimeOfDay.parse("17:00"), "swept at 16:22", "D Chin")
    logs.replace(signed)
    session.commit()
    assert len(logs.get(JOHN_PAUL, DAY).entries) == 2


def test_rewriting_a_day_nobody_wrote_up(logs):
    with pytest.raises(StorageError):
        logs.replace(log())


def test_a_stop_goes_in_and_comes_back_against_the_day_it_touched(logs, session):
    logs.add(log())
    logs.stop(stop())
    session.commit()
    found = logs.get(JOHN_PAUL, DAY)
    assert found.stops is not None
    assert len(found.stops) == 1
    assert found.stops.stops[0].kind is StopKind.OPERATOR


def test_two_stops_cannot_start_at_the_same_moment(logs, session):
    logs.stop(stop())
    session.commit()
    with pytest.raises(StorageError):
        logs.stop(stop())


def test_restarting_writes_the_time_back_against_the_stop(logs, session):
    logs.stop(stop(kind=StopKind.DEROPEMENT, note="grip off at tower four, line walked"))
    session.commit()
    restarted = logs.restart(
        JOHN_PAUL,
        datetime(2025, 1, 15, 11, 30),
        datetime(2025, 1, 15, 13, 15),
        "R Skaggs",
        line_checked=True,
    )
    session.commit()
    assert restarted.restarted_by == "R Skaggs"
    assert restarted.line_checked
    assert logs.open_stops(JOHN_PAUL) == []


def test_restarting_a_stop_nobody_called(logs):
    with pytest.raises(StorageError):
        logs.restart(
            JOHN_PAUL, datetime(2025, 1, 15, 7, 0), datetime(2025, 1, 15, 8, 0), "R Skaggs"
        )


def test_a_stop_that_ran_overnight_belongs_to_both_days(logs, session):
    logs.stop(
        stop(
            at=datetime(2025, 1, 15, 16, 5),
            kind=StopKind.POWER,
            restarted=datetime(2025, 1, 16, 8, 40),
            restarted_by="R Skaggs",
            line_checked=True,
        )
    )
    session.commit()
    assert len(logs.stops_on(JOHN_PAUL, DAY)) == 1
    assert len(logs.stops_on(JOHN_PAUL, date(2025, 1, 16))) == 1
    assert len(logs.stops_on(JOHN_PAUL, date(2025, 1, 17))) == 0


def test_a_stop_still_open_belongs_to_every_day_since(logs, session):
    logs.stop(stop(at=datetime(2025, 1, 15, 16, 5), kind=StopKind.SAFETY_CIRCUIT))
    session.commit()
    assert len(logs.stops_on(JOHN_PAUL, date(2025, 1, 20))) == 1
    assert [one.lift for one in logs.open_stops()] == [JOHN_PAUL]


def test_one_machines_stops_do_not_land_in_anothers_log(logs, session):
    logs.stop(stop())
    logs.stop(stop(lift=STRAWBERRY, at=datetime(2025, 1, 15, 12)))
    session.commit()
    assert len(logs.stops_on(JOHN_PAUL, DAY)) == 1
    assert len(logs.stops_on(STRAWBERRY, DAY)) == 1


def test_the_stops_that_come_back_are_a_log_that_can_be_totalled(logs, session):
    logs.stop(
        stop(
            at=datetime(2025, 1, 15, 10, 0),
            kind=StopKind.WEATHER,
            restarted=datetime(2025, 1, 15, 10, 30),
            restarted_by="D Chin",
        )
    )
    session.commit()
    held = logs.stops_on(JOHN_PAUL, DAY)
    assert isinstance(held, StopLog)
    assert held.downtime().minutes == 30
    assert held.chargeable_downtime().is_zero()


def test_a_deropement_will_not_restart_until_somebody_has_walked_the_line(logs, session):
    logs.stop(stop(kind=StopKind.DEROPEMENT, note="grip off at tower four"))
    session.commit()
    with pytest.raises(OperationsError):
        logs.restart(
            JOHN_PAUL, datetime(2025, 1, 15, 11, 30), datetime(2025, 1, 15, 13, 15), "R Skaggs"
        )


def test_a_hold_is_called_and_cleared(logs, session):
    logs.call_hold(
        HoldRecord(
            lift=JOHN_PAUL,
            reason=HoldReason.GUST,
            started=datetime(2025, 1, 15, 10, 0),
            started_by="D Chin",
            detail="gusting 58 across the line",
        )
    )
    session.commit()
    assert logs.holds(JOHN_PAUL).is_held(JOHN_PAUL)
    cleared = logs.clear_hold(
        JOHN_PAUL, datetime(2025, 1, 15, 10, 0), datetime(2025, 1, 15, 11, 20), "D Chin"
    )
    session.commit()
    assert cleared.length().minutes == 80
    assert not logs.holds(JOHN_PAUL).is_held(JOHN_PAUL)


def test_clearing_a_hold_nobody_called(logs):
    with pytest.raises(StorageError):
        logs.clear_hold(
            JOHN_PAUL, datetime(2025, 1, 15, 6, 0), datetime(2025, 1, 15, 7, 0), "D Chin"
        )


def test_the_holds_come_back_as_a_register_across_the_hill(logs, session):
    for code, at in ((JOHN_PAUL, 9), (STRAWBERRY, 10)):
        logs.call_hold(
            HoldRecord(
                lift=code,
                reason=HoldReason.ICING,
                started=datetime(2025, 1, 15, at, 0),
                started_by="D Chin",
            )
        )
    session.commit()
    assert len(logs.holds()) == 2
    assert len(logs.holds(JOHN_PAUL)) == 1


def test_the_meter_is_read_once_a_day(logs, session):
    reading = MeterReading(JOHN_PAUL, DAY, Decimal("40120.5"), 1_402_000, "R Skaggs")
    logs.read_meter(reading)
    session.commit()
    assert logs.meter_on(JOHN_PAUL, DAY) == reading
    with pytest.raises(StorageError):
        logs.read_meter(reading)


def test_a_day_nobody_read_the_meter_on(logs):
    assert logs.meter_on(JOHN_PAUL, DAY) is None


def test_the_readings_come_back_as_a_history_that_can_be_stepped(logs, session):
    for offset, hours in enumerate(("40120.5", "40128.0", "40135.5")):
        logs.read_meter(
            MeterReading(
                JOHN_PAUL,
                DAY + timedelta(days=offset),
                Decimal(hours),
                1_402_000 + offset * 4000,
                "R Skaggs",
            )
        )
    session.commit()
    history = logs.meters(JOHN_PAUL)
    assert len(history) == 3
    assert history.hours_run() == Decimal("15.0")
    assert history.cycles_run() == 8000


def test_the_meter_reading_lands_with_the_day_when_the_log_carries_one(logs, session):
    reading = MeterReading(JOHN_PAUL, DAY, Decimal("40120.5"), 1_402_000, "R Skaggs")
    logs.add(log().with_meter(reading))
    session.commit()
    found = logs.get(JOHN_PAUL, DAY)
    assert found.meter == reading
