from datetime import datetime

import pytest

from hill import JOHN_PAUL
from sheaveline.board.incident import (
    IMMEDIATE,
    MONTHLY,
    NEXT_DAY,
    BoardError,
    Incident,
    IncidentKind,
    InjuryLevel,
)
from sheaveline.measures.duration import Duration

AT = datetime(2025, 1, 15, 11, 30)


def incident(
    kind=IncidentKind.LOADING,
    injury=InjuryLevel.NONE,
    people=1,
    narrative="skier caught a pole on the ramp",
    reported_by="K Ostler",
    at=AT,
):
    return Incident(JOHN_PAUL, at, kind, injury, people, narrative, reported_by)


def test_an_incident_carries_a_narrative_and_a_name():
    with pytest.raises(BoardError):
        incident(narrative="   ")
    with pytest.raises(BoardError):
        incident(reported_by="")


def test_somebody_hurt_means_somebody_involved():
    with pytest.raises(BoardError):
        incident(injury=InjuryLevel.FIRST_AID, people=0)
    with pytest.raises(BoardError):
        incident(people=-1)
    assert incident(injury=InjuryLevel.NONE, people=0).people == 0


def test_an_ordinary_loading_bump_goes_in_the_monthly_return():
    ordinary = incident(kind=IncidentKind.LOADING, injury=InjuryLevel.FIRST_AID)
    assert ordinary.deadline() == MONTHLY
    assert ordinary.goes_in_the_monthly_return()
    assert not ordinary.is_immediate()


def test_a_deropement_with_nobody_hurt_is_still_a_two_hour_call():
    quiet = incident(kind=IncidentKind.DEROPEMENT, injury=InjuryLevel.NONE, people=0)
    assert quiet.deadline() == IMMEDIATE
    assert quiet.is_immediate()
    assert quiet.deadline_driver() == "kind"


def test_a_serious_injury_on_a_machine_that_behaved_is_still_a_two_hour_call():
    hurt = incident(kind=IncidentKind.UNLOADING, injury=InjuryLevel.ADMITTED)
    assert hurt.deadline() == IMMEDIATE
    assert hurt.deadline_driver() == "injury"


def test_when_both_fire_the_shorter_one_wins():
    both = incident(kind=IncidentKind.EVACUATION, injury=InjuryLevel.ADMITTED)
    assert both.deadline() == IMMEDIATE
    assert both.deadline_driver() == "injury"
    other = incident(kind=IncidentKind.DEROPEMENT, injury=InjuryLevel.TRANSPORTED)
    assert other.deadline() == IMMEDIATE
    assert other.deadline_driver() == "kind"


def test_two_equal_deadlines_are_reported_as_both():
    equal = incident(kind=IncidentKind.DEROPEMENT, injury=InjuryLevel.FATAL)
    assert equal.deadline_driver() == "both"


def test_a_transported_skier_is_a_next_day_filing():
    hurt = incident(kind=IncidentKind.LOADING, injury=InjuryLevel.TRANSPORTED)
    assert hurt.deadline() == NEXT_DAY
    assert not hurt.is_immediate()
    assert not hurt.goes_in_the_monthly_return()


def test_an_evacuation_is_a_next_day_filing_however_it_went():
    quiet = incident(kind=IncidentKind.EVACUATION, injury=InjuryLevel.NONE, people=0)
    assert quiet.deadline() == NEXT_DAY


def test_every_kind_and_every_level_carries_a_deadline():
    for kind in IncidentKind:
        assert kind.deadline.seconds > 0
    for level in InjuryLevel:
        assert level.deadline.seconds > 0


def test_the_due_time_is_the_deadline_after_the_event():
    quiet = incident(kind=IncidentKind.DEROPEMENT, injury=InjuryLevel.NONE, people=0)
    assert quiet.due_by() == datetime(2025, 1, 15, 13, 30)


def test_a_filing_after_the_deadline_is_overdue():
    quiet = incident(kind=IncidentKind.DEROPEMENT, injury=InjuryLevel.NONE, people=0)
    assert not quiet.is_overdue(datetime(2025, 1, 15, 12, 30))
    assert quiet.is_overdue(datetime(2025, 1, 15, 14, 30))
    assert quiet.is_overdue(AT, filed=datetime(2025, 1, 16, 9, 0))
    assert not quiet.is_overdue(datetime(2025, 1, 20), filed=datetime(2025, 1, 15, 12, 0))


def test_some_kinds_hold_the_machine_whatever_the_injuries_were():
    for kind in (
        IncidentKind.DEROPEMENT,
        IncidentKind.ROLLBACK,
        IncidentKind.STRUCTURAL,
        IncidentKind.FALL_FROM_CARRIER,
    ):
        assert incident(kind=kind, injury=InjuryLevel.NONE, people=0).holds_the_machine()


def test_a_serious_injury_holds_the_machine_whatever_the_kind_was():
    assert incident(kind=IncidentKind.LOADING, injury=InjuryLevel.ADMITTED).holds_the_machine()
    assert not incident(kind=IncidentKind.LOADING, injury=InjuryLevel.FIRST_AID).holds_the_machine()


def test_the_injury_levels_know_which_of_them_are_serious():
    assert InjuryLevel.FATAL.is_serious
    assert InjuryLevel.ADMITTED.is_serious
    assert not InjuryLevel.TRANSPORTED.is_serious
    assert not InjuryLevel.NONE.is_serious


def test_the_deadlines_are_ordered_the_way_they_read():
    assert IMMEDIATE < NEXT_DAY < MONTHLY
    assert Duration.from_hours(2) == IMMEDIATE


def test_an_incident_prints_a_one_liner():
    printed = str(incident(kind=IncidentKind.DEROPEMENT, injury=InjuryLevel.NONE, people=0))
    assert printed == "JP deropement at 2025-01-15 11:30, none"
