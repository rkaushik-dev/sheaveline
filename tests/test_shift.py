from datetime import date

import pytest

from hill import JOHN_PAUL, WILDCAT
from sheaveline.measures.duration import Duration
from sheaveline.ops.preop import OperationsError
from sheaveline.ops.shift import Assignment, Post, Roster
from sheaveline.plant.classes import LiftClass
from sheaveline.schedule.hours import OperatingWindow, TimeOfDay

DAY = date(2025, 1, 15)
WINDOW = OperatingWindow.parse("9:00", "3:45 pm", "4:15 pm")


def assignment(person, post, start_hour, end_hour, start_min=0, end_min=0):
    return Assignment(
        person=person,
        post=post,
        starts=TimeOfDay.at(start_hour, start_min),
        ends=TimeOfDay.at(end_hour, end_min),
    )


def roster(*assignments, lift_class=LiftClass.DETACHABLE_CHAIR, lift=JOHN_PAUL):
    return Roster.of(lift, lift_class, DAY, WINDOW, assignments)


def full_day(*posts, person_prefix="P"):
    return [
        assignment(f"{person_prefix}{index}", post, 8, 17)
        for index, post in enumerate(posts, start=1)
    ]


def test_each_class_names_the_posts_it_has_to_fill():
    assert Roster.of(JOHN_PAUL, LiftClass.DETACHABLE_CHAIR, DAY, WINDOW).required() == frozenset(
        {Post.BOTTOM, Post.TOP}
    )
    assert Roster.of(WILDCAT, LiftClass.SURFACE_TOW, DAY, WINDOW).required() == frozenset(
        {Post.BOTTOM}
    )
    tram = Roster.of(JOHN_PAUL, LiftClass.AERIAL_TRAM, DAY, WINDOW)
    assert Post.MECHANIC in tram.required()


def test_an_assignment_names_a_person_and_runs_forwards():
    with pytest.raises(OperationsError):
        assignment("  ", Post.BOTTOM, 9, 16)
    with pytest.raises(OperationsError):
        assignment("R Diaz", Post.BOTTOM, 16, 9)


def test_an_assignment_covers_its_own_stretch():
    shift = assignment("R Diaz", Post.BOTTOM, 9, 16)
    assert shift.covers(TimeOfDay.at(9))
    assert shift.covers(TimeOfDay.at(12))
    assert not shift.covers(TimeOfDay.at(16))
    assert not shift.covers(TimeOfDay.at(8, 59))
    assert shift.length() == Duration.from_hours(7)


def test_an_assignment_prints_the_way_a_board_reads():
    assert str(assignment("R Diaz", Post.BOTTOM, 9, 16)) == (
        "R Diaz on bottom operator, 9:00 to 16:00"
    )


def test_two_shifts_at_once_for_one_person_are_refused():
    with pytest.raises(OperationsError):
        roster(
            assignment("R Diaz", Post.BOTTOM, 9, 13),
            assignment("R Diaz", Post.TOP, 12, 16),
        )


def test_the_same_person_can_work_two_stretches_that_do_not_touch():
    covered = roster(
        assignment("R Diaz", Post.BOTTOM, 8, 12),
        assignment("R Diaz", Post.TOP, 12, 17),
    )
    assert covered.hours_worked("R Diaz") == Duration.from_hours(9)


def test_a_fully_staffed_machine_has_no_gaps():
    covered = roster(*full_day(Post.BOTTOM, Post.TOP))
    assert covered.is_covered()
    assert covered.gaps() == ()
    assert covered.is_staffed_at(TimeOfDay.at(12))
    assert str(covered) == "JP 2025-01-15, 2 on, covered"


def test_a_missing_post_is_a_gap_for_the_whole_day():
    thin = roster(*full_day(Post.BOTTOM))
    assert not thin.is_covered()
    assert thin.gaps()[0][0] is Post.TOP
    assert not thin.is_staffed_at(TimeOfDay.at(12))


def test_the_ten_minute_lunch_gap_is_found():
    slipped = roster(
        assignment("R Diaz", Post.BOTTOM, 8, 17),
        assignment("T Woo", Post.TOP, 8, 12),
        assignment("J Marsh", Post.TOP, 12, 17, start_min=10),
    )
    assert not slipped.is_covered()
    gaps = slipped.gaps()
    assert len(gaps) == 1
    assert gaps[0][0] is Post.TOP
    assert gaps[0][1] == TimeOfDay.at(12)


def test_a_clean_handover_leaves_no_gap():
    clean = roster(
        assignment("R Diaz", Post.BOTTOM, 8, 17),
        assignment("T Woo", Post.TOP, 8, 12),
        assignment("J Marsh", Post.TOP, 12, 17),
    )
    assert clean.is_covered()


def test_a_shift_that_ends_before_the_sweep_leaves_a_gap_at_the_end():
    early = roster(
        assignment("R Diaz", Post.BOTTOM, 8, 17),
        assignment("T Woo", Post.TOP, 8, 16),
    )
    gaps = early.gaps()
    assert gaps
    assert gaps[0][1] == TimeOfDay.at(16)


def test_a_tow_is_covered_by_one_person():
    tow = Roster.of(
        WILDCAT,
        LiftClass.SURFACE_TOW,
        DAY,
        WINDOW,
        [assignment("A Bell", Post.BOTTOM, 8, 17)],
    )
    assert tow.is_covered()


def test_a_tram_is_not_covered_without_a_mechanic():
    tram = Roster.of(
        JOHN_PAUL,
        LiftClass.AERIAL_TRAM,
        DAY,
        WINDOW,
        [assignment("R Diaz", Post.BOTTOM, 8, 17), assignment("T Woo", Post.TOP, 8, 17)],
    )
    assert not tram.is_covered()
    assert tram.gaps()[0][0] is Post.MECHANIC


def test_who_is_on_at_a_given_minute():
    covered = roster(
        assignment("R Diaz", Post.BOTTOM, 8, 17),
        assignment("T Woo", Post.TOP, 8, 12),
        assignment("J Marsh", Post.TOP, 12, 17),
    )
    assert covered.people_at(TimeOfDay.at(11)) == ("R Diaz", "T Woo")
    assert covered.people_at(TimeOfDay.at(13)) == ("J Marsh", "R Diaz")
    assert covered.staffing_at(TimeOfDay.at(11)) == frozenset({Post.BOTTOM, Post.TOP})


def test_the_people_on_the_machine_are_listed_once_each():
    covered = roster(
        assignment("R Diaz", Post.BOTTOM, 8, 12),
        assignment("R Diaz", Post.TOP, 12, 17),
    )
    assert covered.people() == ("R Diaz",)


def test_adding_an_assignment_can_close_a_gap():
    thin = roster(*full_day(Post.BOTTOM))
    assert not thin.is_covered()
    filled = thin.with_assignment(assignment("T Woo", Post.TOP, 8, 17))
    assert filled.is_covered()


def test_the_posts_know_which_of_them_are_operators():
    assert Post.BOTTOM.is_an_operator
    assert Post.MID.is_an_operator
    assert not Post.MECHANIC.is_an_operator
