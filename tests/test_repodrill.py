from datetime import date

import pytest
from sqlalchemy.orm import Session

from hill import JOHN_PAUL, STRAWBERRY, john_paul, strawberry
from sheaveline.evac.drill import Drill, DrillKind
from sheaveline.measures.duration import Duration
from sheaveline.persistence.base import StorageError, build_engine, create_schema
from sheaveline.persistence.repositories import DrillRepository, LiftRepository

ON = date(2025, 1, 15)


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
def drills(session):
    return DrillRepository(session)


def drill(lift=JOHN_PAUL, on=ON, kind=DrillKind.FULL_LINE, teams=("alpha", "bravo"), carriers=6):
    return Drill.of(
        lift=lift,
        on=on,
        kind=kind,
        teams=teams,
        carriers_worked=carriers,
        elapsed=Duration.from_minutes(48),
        observer="R Skaggs",
    )


def test_a_drill_goes_in_and_comes_back_out(drills, session):
    drills.add(drill())
    session.commit()
    found = drills.on_lift(JOHN_PAUL)
    assert len(found) == 1
    assert found[0].teams == ("alpha", "bravo")
    assert found[0].elapsed.minutes == 48
    assert found[0].counts_for_the_annual()


def test_the_teams_come_back_in_a_settled_order(drills, session):
    drills.add(drill(teams=("charlie", "alpha", "bravo")))
    session.commit()
    assert drills.on_lift(JOHN_PAUL)[0].teams == ("alpha", "bravo", "charlie")


def test_the_same_machine_is_not_drilled_twice_in_a_day(drills, session):
    drills.add(drill())
    session.commit()
    with pytest.raises(StorageError, match="already drilled"):
        drills.add(drill())


def test_two_machines_can_drill_on_the_same_day(drills, session):
    drills.add(drill(lift=JOHN_PAUL))
    drills.add(drill(lift=STRAWBERRY))
    session.commit()
    assert len(drills.register()) == 2


def test_the_last_full_drill_is_the_one_that_matters(drills, session):
    drills.add(drill(on=date(2024, 11, 1)))
    drills.add(drill(on=date(2024, 12, 1), kind=DrillKind.PARTIAL, carriers=2))
    session.commit()
    last = drills.last_full(JOHN_PAUL)
    assert last is not None
    assert last.on == date(2024, 11, 1)


def test_the_annual_runs_a_year_from_the_last_full_drill(drills, session):
    drills.add(drill(on=date(2024, 11, 1)))
    session.commit()
    assert drills.expires(JOHN_PAUL) == date(2025, 11, 1)


def test_a_machine_that_has_never_drilled_has_no_expiry(drills):
    assert drills.expires(JOHN_PAUL) is None
    assert drills.last_full(JOHN_PAUL) is None


def test_what_is_out_of_date_is_listed(drills, session):
    drills.add(drill(lift=JOHN_PAUL, on=date(2024, 11, 1)))
    session.commit()
    assert drills.out_of_date(ON) == [STRAWBERRY]
    assert drills.out_of_date(date(2026, 1, 1)) == [JOHN_PAUL, STRAWBERRY]


def test_a_tabletop_does_not_stop_a_machine_going_out_of_date(drills, session):
    drills.add(drill(kind=DrillKind.TABLETOP, carriers=0))
    session.commit()
    assert JOHN_PAUL in drills.out_of_date(ON)
