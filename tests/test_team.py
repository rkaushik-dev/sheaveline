from datetime import date

import pytest

from hill import john_paul
from sheaveline.evac.profile import EvacuationError, EvacuationProfile
from sheaveline.evac.team import KitItem, RescueKit, RescueTeam, TeamPool
from sheaveline.line.catenary import SagModel
from sheaveline.measures.length import Length
from sheaveline.measures.load import Load, LoadPerFoot

ON = date(2025, 1, 15)
SAG = SagModel(LoadPerFoot.from_pounds_per_foot("6.20"), Load.from_pounds(34500))


def kit(ladder_feet=24, rope_feet=120, seats=2, harnesses=3, radios=1):
    return RescueKit(
        ladder=None if ladder_feet is None else Length.from_feet(ladder_feet),
        rope=None if rope_feet is None else Length.from_feet(rope_feet),
        seats=seats,
        harnesses=harnesses,
        radios=radios,
    )


def team(
    name="alpha", members=("R Diaz", "T Woo", "J Marsh"), staged=0, until=date(2025, 12, 1), **kw
):
    return RescueTeam.of(name, members, kit(**kw), Length.from_feet(staged), until)


def stops():
    walk = EvacuationProfile(john_paul(), SAG, Length.from_feet(2))
    return list(walk.stops_at_offset(Length.from_feet(0)))


def lowest_stop():
    return min(stops(), key=lambda stop: stop.height.mils)


def highest_stop():
    return max(stops(), key=lambda stop: stop.height.mils)


def test_a_ladder_reaches_less_than_its_length():
    assert kit(ladder_feet=24, rope_feet=None).ladder_reach() == Length.from_feet(20)
    assert kit(ladder_feet=None, rope_feet=None).ladder_reach().is_zero()
    assert kit(ladder_feet=3, rope_feet=None).ladder_reach().is_zero()


def test_the_reach_is_the_longer_of_the_two():
    assert kit(ladder_feet=24, rope_feet=120).reach() == Length.from_feet(120)
    assert kit(ladder_feet=60, rope_feet=None).reach() == Length.from_feet(56)


def test_a_kit_with_no_length_on_a_piece_is_refused():
    with pytest.raises(EvacuationError):
        RescueKit(ladder=Length.from_feet(0))
    with pytest.raises(EvacuationError):
        RescueKit(rope=Length.from_feet(0))
    with pytest.raises(EvacuationError):
        RescueKit(seats=-1)


def test_a_kit_says_whether_the_ladder_alone_will_do():
    small = kit(ladder_feet=24, rope_feet=120)
    assert not small.needs_a_rope_for(Length.from_feet(18))
    assert small.needs_a_rope_for(Length.from_feet(40))


def test_a_kit_is_complete_only_with_a_harness_each_and_a_radio():
    assert kit(harnesses=3, radios=1, seats=1).is_complete(3)
    assert not kit(harnesses=2, radios=1, seats=1).is_complete(3)
    assert not kit(harnesses=3, radios=0, seats=1).is_complete(3)
    assert not kit(harnesses=3, radios=1, seats=0).is_complete(3)


def test_a_kit_reports_what_it_carries():
    full = kit()
    assert full.carries(KitItem.LADDER)
    assert full.carries(KitItem.ROPE_SYSTEM)
    assert full.carries(KitItem.RADIO)
    assert not kit(ladder_feet=None).carries(KitItem.LADDER)
    assert KitItem.LADDER.reaches
    assert not KitItem.RADIO.reaches


def test_a_kit_prints_its_reach():
    assert str(kit(ladder_feet=24, rope_feet=120, seats=2)) == "reach 120 ft, 2 seats"


def test_a_team_needs_a_name_and_two_people():
    with pytest.raises(EvacuationError):
        team(name="  ")
    with pytest.raises(EvacuationError):
        team(members=("R Diaz",))
    with pytest.raises(EvacuationError):
        team(members=("R Diaz", "R Diaz"))


def test_a_team_is_current_until_its_training_lapses():
    assert team(until=date(2025, 12, 1)).is_current(ON)
    assert not team(until=date(2024, 12, 1)).is_current(ON)


def test_a_team_with_a_short_kit_is_not_equipped():
    assert team().is_equipped()
    assert not team(harnesses=1).is_equipped()


def test_a_team_can_take_a_carrier_it_can_reach():
    low = lowest_stop()
    assert team().can_reach(low)
    assert team().may_work(low, ON)


def test_a_team_that_cannot_reach_says_so_and_says_why():
    high = highest_stop()
    short = team(ladder_feet=16, rope_feet=20)
    assert not short.can_reach(high)
    reasons = short.refusals(high, ON)
    assert any("reaches" in reason for reason in reasons)


def test_a_lapsed_team_is_refused_however_good_its_kit_is():
    low = lowest_stop()
    lapsed = team(until=date(2024, 12, 1))
    assert not lapsed.may_work(low, ON)
    assert any("training lapsed" in reason for reason in lapsed.refusals(low, ON))


def test_a_team_short_of_kit_is_refused_however_current_it_is():
    low = lowest_stop()
    thin = team(harnesses=1)
    assert not thin.may_work(low, ON)
    assert any("kit is short" in reason for reason in thin.refusals(low, ON))


def test_a_team_that_can_do_the_job_has_nothing_against_it():
    assert team().refusals(lowest_stop(), ON) == ()


def test_a_team_reports_how_far_it_is_from_a_station():
    staged = team(staged=2400)
    assert staged.distance_to(Length.from_feet(3000)) == Length.from_feet(600)
    assert staged.distance_to(Length.from_feet(1800)) == Length.from_feet(600)


def test_a_team_prints_its_size_and_its_reach():
    assert str(team()) == "alpha, 3 up, reach 120 ft, 2 seats"


def test_a_pool_needs_teams_and_unique_names():
    with pytest.raises(EvacuationError):
        TeamPool.of([])
    with pytest.raises(EvacuationError):
        TeamPool.of([team(name="alpha"), team(name="alpha")])


def test_a_pool_separates_the_current_teams_from_the_lapsed_ones():
    pool = TeamPool.of(
        [
            team(name="alpha"),
            team(name="bravo", until=date(2024, 6, 1)),
        ]
    )
    assert len(pool) == 2
    assert [t.name for t in pool.current(ON)] == ["alpha"]
    assert [t.name for t in pool.lapsed(ON)] == ["bravo"]
    assert pool.people(ON) == 3


def test_the_nearest_able_team_is_the_one_that_gets_the_job():
    low = lowest_stop()
    far = team(name="base", staged=0)
    near = team(name="summit", staged=4800)
    pool = TeamPool.of([far, near])
    chosen = pool.nearest_able(low, ON)
    assert chosen.distance_to(low.station) <= far.distance_to(low.station)


def test_a_carrier_nobody_can_reach_is_an_error_rather_than_a_guess():
    high = highest_stop()
    pool = TeamPool.of([team(ladder_feet=12, rope_feet=None)])
    assert pool.able(high, ON) == ()
    with pytest.raises(EvacuationError):
        pool.nearest_able(high, ON)


def test_the_pool_reports_its_deepest_reach():
    pool = TeamPool.of([team(name="a", rope_feet=80), team(name="b", rope_feet=160)])
    assert pool.deepest_reach() == Length.from_feet(160)
