from datetime import date

import pytest
from sqlalchemy.orm import Session

from hill import JOHN_PAUL, STRAWBERRY, john_paul, strawberry
from sheaveline.ops.shift import Assignment, Post
from sheaveline.people.certification import Certificate, Endorsement
from sheaveline.persistence.base import StorageError, build_engine, create_schema
from sheaveline.persistence.repositories import LiftRepository, PersonnelRepository
from sheaveline.plant.classes import LiftClass
from sheaveline.schedule.hours import OperatingWindow, TimeOfDay

ON = date(2025, 1, 15)
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
def people(session):
    return PersonnelRepository(session)


def card(
    person="D Chin",
    endorsement=Endorsement.OPERATOR,
    issued=date(2024, 11, 1),
    expires=date(2025, 11, 1),
    classes=None,
):
    return Certificate(
        person=person,
        endorsement=endorsement,
        issued=issued,
        expires=expires,
        issued_by="Utah tramway board",
        classes=classes if classes is not None else frozenset(LiftClass),
    )


def shift(person="D Chin", post=Post.BOTTOM, starts="08:30", ends="16:45"):
    return Assignment(
        person=person, post=post, starts=TimeOfDay.parse(starts), ends=TimeOfDay.parse(ends)
    )


def test_a_card_goes_in_and_comes_back_out(people, session):
    people.issue(card())
    session.commit()
    found = people.for_person("D Chin")
    assert len(found) == 1
    assert found[0].endorsement is Endorsement.OPERATOR
    assert found[0].is_current(ON)


def test_the_classes_a_card_covers_survive_the_round_trip(people, session):
    people.issue(card(classes=frozenset({LiftClass.DETACHABLE_CHAIR})))
    session.commit()
    found = people.for_person("D Chin")[0]
    assert found.classes == frozenset({LiftClass.DETACHABLE_CHAIR})
    assert found.covers_class(LiftClass.DETACHABLE_CHAIR)
    assert not found.covers_class(LiftClass.SURFACE_TOW)


def test_a_card_covering_everything_comes_back_covering_everything(people, session):
    people.issue(card())
    session.commit()
    assert people.for_person("D Chin")[0].classes == frozenset(LiftClass)


def test_the_same_card_is_not_issued_twice(people, session):
    people.issue(card())
    session.commit()
    with pytest.raises(StorageError, match="already has"):
        people.issue(card())


def test_the_whole_file_answers_who_may_hold_a_post(people, session):
    people.issue(card(person="D Chin", endorsement=Endorsement.OPERATOR))
    people.issue(card(person="R Skaggs", endorsement=Endorsement.MECHANIC))
    session.commit()
    held = people.file()
    assert held.qualified_for(Post.BOTTOM, LiftClass.DETACHABLE_CHAIR, ON) == ("D Chin",)
    assert people.people() == ["D Chin", "R Skaggs"]


def test_renewing_a_card_writes_a_new_one_and_keeps_the_old(people, session):
    people.issue(card())
    session.commit()
    renewed = people.renew("D Chin", Endorsement.OPERATOR, date(2026, 11, 1), "R Skaggs")
    session.commit()
    assert renewed.expires == date(2026, 11, 1)
    assert len(people.for_person("D Chin")) == 2
    assert people.file().current_for("D Chin", date(2026, 1, 1))


def test_renewing_a_card_nobody_holds(people):
    with pytest.raises(StorageError, match="no operator card to renew"):
        people.renew("D Chin", Endorsement.OPERATOR, date(2026, 11, 1), "R Skaggs")


def test_a_shift_goes_in_and_comes_back_in_the_roster(people, session):
    people.assign(JOHN_PAUL, ON, shift())
    session.commit()
    roster = people.roster(JOHN_PAUL, ON, WINDOW)
    assert roster.lift == JOHN_PAUL
    assert roster.lift_class is LiftClass.DETACHABLE_CHAIR
    assert [one.person for one in roster.assignments] == ["D Chin"]


def test_the_same_person_cannot_start_twice_at_the_same_time(people, session):
    people.assign(JOHN_PAUL, ON, shift())
    session.commit()
    with pytest.raises(StorageError, match="already on JP"):
        people.assign(JOHN_PAUL, ON, shift())


def test_a_roster_on_a_machine_that_is_not_on_the_register(people):
    from sheaveline.plant.identity import LiftCode

    with pytest.raises(StorageError):
        people.roster(LiftCode("ZZ"), ON, WINDOW)


def test_the_roster_is_only_the_shifts_on_that_machine_and_that_day(people, session):
    people.assign(JOHN_PAUL, ON, shift())
    people.assign(STRAWBERRY, ON, shift(person="T Woo"))
    people.assign(JOHN_PAUL, date(2025, 1, 16), shift(person="J Marsh"))
    session.commit()
    assert [one.person for one in people.roster(JOHN_PAUL, ON, WINDOW).assignments] == ["D Chin"]


def test_where_one_person_is_down_to_be_across_the_hill(people, session):
    people.assign(JOHN_PAUL, ON, shift(starts="08:30", ends="12:30"))
    people.assign(STRAWBERRY, ON, shift(starts="13:00", ends="16:45"))
    session.commit()
    found = people.working("D Chin", ON)
    assert [code for code, _ in found] == [JOHN_PAUL, STRAWBERRY]


def test_somebody_who_is_not_working(people):
    assert people.working("T Woo", ON) == []
