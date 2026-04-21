from datetime import date, datetime

import pytest
from sqlalchemy.orm import Session

from hill import JOHN_PAUL, STRAWBERRY, john_paul, strawberry, wildcat
from sheaveline.board.incident import Incident, IncidentKind, InjuryLevel
from sheaveline.measures.length import Length
from sheaveline.persistence.base import StorageError, build_engine, create_schema
from sheaveline.persistence.repositories import (
    IncidentRepository,
    LiftRepository,
    RopeRepository,
    WearSurveyRepository,
    WorkOrderRepository,
)
from sheaveline.plant.identity import RopeSerial
from sheaveline.ropework.wear import DiameterReading, WearSurvey
from sheaveline.upkeep.workorder import Priority, WorkOrder


@pytest.fixture
def session():
    engine = build_engine()
    create_schema(engine)
    with Session(engine) as opened:
        yield opened
    engine.dispose()


def stocked(session):
    lifts = LiftRepository(session)
    lifts.add(john_paul())
    lifts.add(strawberry())
    session.commit()
    return lifts


def test_a_lift_goes_in_and_comes_back_out(session):
    lifts = stocked(session)
    found = lifts.get(JOHN_PAUL)
    assert found.name == "John Paul"
    assert found.carriers_on_line() == 84
    assert lifts.exists(JOHN_PAUL)


def test_a_lift_that_is_not_there(session):
    lifts = LiftRepository(session)
    with pytest.raises(StorageError):
        lifts.get(JOHN_PAUL)
    assert not lifts.exists(JOHN_PAUL)


def test_a_lift_goes_in_once(session):
    lifts = stocked(session)
    with pytest.raises(StorageError):
        lifts.add(john_paul())


def test_the_lifts_come_back_in_code_order(session):
    lifts = stocked(session)
    assert lifts.codes() == [JOHN_PAUL, STRAWBERRY]
    assert [lift.code for lift in lifts.all()] == [JOHN_PAUL, STRAWBERRY]


def test_adding_a_lift_puts_its_rope_on_file(session):
    stocked(session)
    ropes = RopeRepository(session)
    assert ropes.exists(john_paul().rope.serial)
    assert [rope.serial for rope in ropes.on_lift(JOHN_PAUL)] == [john_paul().rope.serial]


def test_a_rope_that_is_on_file_already_is_not_added_twice(session):
    stocked(session)
    ropes = RopeRepository(session)
    with pytest.raises(StorageError):
        ropes.add(john_paul().rope, JOHN_PAUL)


def test_a_spare_rope_sits_on_no_machine(session):
    stocked(session)
    ropes = RopeRepository(session)
    spare = wildcat().rope
    ropes.add(spare)
    session.commit()
    assert [rope.serial for rope in ropes.spares()] == [spare.serial]
    assert len(ropes.all()) == 3


def test_a_rope_can_be_taken_off_file(session):
    ropes = RopeRepository(session)
    ropes.add(wildcat().rope)
    session.commit()
    ropes.remove(wildcat().rope.serial)
    session.commit()
    assert not ropes.exists(wildcat().rope.serial)
    with pytest.raises(StorageError):
        ropes.remove(wildcat().rope.serial)


def test_a_rope_that_is_not_there(session):
    with pytest.raises(StorageError):
        RopeRepository(session).get(RopeSerial.parse("BRI-2011-01887"))


def test_a_lift_can_be_taken_off_file(session):
    lifts = stocked(session)
    lifts.remove(STRAWBERRY)
    session.commit()
    assert lifts.codes() == [JOHN_PAUL]
    with pytest.raises(StorageError):
        lifts.remove(STRAWBERRY)


def survey(rope, taken=date(2025, 1, 15), inches="1.376"):
    return WearSurvey.of(
        rope,
        taken,
        "R Halvorsen",
        [
            DiameterReading(
                Length.from_feet(0),
                Length.from_inches(inches),
                Length.from_inches(inches),
            )
        ],
    )


def test_wear_surveys_come_back_in_the_order_they_were_walked(session):
    stocked(session)
    rope = john_paul().rope
    surveys = WearSurveyRepository(session)
    surveys.add(survey(rope, date(2025, 1, 15)))
    surveys.add(survey(rope, date(2024, 12, 1), inches="1.377"))
    session.commit()
    walked = surveys.for_rope(rope)
    assert [one.taken for one in walked] == [date(2024, 12, 1), date(2025, 1, 15)]
    assert surveys.latest(rope).taken == date(2025, 1, 15)
    assert surveys.count_for(rope) == 2


def test_a_survey_can_be_fetched_by_the_day_it_was_taken(session):
    stocked(session)
    rope = john_paul().rope
    surveys = WearSurveyRepository(session)
    surveys.add(survey(rope))
    session.commit()
    assert surveys.on(rope, date(2025, 1, 15)).inspector == "R Halvorsen"
    with pytest.raises(StorageError):
        surveys.on(rope, date(2025, 2, 1))


def test_a_rope_never_walked_has_no_latest_survey(session):
    stocked(session)
    with pytest.raises(StorageError):
        WearSurveyRepository(session).latest(john_paul().rope)


def incident(at=datetime(2025, 1, 15, 11, 30), lift=JOHN_PAUL):
    return Incident(
        lift,
        at,
        IncidentKind.DEROPEMENT,
        InjuryLevel.NONE,
        0,
        "chair 34 off at T4",
        "K Ostler",
    )


def test_incidents_come_back_by_machine_and_in_order(session):
    stocked(session)
    incidents = IncidentRepository(session)
    incidents.add(incident(at=datetime(2025, 1, 15, 14, 0)))
    incidents.add(incident(at=datetime(2025, 1, 15, 11, 30)))
    incidents.add(incident(lift=STRAWBERRY))
    session.commit()
    found = incidents.for_lift(JOHN_PAUL)
    assert [one.at.hour for one in found] == [11, 14]
    assert len(incidents.for_lift(STRAWBERRY)) == 1


def test_everything_starts_unfiled(session):
    stocked(session)
    incidents = IncidentRepository(session)
    incidents.add(incident())
    session.commit()
    assert len(incidents.unfiled()) == 1


def test_incidents_can_be_pulled_out_by_date(session):
    stocked(session)
    incidents = IncidentRepository(session)
    incidents.add(incident(at=datetime(2025, 1, 15, 11, 30)))
    incidents.add(incident(at=datetime(2025, 3, 1, 11, 30)))
    session.commit()
    found = incidents.between(date(2025, 1, 1), date(2025, 1, 31))
    assert len(found) == 1


def order(number=1, priority=Priority.SCHEDULED, closed=None):
    return WorkOrder(
        number=number,
        lift=JOHN_PAUL,
        raised=date(2025, 1, 15),
        raised_by="M Vetter",
        detail="sheave liner cracked",
        priority=priority,
        where="JP-T4",
        closed=closed,
        closed_by=None if closed is None else "M Vetter",
    )


def test_work_orders_take_the_next_number(session):
    stocked(session)
    orders = WorkOrderRepository(session)
    assert orders.next_number() == 1
    orders.add(order(number=1))
    session.commit()
    assert orders.next_number() == 2


def test_a_work_order_goes_in_once(session):
    stocked(session)
    orders = WorkOrderRepository(session)
    orders.add(order())
    session.commit()
    with pytest.raises(StorageError):
        orders.add(order())


def test_a_work_order_comes_back_the_way_it_went_in(session):
    stocked(session)
    orders = WorkOrderRepository(session)
    orders.add(order())
    session.commit()
    found = orders.get(1)
    assert found.priority is Priority.SCHEDULED
    assert found.where == "JP-T4"
    assert found.is_open()


def test_closing_a_work_order_writes_it_back(session):
    stocked(session)
    orders = WorkOrderRepository(session)
    orders.add(order())
    session.commit()
    orders.close(1, date(2025, 1, 20), "M Vetter")
    session.commit()
    assert not orders.get(1).is_open()
    assert orders.open_orders() == []


def test_the_open_orders_can_be_narrowed_and_sorted(session):
    stocked(session)
    orders = WorkOrderRepository(session)
    orders.add(order(number=1, priority=Priority.URGENT))
    orders.add(order(number=2, priority=Priority.ROUTINE))
    session.commit()
    assert [one.number for one in orders.open_orders(JOHN_PAUL)] == [1, 2]
    assert orders.open_orders(STRAWBERRY) == []
    assert [one.number for one in orders.of_priority(Priority.URGENT)] == [1]


def test_a_work_order_that_is_not_there(session):
    with pytest.raises(StorageError):
        WorkOrderRepository(session).get(9)
    with pytest.raises(StorageError):
        WorkOrderRepository(session).close(9, date(2025, 1, 20), "M Vetter")


def test_a_rope_taken_off_a_machine_stays_on_file_as_a_spare(session):
    lifts = stocked(session)
    ropes = RopeRepository(session)
    serial = john_paul().rope.serial
    lifts.remove(JOHN_PAUL)
    session.flush()
    assert ropes.exists(serial)
    assert serial in {one.serial for one in ropes.spares()}


def test_putting_the_machine_back_claims_its_rope_again(session):
    lifts = stocked(session)
    ropes = RopeRepository(session)
    serial = john_paul().rope.serial
    lifts.remove(JOHN_PAUL)
    session.flush()
    lifts.add(john_paul())
    session.commit()
    assert lifts.get(JOHN_PAUL).rope.serial == serial
    assert ropes.spares() == []


def test_a_rope_already_on_another_machine_cannot_be_claimed(session):
    stocked(session)
    ropes = RopeRepository(session)
    with pytest.raises(StorageError):
        ropes.attach(john_paul().rope.serial, STRAWBERRY)


def test_a_rope_can_be_taken_off_a_machine_without_being_deleted(session):
    stocked(session)
    ropes = RopeRepository(session)
    serial = john_paul().rope.serial
    ropes.detach(serial)
    session.flush()
    assert serial in {one.serial for one in ropes.spares()}
    with pytest.raises(StorageError):
        ropes.detach(RopeSerial.parse("FAT-2019-99999"))


def test_every_repository_shares_the_one_piece_of_session_plumbing(session):
    from sheaveline.persistence.repositories import Repository

    for kind in (
        LiftRepository,
        RopeRepository,
        WearSurveyRepository,
        IncidentRepository,
        WorkOrderRepository,
    ):
        assert issubclass(kind, Repository)
        assert kind(session).session is session


def test_the_fetch_helper_says_which_row_was_asked_for(session):
    from sheaveline.persistence.models.plant import LiftRow
    from sheaveline.persistence.repositories import Repository

    plumbing = Repository(session)
    with pytest.raises(StorageError) as refused:
        plumbing.fetch(LiftRow, JOHN_PAUL, "there is no lift JP")
    assert "there is no lift JP" in str(refused.value)


def test_the_find_helper_hands_back_nothing_rather_than_complaining(session):
    from sheaveline.persistence.models.plant import LiftRow
    from sheaveline.persistence.repositories import Repository

    assert Repository(session).find(LiftRow, JOHN_PAUL) is None
    stocked(session)
    assert Repository(session).find(LiftRow, JOHN_PAUL) is not None


def test_the_one_helper_complains_when_a_query_finds_nothing(session):
    from sqlalchemy import select

    from sheaveline.persistence.models.plant import LiftRow
    from sheaveline.persistence.repositories import Repository

    plumbing = Repository(session)
    statement = select(LiftRow).where(LiftRow.name == "Nothing")
    with pytest.raises(StorageError):
        plumbing.one(statement, "no such machine")


def test_the_many_helper_hands_back_a_list(session):
    from sqlalchemy import select

    from sheaveline.persistence.models.plant import LiftRow
    from sheaveline.persistence.repositories import Repository

    stocked(session)
    found = Repository(session).many(select(LiftRow).order_by(LiftRow.code))
    assert [one.code for one in found] == [JOHN_PAUL, STRAWBERRY]
