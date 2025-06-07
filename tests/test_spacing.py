from decimal import Decimal

import pytest

from hill import john_paul, strawberry
from sheaveline.capacity.spacing import CapacityError, LineSpacing, SpacingSurvey
from sheaveline.measures.duration import Duration
from sheaveline.measures.length import Length
from sheaveline.measures.speed import Speed
from sheaveline.plant.classes import LiftClass


def spacing(loop_feet=10132, carriers=84, fpm=1000):
    return LineSpacing(
        loop=Length.from_feet(loop_feet),
        carriers=carriers,
        speed=Speed.from_feet_per_minute(fpm),
    )


def test_spacing_is_the_loop_divided_by_the_carriers():
    assert spacing(loop_feet=10000, carriers=100).spacing() == Length.from_feet(100)


def test_the_interval_is_the_spacing_at_the_rope_speed():
    assert spacing(loop_feet=10000, carriers=100, fpm=1000).interval() == Duration.from_seconds(6)


def test_a_spacing_built_from_a_lift_reads_its_own_numbers():
    lift = john_paul()
    built = LineSpacing.of(lift)
    assert built.carriers == 84
    assert built.speed == lift.design_speed
    assert built.loop == lift.expected_rope_length()


def test_a_spacing_can_be_asked_at_another_speed():
    lift = john_paul()
    slow = LineSpacing.of(lift, Speed.from_feet_per_minute(500))
    assert slow.interval() > LineSpacing.of(lift).interval()


def test_the_line_checks_its_own_numbers():
    with pytest.raises(CapacityError):
        spacing(loop_feet=0)
    with pytest.raises(CapacityError):
        spacing(carriers=0)
    with pytest.raises(CapacityError):
        LineSpacing(Length.from_feet(10000), 84, Speed.from_feet_per_minute(0))


def test_taking_carriers_off_opens_the_interval_up():
    full = spacing(carriers=84)
    thinned = full.with_carriers(60)
    assert thinned.interval() > full.interval()
    assert thinned.spacing() > full.spacing()


def test_slowing_the_rope_opens_the_interval_up_as_well():
    fast = spacing(fpm=1000)
    slow = fast.at_speed(Speed.from_feet_per_minute(500))
    assert slow.interval().seconds == fast.interval().seconds * 2
    assert slow.spacing() == fast.spacing()


def test_the_two_dials_can_be_turned_the_other_way_round():
    line = spacing(loop_feet=10000, carriers=100)
    assert line.carriers_for(Length.from_feet(200)) == 50
    assert line.spacing_for(Duration.from_seconds(6)) == Length.from_feet(100)


def test_a_spacing_of_nothing_has_no_carrier_count():
    with pytest.raises(CapacityError):
        spacing().carriers_for(Length.from_feet(0))
    with pytest.raises(CapacityError):
        spacing().spacing_for(Duration.from_seconds(0))


def test_a_detachable_will_run_a_shorter_interval_than_a_fixed_grip():
    line = spacing(loop_feet=10000, carriers=100, fpm=1000)
    assert line.interval() == Duration.from_seconds(6)
    assert line.meets(LiftClass.DETACHABLE_CHAIR)
    assert not line.meets(LiftClass.FIXED_CHAIR)


def test_the_shortfall_says_how_far_under_the_floor_it_is():
    line = spacing(loop_feet=10000, carriers=100, fpm=1000)
    assert line.shortfall(LiftClass.FIXED_CHAIR) == Duration.from_seconds(3)
    assert line.shortfall(LiftClass.DETACHABLE_CHAIR) == Duration.from_seconds(0)


def test_the_fullest_the_line_may_be_run_for_its_class():
    line = spacing(loop_feet=10000, carriers=100, fpm=1000)
    assert line.most_carriers_for(LiftClass.DETACHABLE_CHAIR) == 100
    assert line.most_carriers_for(LiftClass.FIXED_CHAIR) == 66


def test_a_tram_is_held_to_an_interval_nothing_else_would_tolerate():
    line = spacing(loop_feet=10000, carriers=2, fpm=1000)
    assert line.meets(LiftClass.AERIAL_TRAM)


def test_the_quad_on_the_hill_clears_its_own_floor():
    line = LineSpacing.of(john_paul())
    assert line.meets(LiftClass.DETACHABLE_CHAIR)
    assert line.interval().seconds >= 6


def test_the_triple_on_the_hill_clears_its_own_floor():
    line = LineSpacing.of(strawberry())
    assert line.meets(LiftClass.FIXED_CHAIR)


def test_a_line_prints_the_three_numbers_the_crew_reads():
    assert str(spacing(loop_feet=10000, carriers=100)) == "100 carriers at 100 ft, 0:06 apart"


def survey(*gaps_feet, nominal=100):
    return SpacingSurvey.of(Length.from_feet(nominal), [Length.from_feet(gap) for gap in gaps_feet])


def test_a_survey_reports_its_widest_and_tightest_gaps():
    walked = survey(98, 103, 99, 101)
    assert walked.widest() == Length.from_feet(103)
    assert walked.tightest() == Length.from_feet(98)
    assert walked.spread() == Length.from_feet(5)


def test_an_even_line_is_inside_the_tolerance():
    assert survey(98, 103, 99, 101).is_even()
    assert survey(98, 103, 99, 101).out_of_tolerance() == ()


def test_a_line_with_one_bunched_pair_is_not_even():
    walked = survey(98, 103, 60, 140)
    assert not walked.is_even()
    assert len(walked.out_of_tolerance()) == 2
    assert walked.worst_error() == Decimal("0.4")


def test_a_tighter_tolerance_catches_more():
    walked = survey(98, 103, 99, 101)
    assert walked.is_even("0.05")
    assert not walked.is_even("0.01")


def test_a_survey_checks_its_own_numbers():
    with pytest.raises(CapacityError):
        SpacingSurvey.of(Length.from_feet(0), [Length.from_feet(100)])
    with pytest.raises(CapacityError):
        SpacingSurvey.of(Length.from_feet(100), [])
    with pytest.raises(CapacityError):
        survey(100, 0)
