from datetime import date

import pytest

from hill import JOHN_PAUL, john_paul, strawberry
from sheaveline.measures.length import Length
from sheaveline.persistence.base import StorageError, build_engine, create_schema
from sheaveline.persistence.uow import UnitOfWork
from sheaveline.ropework.wear import DiameterReading, WearSurvey
from sheaveline.upkeep.workorder import Priority, WorkOrder


@pytest.fixture
def engine():
    made = build_engine()
    create_schema(made)
    yield made
    made.dispose()


def order(number=1):
    return WorkOrder(
        number=number,
        lift=JOHN_PAUL,
        raised=date(2025, 1, 15),
        raised_by="M Vetter",
        detail="sheave liner cracked",
        priority=Priority.SCHEDULED,
        where="JP-T4",
    )


def test_a_committed_unit_of_work_keeps_what_it_wrote(engine):
    with UnitOfWork.on(engine) as work:
        work.lifts.add(john_paul())
        work.commit()
    with UnitOfWork.on(engine) as work:
        assert work.lifts.exists(JOHN_PAUL)


def test_a_unit_of_work_that_is_not_committed_keeps_nothing(engine):
    with UnitOfWork.on(engine) as work:
        work.lifts.add(john_paul())
    with UnitOfWork.on(engine) as work:
        assert not work.lifts.exists(JOHN_PAUL)


def test_anything_raised_inside_rolls_the_whole_thing_back(engine):
    with pytest.raises(RuntimeError), UnitOfWork.on(engine) as work:
        work.lifts.add(john_paul())
        work.commit()
        work.lifts.add(strawberry())
        raise RuntimeError("the mechanic was called away")
    with UnitOfWork.on(engine) as work:
        assert work.lifts.codes() == [JOHN_PAUL]


def test_a_rollback_inside_leaves_the_earlier_commit_alone(engine):
    with UnitOfWork.on(engine) as work:
        work.lifts.add(john_paul())
        work.commit()
        work.lifts.add(strawberry())
        work.rollback()
    with UnitOfWork.on(engine) as work:
        assert work.lifts.codes() == [JOHN_PAUL]


def test_a_unit_of_work_reports_whether_it_was_committed(engine):
    with UnitOfWork.on(engine) as work:
        assert not work.was_committed
        work.lifts.add(john_paul())
        work.commit()
        assert work.was_committed
        work.rollback()
        assert not work.was_committed


def test_a_unit_of_work_is_only_usable_inside_its_block(engine):
    work = UnitOfWork.on(engine)
    assert not work.is_open
    with pytest.raises(StorageError):
        work.commit()
    with pytest.raises(StorageError):
        _ = work.lifts
    with work:
        assert work.is_open
    assert not work.is_open


def test_a_unit_of_work_cannot_be_opened_twice_at_once(engine):
    work = UnitOfWork.on(engine)
    with work, pytest.raises(StorageError):
        work.__enter__()


def test_a_flush_lets_the_database_refuse_early(engine):
    with UnitOfWork.on(engine) as work:
        work.lifts.add(john_paul())
        work.flush()
        assert work.lifts.exists(JOHN_PAUL)


def test_every_repository_hangs_off_the_same_session(engine):
    with UnitOfWork.on(engine) as work:
        assert work.lifts.session is work.session
        assert work.ropes.session is work.session
        assert work.wear_surveys.session is work.session
        assert work.incidents.session is work.session
        assert work.work_orders.session is work.session


def test_two_repositories_see_each_other_inside_one_unit(engine):
    with UnitOfWork.on(engine) as work:
        work.lifts.add(john_paul())
        work.flush()
        assert work.ropes.exists(john_paul().rope.serial)
        work.commit()


def test_a_survey_and_an_order_written_together_land_together(engine):
    rope = john_paul().rope
    with UnitOfWork.on(engine) as work:
        work.lifts.add(john_paul())
        work.flush()
        work.wear_surveys.add(
            WearSurvey.of(
                rope,
                date(2025, 1, 15),
                "R Halvorsen",
                [
                    DiameterReading(
                        Length.from_feet(0),
                        Length.from_inches("1.376"),
                        Length.from_inches("1.376"),
                    )
                ],
            )
        )
        work.work_orders.add(order())
        work.commit()
    with UnitOfWork.on(engine) as work:
        assert work.wear_surveys.count_for(rope) == 1
        assert work.work_orders.get(1).where == "JP-T4"


def test_a_failure_halfway_leaves_neither_of_them(engine):
    rope = john_paul().rope
    with pytest.raises(StorageError), UnitOfWork.on(engine) as work:
        work.lifts.add(john_paul())
        work.flush()
        work.work_orders.add(order())
        work.work_orders.add(order())
    with UnitOfWork.on(engine) as work:
        assert not work.lifts.exists(JOHN_PAUL)
        assert not work.ropes.exists(rope.serial)


def test_a_second_unit_of_work_can_be_opened_after_the_first_closes(engine):
    work = UnitOfWork.on(engine)
    with work:
        work.lifts.add(john_paul())
        work.commit()
    with work:
        assert work.lifts.exists(JOHN_PAUL)
