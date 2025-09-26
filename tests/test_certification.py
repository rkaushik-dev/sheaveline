from datetime import date

import pytest

from sheaveline.ops.shift import Post
from sheaveline.people.certification import (
    Certificate,
    Endorsement,
    PeopleError,
    PersonnelFile,
)
from sheaveline.plant.classes import LiftClass

ON = date(2025, 1, 15)
QUAD = LiftClass.DETACHABLE_CHAIR
TRIPLE = LiftClass.FIXED_CHAIR


def card(
    person="R Diaz",
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


def file(*cards):
    return PersonnelFile.of(cards or [card()])


def test_a_card_names_a_person_and_an_issuer_and_runs_forwards():
    with pytest.raises(PeopleError):
        card(person="  ")
    with pytest.raises(PeopleError):
        Certificate("R Diaz", Endorsement.OPERATOR, date(2024, 1, 1), date(2025, 1, 1), " ")
    with pytest.raises(PeopleError):
        card(issued=date(2025, 1, 1), expires=date(2024, 1, 1))
    with pytest.raises(PeopleError):
        card(classes=frozenset())


def test_a_card_is_current_between_its_dates():
    held = card(issued=date(2024, 11, 1), expires=date(2025, 11, 1))
    assert held.is_current(ON)
    assert not held.is_current(date(2024, 10, 1))
    assert not held.is_current(date(2025, 12, 1))
    assert held.days_left(ON) == 290


def test_an_operator_card_covers_the_bottom_but_not_the_top():
    held = card(endorsement=Endorsement.OPERATOR)
    assert held.covers_post(Post.BOTTOM)
    assert held.covers_post(Post.MID)
    assert not held.covers_post(Post.TOP)
    assert not held.covers_post(Post.MECHANIC)


def test_a_lead_card_covers_the_top_and_everything_under_it():
    held = card(endorsement=Endorsement.LEAD_OPERATOR)
    assert held.covers_post(Post.TOP)
    assert held.covers_post(Post.BOTTOM)
    assert not held.covers_post(Post.MECHANIC)


def test_a_mechanic_card_covers_only_the_mechanic_post():
    held = card(endorsement=Endorsement.MECHANIC)
    assert held.covers_post(Post.MECHANIC)
    assert not held.covers_post(Post.BOTTOM)


def test_a_card_can_be_limited_to_certain_machines():
    limited = card(classes=frozenset({TRIPLE}))
    assert limited.covers_class(TRIPLE)
    assert not limited.covers_class(QUAD)
    assert limited.allows(Post.BOTTOM, TRIPLE, ON)
    assert not limited.allows(Post.BOTTOM, QUAD, ON)


def test_all_three_questions_have_to_pass_at_once():
    lapsed = card(issued=date(2023, 1, 1), expires=date(2024, 1, 1))
    assert not lapsed.allows(Post.BOTTOM, QUAD, ON)
    wrong_post = card(endorsement=Endorsement.OPERATOR)
    assert not wrong_post.allows(Post.TOP, QUAD, ON)
    right = card(endorsement=Endorsement.LEAD_OPERATOR)
    assert right.allows(Post.TOP, QUAD, ON)


def test_a_renewal_starts_where_the_old_card_ended():
    held = card(expires=date(2025, 11, 1))
    renewed = held.renewed_to(date(2026, 11, 1), "Utah tramway board")
    assert renewed.issued == date(2025, 11, 1)
    assert renewed.expires == date(2026, 11, 1)
    assert renewed.endorsement is held.endorsement
    with pytest.raises(PeopleError):
        held.renewed_to(date(2025, 6, 1), "Utah tramway board")


def test_a_card_prints_its_expiry():
    assert str(card()) == "R Diaz, operator, to 2025-11-01"


def test_the_file_lists_its_people_once_each():
    held = file(
        card(person="R Diaz"),
        card(person="R Diaz", endorsement=Endorsement.LEAD_OPERATOR),
        card(person="T Woo"),
    )
    assert held.people() == ("R Diaz", "T Woo")
    assert len(held.for_person("R Diaz")) == 2
    assert len(held) == 3


def test_the_file_answers_whether_somebody_may_hold_a_post():
    held = file(
        card(person="R Diaz", endorsement=Endorsement.OPERATOR),
        card(person="T Woo", endorsement=Endorsement.LEAD_OPERATOR),
    )
    assert held.may_hold("R Diaz", Post.BOTTOM, QUAD, ON)
    assert not held.may_hold("R Diaz", Post.TOP, QUAD, ON)
    assert held.may_hold("T Woo", Post.TOP, QUAD, ON)
    assert not held.may_hold("J Marsh", Post.BOTTOM, QUAD, ON)


def test_the_people_qualified_for_a_post_are_listed():
    held = file(
        card(person="R Diaz", endorsement=Endorsement.OPERATOR),
        card(person="T Woo", endorsement=Endorsement.LEAD_OPERATOR),
        card(person="J Marsh", endorsement=Endorsement.MECHANIC),
    )
    assert held.qualified_for(Post.BOTTOM, QUAD, ON) == ("R Diaz", "T Woo")
    assert held.qualified_for(Post.TOP, QUAD, ON) == ("T Woo",)
    assert held.qualified_for(Post.MECHANIC, QUAD, ON) == ("J Marsh",)


def test_a_class_restriction_narrows_the_qualified_list():
    held = file(
        card(person="R Diaz", classes=frozenset({TRIPLE})),
        card(person="T Woo"),
    )
    assert held.qualified_for(Post.BOTTOM, TRIPLE, ON) == ("R Diaz", "T Woo")
    assert held.qualified_for(Post.BOTTOM, QUAD, ON) == ("T Woo",)


def test_the_cards_about_to_run_out_are_listed():
    held = file(
        card(person="R Diaz", expires=date(2025, 2, 1)),
        card(person="T Woo", expires=date(2025, 11, 1)),
    )
    soon = held.expiring_within(ON, 30)
    assert [c.person for c in soon] == ["R Diaz"]
    with pytest.raises(PeopleError):
        held.expiring_within(ON, -1)


def test_the_cards_that_have_already_run_out_are_listed_separately():
    held = file(
        card(person="R Diaz", issued=date(2023, 1, 1), expires=date(2024, 1, 1)),
        card(person="T Woo"),
    )
    assert [c.person for c in held.lapsed(ON)] == ["R Diaz"]
    assert held.current_for("T Woo", ON)
    assert held.current_for("R Diaz", ON) == ()


def test_adding_a_card_makes_a_new_file():
    held = file()
    longer = held.with_certificate(card(person="T Woo"))
    assert len(held) == 1
    assert len(longer) == 2
