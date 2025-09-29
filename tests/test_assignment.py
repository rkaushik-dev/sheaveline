from datetime import date

import pytest

from hill import JOHN_PAUL
from sheaveline.ops.shift import Assignment, Post, Roster
from sheaveline.people.assignment import ProblemKind, RosterCheck
from sheaveline.people.certification import (
    Certificate,
    Endorsement,
    PeopleError,
    PersonnelFile,
)
from sheaveline.plant.classes import LiftClass
from sheaveline.schedule.hours import OperatingWindow, TimeOfDay

DAY = date(2025, 1, 15)
WINDOW = OperatingWindow.parse("9:00", "3:45 pm", "4:15 pm")
QUAD = LiftClass.DETACHABLE_CHAIR


def card(person, endorsement=Endorsement.OPERATOR, expires=date(2025, 11, 1), classes=None):
    return Certificate(
        person=person,
        endorsement=endorsement,
        issued=date(2024, 11, 1),
        expires=expires,
        issued_by="Utah tramway board",
        classes=classes if classes is not None else frozenset(LiftClass),
    )


def shift(person, post, start=8, end=17):
    return Assignment(person, post, TimeOfDay.at(start), TimeOfDay.at(end))


def check(assignments, cards, lift_class=QUAD):
    roster = Roster.of(JOHN_PAUL, lift_class, DAY, WINDOW, assignments)
    return RosterCheck(roster, PersonnelFile.of(cards), lift_class, DAY)


def clean():
    return check(
        [shift("R Diaz", Post.BOTTOM), shift("T Woo", Post.TOP)],
        [
            card("R Diaz", Endorsement.OPERATOR),
            card("T Woo", Endorsement.LEAD_OPERATOR),
        ],
    )


def test_a_covered_roster_with_the_right_cards_is_clear():
    read = clean()
    assert read.is_legal()
    assert read.problems() == ()
    assert str(read) == "JP 2025-01-15, roster clear"


def test_a_roster_for_another_class_is_refused():
    roster = Roster.of(JOHN_PAUL, LiftClass.FIXED_CHAIR, DAY, WINDOW, [])
    with pytest.raises(PeopleError):
        RosterCheck(roster, PersonnelFile.of(), QUAD, DAY)


def test_an_empty_post_shows_as_uncovered():
    read = check([shift("R Diaz", Post.BOTTOM)], [card("R Diaz")])
    assert not read.is_legal()
    assert read.problems_of(ProblemKind.UNCOVERED)
    assert read.problems_of(ProblemKind.UNCOVERED)[0].post is Post.TOP


def test_somebody_with_no_card_at_all_is_reported():
    read = check(
        [shift("R Diaz", Post.BOTTOM), shift("T Woo", Post.TOP)],
        [card("T Woo", Endorsement.LEAD_OPERATOR)],
    )
    problems = read.problems_of(ProblemKind.UNCARDED)
    assert [problem.person for problem in problems] == ["R Diaz"]


def test_the_bottom_operator_moving_up_is_caught():
    read = check(
        [shift("R Diaz", Post.BOTTOM), shift("T Woo", Post.TOP)],
        [
            card("R Diaz", Endorsement.OPERATOR),
            card("T Woo", Endorsement.OPERATOR),
        ],
    )
    problems = read.problems_of(ProblemKind.UNCARDED)
    assert [problem.person for problem in problems] == ["T Woo"]
    assert problems[0].post is Post.TOP


def test_a_card_that_does_not_cover_the_machine_says_so():
    read = check(
        [shift("R Diaz", Post.BOTTOM), shift("T Woo", Post.TOP)],
        [
            card("R Diaz", classes=frozenset({LiftClass.FIXED_CHAIR})),
            card("T Woo", Endorsement.LEAD_OPERATOR),
        ],
    )
    problems = read.problems_of(ProblemKind.WRONG_CLASS)
    assert [problem.person for problem in problems] == ["R Diaz"]


def test_a_card_that_has_run_out_says_that_instead():
    read = check(
        [shift("R Diaz", Post.BOTTOM), shift("T Woo", Post.TOP)],
        [
            card("R Diaz", expires=date(2024, 12, 1)),
            card("T Woo", Endorsement.LEAD_OPERATOR),
        ],
    )
    problems = read.problems_of(ProblemKind.LAPSED)
    assert [problem.person for problem in problems] == ["R Diaz"]


def test_the_three_card_failures_are_told_apart():
    read = check(
        [
            shift("R Diaz", Post.BOTTOM),
            shift("T Woo", Post.TOP),
        ],
        [
            card("R Diaz", expires=date(2024, 12, 1)),
            card("T Woo", Endorsement.OPERATOR),
        ],
    )
    kinds = {problem.kind for problem in read.problems()}
    assert ProblemKind.LAPSED in kinds
    assert ProblemKind.UNCARDED in kinds


def test_only_a_person_problem_carries_a_name():
    read = check([shift("R Diaz", Post.BOTTOM)], [card("R Diaz")])
    for problem in read.problems():
        if problem.kind.is_about_a_person:
            assert problem.person is not None
        else:
            assert problem.person is None


def test_the_people_who_need_sorting_out_are_listed_once_each():
    read = check(
        [shift("R Diaz", Post.BOTTOM), shift("T Woo", Post.TOP)],
        [],
    )
    assert read.people_to_fix() == ("R Diaz", "T Woo")


def test_a_substitute_is_somebody_carded_and_not_already_working():
    read = check(
        [shift("R Diaz", Post.BOTTOM), shift("T Woo", Post.TOP)],
        [
            card("R Diaz", Endorsement.OPERATOR),
            card("T Woo", Endorsement.LEAD_OPERATOR),
            card("J Marsh", Endorsement.LEAD_OPERATOR),
        ],
    )
    top_shift = next(one for one in read.roster.assignments if one.post is Post.TOP)
    assert read.substitutes(top_shift) == ("J Marsh",)


def test_cover_for_an_empty_post_lists_who_is_free():
    read = check(
        [shift("R Diaz", Post.BOTTOM)],
        [
            card("R Diaz", Endorsement.OPERATOR),
            card("J Marsh", Endorsement.LEAD_OPERATOR),
        ],
    )
    assert read.cover_for(Post.TOP, TimeOfDay.at(12)) == ("J Marsh",)


def test_nobody_free_means_an_empty_list_rather_than_an_error():
    read = clean()
    assert read.cover_for(Post.TOP, TimeOfDay.at(12)) == ()


def test_a_problem_prints_the_way_a_supervisor_reads_it():
    read = check(
        [shift("R Diaz", Post.BOTTOM), shift("T Woo", Post.TOP)],
        [card("R Diaz", Endorsement.OPERATOR)],
    )
    printed = str(read.problems_of(ProblemKind.UNCARDED)[0])
    assert printed.endswith("T Woo, no card for the post")


def test_the_check_prints_a_count_when_it_is_not_clear():
    read = check([shift("R Diaz", Post.BOTTOM)], [])
    assert "problems" in str(read)
