from datetime import date
from decimal import Decimal

import pytest
from sqlalchemy import inspect, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from sheaveline.measures.angle import Bearing
from sheaveline.measures.speed import Speed
from sheaveline.persistence.base import build_engine, create_schema
from sheaveline.persistence.models.plant import LiftRow
from sheaveline.persistence.models.upkeep import (
    DefectRow,
    InspectionRecordRow,
    WorkOrderRow,
)
from sheaveline.plant.classes import LiftClass
from sheaveline.plant.identity import LiftCode
from sheaveline.upkeep.record import DefectLevel
from sheaveline.upkeep.workorder import Priority

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


def record(code="W1", done=DAY, hours=None, cycles=None):
    return InspectionRecordRow(
        lift_code=JP, code=code, done=done, by="M Vetter", hours=hours, cycles=cycles
    )


def defect_row(level=DefectLevel.DEFECT, cleared=None):
    return DefectRow(
        where="JP-T4",
        level=level,
        detail="sheave liner cracked",
        raised=DAY,
        cleared=cleared,
        cleared_by=None if cleared is None else "M Vetter",
    )


def test_the_schema_carries_every_upkeep_table(engine):
    tables = set(inspect(engine).get_table_names())
    assert {"inspection_records", "defects", "work_orders"} <= tables


def test_a_record_takes_its_defects_with_it(engine):
    with Session(engine) as session:
        walked = record()
        walked.defects.append(defect_row())
        session.add(walked)
        session.commit()
    with Session(engine) as session:
        found = session.scalars(select(InspectionRecordRow)).one()
        assert len(found.defects) == 1
        assert found.defects[0].level is DefectLevel.DEFECT
        assert found.lift.name == "John Paul"


def test_one_record_a_job_a_day(engine):
    with Session(engine) as session:
        session.add(record())
        session.add(record())
        with pytest.raises(IntegrityError):
            session.commit()


def test_the_same_job_can_be_done_on_two_days(engine):
    with Session(engine) as session:
        session.add(record(done=date(2025, 1, 8)))
        session.add(record(done=date(2025, 1, 15)))
        session.commit()
    with Session(engine) as session:
        assert len(session.scalars(select(InspectionRecordRow)).all()) == 2


def test_a_record_keeps_its_meter_readings(engine):
    with Session(engine) as session:
        session.add(record(code="H1", hours=Decimal("42000.0"), cycles=1_000_000))
        session.commit()
    with Session(engine) as session:
        found = session.scalars(select(InspectionRecordRow)).one()
        assert found.hours == Decimal("42000.0")
        assert found.cycles == 1_000_000


def test_a_defect_cannot_be_cleared_before_it_was_raised(engine):
    with Session(engine) as session:
        walked = record()
        walked.defects.append(defect_row(cleared=date(2025, 1, 1)))
        session.add(walked)
        with pytest.raises(IntegrityError):
            session.commit()


def test_a_defect_needs_a_record_to_hang_off(engine):
    with Session(engine) as session:
        session.add(defect_row())
        with pytest.raises(IntegrityError):
            session.commit()


def test_deleting_a_record_takes_its_defects_with_it(engine):
    with Session(engine) as session:
        walked = record()
        walked.defects.append(defect_row())
        session.add(walked)
        session.commit()
        session.delete(walked)
        session.commit()
    with Session(engine) as session:
        assert session.scalars(select(DefectRow)).all() == []


def test_a_work_order_carries_its_own_place_and_priority(engine):
    with Session(engine) as session:
        session.add(
            WorkOrderRow(
                number=1,
                lift_code=JP,
                raised=DAY,
                raised_by="M Vetter",
                detail="sheave liner cracked",
                priority=Priority.SCHEDULED,
                where="JP-T4",
            )
        )
        session.commit()
    with Session(engine) as session:
        found = session.get(WorkOrderRow, 1)
        assert found is not None
        assert found.priority is Priority.SCHEDULED
        assert found.where == "JP-T4"
        assert found.closed is None


def test_a_work_order_does_not_read_through_to_the_defect(engine):
    """Downgrading a defect later must not rewrite what the department decided."""
    with Session(engine) as session:
        walked = record()
        walked.defects.append(defect_row(level=DefectLevel.SERIOUS))
        session.add(walked)
        session.add(
            WorkOrderRow(
                number=1,
                lift_code=JP,
                raised=DAY,
                raised_by="M Vetter",
                detail="sheave liner cracked",
                priority=Priority.IMMEDIATE,
                where="JP-T4",
            )
        )
        session.commit()
        found = session.scalars(select(DefectRow)).one()
        found.level = DefectLevel.OBSERVATION
        session.commit()
    with Session(engine) as session:
        order = session.get(WorkOrderRow, 1)
        assert order is not None
        assert order.priority is Priority.IMMEDIATE


def test_a_work_order_cannot_close_before_it_was_raised(engine):
    with Session(engine) as session:
        session.add(
            WorkOrderRow(
                number=1,
                lift_code=JP,
                raised=DAY,
                raised_by="M Vetter",
                detail="a thing",
                priority=Priority.ROUTINE,
                closed=date(2025, 1, 1),
                closed_by="M Vetter",
            )
        )
        with pytest.raises(IntegrityError):
            session.commit()


def test_work_orders_are_numbered_from_one(engine):
    with Session(engine) as session:
        session.add(
            WorkOrderRow(
                number=0,
                lift_code=JP,
                raised=DAY,
                raised_by="M Vetter",
                detail="a thing",
                priority=Priority.ROUTINE,
            )
        )
        with pytest.raises(IntegrityError):
            session.commit()
