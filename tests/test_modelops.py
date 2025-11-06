from datetime import date, datetime
from decimal import Decimal

import pytest
from sqlalchemy import inspect, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from sheaveline.measures.angle import Bearing
from sheaveline.measures.speed import Speed
from sheaveline.ops.logbook import EntryKind
from sheaveline.ops.preop import CheckOutcome
from sheaveline.ops.shift import Post
from sheaveline.ops.stop import StopKind
from sheaveline.persistence.base import build_engine, create_schema
from sheaveline.persistence.models.ops import (
    AssignmentRow,
    DayLogRow,
    HoldRow,
    LogEntryRow,
    MeterReadingRow,
    PreOpResultRow,
    StopRow,
)
from sheaveline.persistence.models.plant import LiftRow
from sheaveline.plant.classes import LiftClass
from sheaveline.plant.identity import LiftCode
from sheaveline.schedule.hours import TimeOfDay
from sheaveline.weather.hold import HoldReason

JP = LiftCode("JP")
DAY = date(2025, 1, 15)


@pytest.fixture
def engine():
    made = build_engine()
    create_schema(made)
    with Session(made) as session:
        session.add(
            LiftRow(
                code=JP,
                name="John Paul",
                lift_class=LiftClass.DETACHABLE_CHAIR,
                bearing=Bearing.from_degrees(72),
                design_speed=Speed.from_feet_per_minute(1000),
                commissioned=date(2004, 12, 3),
            )
        )
        session.commit()
    yield made
    made.dispose()


def day_log(day=DAY, opened=None, closed=None):
    return DayLogRow(
        lift_code=JP,
        day=day,
        first_load=TimeOfDay.at(9),
        last_load=TimeOfDay.at(15, 45),
        sweep_by=TimeOfDay.at(16, 15),
        opened=opened,
        closed=closed,
        mechanic="M Vetter",
        operator="K Ostler",
    )


def test_the_schema_carries_every_operating_table(engine):
    tables = set(inspect(engine).get_table_names())
    assert {
        "day_logs",
        "log_entries",
        "preop_results",
        "stops",
        "holds",
        "meter_readings",
        "assignments",
    } <= tables


def test_a_day_log_takes_its_lines_and_its_checks_with_it(engine):
    with Session(engine) as session:
        book = day_log(opened=TimeOfDay.at(9), closed=TimeOfDay.at(16))
        book.entries.append(
            LogEntryRow(
                at=datetime(2025, 1, 15, 9, 0),
                kind=EntryKind.OPENED,
                text="first load at 9:00",
                by="K Ostler",
            )
        )
        book.checks.append(PreOpResultRow(code="B1", outcome=CheckOutcome.PASS, by="M Vetter"))
        session.add(book)
        session.commit()
    with Session(engine) as session:
        found = session.scalars(select(DayLogRow)).one()
        assert len(found.entries) == 1
        assert len(found.checks) == 1
        assert found.opened == TimeOfDay.at(9)
        assert found.lift.name == "John Paul"


def test_one_day_log_a_machine_a_day(engine):
    with Session(engine) as session:
        session.add(day_log())
        session.add(day_log())
        with pytest.raises(IntegrityError):
            session.commit()


def test_the_window_has_to_run_forwards(engine):
    with Session(engine) as session:
        book = day_log()
        book.last_load = TimeOfDay.at(8)
        session.add(book)
        with pytest.raises(IntegrityError):
            session.commit()


def test_the_sweep_comes_after_the_last_load(engine):
    with Session(engine) as session:
        book = day_log()
        book.sweep_by = TimeOfDay.at(15)
        session.add(book)
        with pytest.raises(IntegrityError):
            session.commit()


def test_an_item_is_walked_once_a_day(engine):
    with Session(engine) as session:
        book = day_log()
        book.checks.append(PreOpResultRow(code="B1", outcome=CheckOutcome.PASS, by="M Vetter"))
        book.checks.append(PreOpResultRow(code="B1", outcome=CheckOutcome.PASS, by="M Vetter"))
        session.add(book)
        with pytest.raises(IntegrityError):
            session.commit()


def test_a_stop_sits_against_the_lift_rather_than_the_day(engine):
    with Session(engine) as session:
        session.add(
            StopRow(
                lift_code=JP,
                kind=StopKind.WEATHER,
                stopped=datetime(2025, 1, 15, 16, 0),
                stopped_by="K Ostler",
                restarted=datetime(2025, 1, 16, 9, 0),
                restarted_by="K Ostler",
            )
        )
        session.commit()
    with Session(engine) as session:
        found = session.scalars(select(StopRow)).one()
        assert found.stopped.day == 15
        assert found.restarted is not None
        assert found.restarted.day == 16


def test_a_stop_cannot_restart_before_it_stopped(engine):
    with Session(engine) as session:
        session.add(
            StopRow(
                lift_code=JP,
                kind=StopKind.GATE,
                stopped=datetime(2025, 1, 15, 12, 0),
                stopped_by="K Ostler",
                restarted=datetime(2025, 1, 15, 11, 0),
                restarted_by="K Ostler",
            )
        )
        with pytest.raises(IntegrityError):
            session.commit()


def test_two_stops_cannot_share_a_moment_on_one_machine(engine):
    with Session(engine) as session:
        for _ in range(2):
            session.add(
                StopRow(
                    lift_code=JP,
                    kind=StopKind.GATE,
                    stopped=datetime(2025, 1, 15, 12, 0),
                    stopped_by="K Ostler",
                )
            )
        with pytest.raises(IntegrityError):
            session.commit()


def test_a_hold_runs_across_the_end_of_the_day(engine):
    with Session(engine) as session:
        session.add(
            HoldRow(
                lift_code=JP,
                reason=HoldReason.GUST,
                started=datetime(2025, 1, 15, 16, 0),
                started_by="K Ostler",
                detail="52 mph against a limit of 45 mph",
            )
        )
        session.commit()
    with Session(engine) as session:
        found = session.scalars(select(HoldRow)).one()
        assert found.reason is HoldReason.GUST
        assert found.ended is None


def test_a_hold_cannot_end_before_it_starts(engine):
    with Session(engine) as session:
        session.add(
            HoldRow(
                lift_code=JP,
                reason=HoldReason.GUST,
                started=datetime(2025, 1, 15, 16, 0),
                started_by="K Ostler",
                ended=datetime(2025, 1, 15, 15, 0),
                ended_by="K Ostler",
            )
        )
        with pytest.raises(IntegrityError):
            session.commit()


def test_one_meter_reading_a_machine_a_day(engine):
    with Session(engine) as session:
        for _ in range(2):
            session.add(
                MeterReadingRow(
                    lift_code=JP,
                    day=DAY,
                    hours=Decimal("42000.0"),
                    cycles=1_000_000,
                    read_by="M Vetter",
                )
            )
        with pytest.raises(IntegrityError):
            session.commit()


def test_a_meter_reading_keeps_its_tenth_of_an_hour(engine):
    with Session(engine) as session:
        session.add(
            MeterReadingRow(
                lift_code=JP,
                day=DAY,
                hours=Decimal("42009.5"),
                cycles=1_000_400,
                read_by="M Vetter",
            )
        )
        session.commit()
    with Session(engine) as session:
        found = session.scalars(select(MeterReadingRow)).one()
        assert found.hours == Decimal("42009.5")


def test_a_negative_meter_reading_is_refused(engine):
    with Session(engine) as session:
        session.add(
            MeterReadingRow(
                lift_code=JP,
                day=DAY,
                hours=Decimal("-1.0"),
                cycles=0,
                read_by="M Vetter",
            )
        )
        with pytest.raises(IntegrityError):
            session.commit()


def test_a_shift_runs_forwards(engine):
    with Session(engine) as session:
        session.add(
            AssignmentRow(
                lift_code=JP,
                day=DAY,
                person="R Diaz",
                post=Post.BOTTOM,
                starts=TimeOfDay.at(16),
                ends=TimeOfDay.at(9),
            )
        )
        with pytest.raises(IntegrityError):
            session.commit()


def test_two_shifts_for_one_person_can_start_at_different_times(engine):
    with Session(engine) as session:
        session.add(
            AssignmentRow(
                lift_code=JP,
                day=DAY,
                person="R Diaz",
                post=Post.BOTTOM,
                starts=TimeOfDay.at(8),
                ends=TimeOfDay.at(12),
            )
        )
        session.add(
            AssignmentRow(
                lift_code=JP,
                day=DAY,
                person="R Diaz",
                post=Post.TOP,
                starts=TimeOfDay.at(12),
                ends=TimeOfDay.at(17),
            )
        )
        session.commit()
    with Session(engine) as session:
        assert len(session.scalars(select(AssignmentRow)).all()) == 2


def test_deleting_a_day_log_takes_its_lines_with_it(engine):
    with Session(engine) as session:
        book = day_log()
        book.entries.append(
            LogEntryRow(
                at=datetime(2025, 1, 15, 11, 0),
                kind=EntryKind.NOTE,
                text="wind picking up",
                by="K Ostler",
            )
        )
        session.add(book)
        session.commit()
        session.delete(book)
        session.commit()
    with Session(engine) as session:
        assert session.scalars(select(LogEntryRow)).all() == []
