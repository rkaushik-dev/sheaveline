from datetime import date

import pytest

from hill import JOHN_PAUL, WILDCAT, john_paul, strawberry, wildcat
from sheaveline.measures.speed import Speed
from sheaveline.ops.downhill import (
    DownhillAuthorisation,
    DownhillCheck,
    DownhillPolicy,
    DownhillReason,
)
from sheaveline.ops.preop import OperationsError
from sheaveline.ops.shift import Post
from sheaveline.plant.classes import LiftClass

ON = date(2025, 1, 15)


def authorisation(
    lift=JOHN_PAUL,
    reason=DownhillReason.SIGHTSEEING,
    granted=date(2025, 1, 1),
    expires=date(2025, 4, 20),
    escort=None,
):
    return DownhillAuthorisation(
        lift=lift,
        reason=reason,
        granted=granted,
        granted_by="H Okafor",
        expires=expires,
        escort=escort,
    )


def check(lift=None, authorisations=None, posts=(Post.TOP,), fpm=400):
    return DownhillCheck.of(
        lift or john_paul(),
        ON,
        authorisations if authorisations is not None else [authorisation()],
        posts,
        None if fpm is None else Speed.from_feet_per_minute(fpm),
    )


def test_every_class_has_a_downhill_policy():
    for lift_class in LiftClass:
        assert isinstance(DownhillPolicy.for_class(lift_class), DownhillPolicy)


def test_a_surface_tow_does_not_carry_downhill_at_all():
    policy = DownhillPolicy.for_class(LiftClass.SURFACE_TOW)
    assert not policy.allowed
    assert policy.carriers_at_once == 0


def test_a_detachable_carries_more_at_once_than_a_fixed_grip():
    quad = DownhillPolicy.for_class(LiftClass.DETACHABLE_CHAIR)
    triple = DownhillPolicy.for_class(LiftClass.FIXED_CHAIR)
    assert quad.carriers_at_once > triple.carriers_at_once
    assert quad.speed is not None
    assert triple.speed is not None
    assert quad.speed > triple.speed


def test_every_aerial_class_wants_somebody_at_the_top():
    for lift_class in LiftClass:
        policy = DownhillPolicy.for_class(lift_class)
        if policy.allowed:
            assert policy.attendant_at_top


def test_an_authorisation_is_signed_and_runs_forwards():
    with pytest.raises(OperationsError):
        DownhillAuthorisation(JOHN_PAUL, DownhillReason.STAFF, ON, "  ", ON)
    with pytest.raises(OperationsError):
        authorisation(granted=date(2025, 4, 1), expires=date(2025, 1, 1))


def test_a_ride_that_needs_an_escort_carries_one():
    with pytest.raises(OperationsError):
        authorisation(reason=DownhillReason.DISABILITY)
    assert authorisation(reason=DownhillReason.DISABILITY, escort="K Ostler").escort


def test_a_ride_that_does_not_need_an_escort_must_not_name_one():
    with pytest.raises(OperationsError):
        authorisation(reason=DownhillReason.SIGHTSEEING, escort="K Ostler")


def test_the_reasons_know_which_of_them_are_public():
    assert DownhillReason.SIGHTSEEING.is_public
    assert not DownhillReason.STAFF.is_public
    assert DownhillReason.EQUIPMENT.needs_an_escort
    assert not DownhillReason.BEGINNER.needs_an_escort


def test_an_authorisation_is_current_between_its_dates():
    held = authorisation(granted=date(2025, 1, 1), expires=date(2025, 4, 20))
    assert held.is_current(ON)
    assert not held.is_current(date(2024, 12, 1))
    assert held.days_left(ON) == 95


def test_an_authorisation_prints_its_expiry():
    assert str(authorisation()) == "JP downhill for sightseeing, to 2025-04-20"


def test_a_machine_with_everything_in_place_may_carry_down():
    read = check()
    assert read.may_run()
    assert read.refusals() == ()
    assert read.carriers_at_once() == 4
    assert "may carry down" in str(read)


def test_a_tow_is_refused_before_anything_else_is_looked_at():
    read = check(lift=wildcat(), authorisations=[authorisation(lift=WILDCAT)], posts=())
    assert not read.may_run()
    assert read.refusals() == ("a surface lift does not carry downhill",)
    assert read.carriers_at_once() == 0


def test_nothing_authorised_today_stops_it():
    read = check(authorisations=[])
    assert "nothing authorised for today" in read.refusals()


def test_an_authorisation_for_another_machine_does_not_count():
    read = check(authorisations=[authorisation(lift=WILDCAT)])
    assert "nothing authorised for today" in read.refusals()


def test_a_lapsed_authorisation_does_not_count():
    lapsed = authorisation(granted=date(2024, 11, 1), expires=date(2024, 12, 1))
    read = check(authorisations=[lapsed])
    assert "nothing authorised for today" in read.refusals()


def test_nobody_at_the_top_stops_it():
    read = check(posts=(Post.BOTTOM,))
    assert "nobody at the top terminal" in read.refusals()


def test_a_rope_running_too_fast_stops_it_and_says_by_how_much():
    read = check(fpm=1000)
    assert any("above the" in one for one in read.refusals())
    assert "1000 fpm" in read.refusals()[0]


def test_no_rope_speed_set_stops_it_too():
    read = check(fpm=None)
    assert "no rope speed set" in read.refusals()


def test_a_fixed_grip_is_held_to_a_slower_rope_than_a_detachable():
    at_four_hundred = Speed.from_feet_per_minute(400)
    quad = check(fpm=400)
    triple = DownhillCheck.of(
        strawberry(),
        ON,
        [authorisation(lift=strawberry().code)],
        (Post.TOP,),
        at_four_hundred,
    )
    assert quad.may_run()
    assert not triple.may_run()


def test_several_things_can_be_wrong_at_once():
    read = check(authorisations=[], posts=(), fpm=1000)
    assert len(read.refusals()) == 3


def test_a_refused_machine_carries_nobody():
    assert check(authorisations=[]).carriers_at_once() == 0
    assert "may not carry down" in str(check(authorisations=[]))
