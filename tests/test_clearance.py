import pytest

from sheaveline.line.catenary import LineError, SagModel
from sheaveline.line.clearance import (
    ClearanceCheck,
    GroundUse,
    GroundUseMap,
    UseSegment,
)
from sheaveline.measures.length import Length
from sheaveline.measures.load import Load, LoadPerFoot
from sheaveline.plant.profile import LineProfile
from sheaveline.plant.span import Span, Support

FLAT = LineProfile.from_survey([(0, 6400), (500, 6400), (1000, 6400)])
SAG = SagModel(LoadPerFoot.from_pounds_per_foot("3.12"), Load.from_pounds(34500))


def span(upper_elev=6440):
    return Span(
        1,
        Support.terminal("a", Length.from_feet(0), Length.from_feet(6430)),
        Support.terminal("b", Length.from_feet(1000), Length.from_feet(upper_elev)),
    )


def check(uses=None, snow_feet=0, drop_feet=7, upper_elev=6440):
    return ClearanceCheck(
        span=span(upper_elev),
        ground=FLAT,
        sag=SAG,
        carrier_drop=Length.from_feet(drop_feet),
        snow=Length.from_feet(snow_feet),
        uses=uses or GroundUseMap(),
    )


def segment(start, end, use):
    return UseSegment(Length.from_feet(start), Length.from_feet(end), use)


def test_each_ground_use_carries_its_own_minimum():
    assert GroundUse.OPEN.minimum == Length.from_feet(12)
    assert GroundUse.ROAD.minimum == Length.from_feet(24)
    assert GroundUse.WATER.minimum == Length.from_feet(30)
    assert GroundUse.ROAD.is_trafficked
    assert not GroundUse.OPEN.is_trafficked


def test_ground_with_nothing_recorded_is_open_ground():
    assert GroundUseMap().use_at(Length.from_feet(400)) is GroundUse.OPEN
    assert GroundUseMap().minimum_at(Length.from_feet(400)) == Length.from_feet(12)


def test_a_segment_sets_the_use_over_its_stretch():
    uses = GroundUseMap.of([segment(300, 600, GroundUse.TRAIL)])
    assert uses.use_at(Length.from_feet(200)) is GroundUse.OPEN
    assert uses.use_at(Length.from_feet(450)) is GroundUse.TRAIL
    assert uses.use_at(Length.from_feet(700)) is GroundUse.OPEN


def test_overlapping_uses_take_the_more_demanding_one():
    uses = GroundUseMap.of([segment(300, 600, GroundUse.TRAIL), segment(400, 500, GroundUse.ROAD)])
    assert uses.use_at(Length.from_feet(450)) is GroundUse.ROAD
    assert uses.use_at(Length.from_feet(350)) is GroundUse.TRAIL


def test_a_segment_has_to_run_up_the_line():
    with pytest.raises(LineError):
        segment(600, 300, GroundUse.TRAIL)


def test_the_carrier_bottom_is_the_rope_less_the_hanger():
    reading = check().reading_at(Length.from_feet(500))
    rope = SAG.rope_elevation(span(), Length.from_feet(500))
    assert reading.carrier_bottom == rope - Length.from_feet(7)


def test_the_available_clearance_takes_off_the_ground_and_the_snow():
    dry = check(snow_feet=0).reading_at(Length.from_feet(500))
    deep = check(snow_feet=6).reading_at(Length.from_feet(500))
    assert dry.available - deep.available == Length.from_feet(6)


def test_a_span_with_room_passes_and_the_same_span_in_deep_snow_does_not():
    assert check(snow_feet=0).passes()
    assert not check(snow_feet=8).passes()


def test_the_margin_is_what_is_there_less_what_is_wanted():
    reading = check().reading_at(Length.from_feet(0))
    assert reading.available == Length.from_feet(23)
    assert reading.required == Length.from_feet(12)
    assert reading.margin == Length.from_feet(11)
    assert reading.passes()


def test_a_road_under_the_span_can_fail_where_open_ground_passes():
    station = Length.from_feet(500)
    open_ground = check().reading_at(station)
    assert open_ground.passes()
    over_road = check(uses=GroundUseMap.of([segment(400, 600, GroundUse.ROAD)]))
    tight = over_road.reading_at(station)
    assert tight.available == open_ground.available
    assert tight.required == Length.from_feet(24)


def test_the_tightest_margin_is_not_always_the_lowest_rope():
    uses = GroundUseMap.of([segment(0, 100, GroundUse.WATER)])
    walked = check(uses=uses, snow_feet=6)
    # The chord climbs, so the rope bottoms out below midspan.
    assert walked.lowest().station == Length.from_feet(400)
    assert walked.tightest().station != walked.lowest().station


def test_the_failures_are_listed_station_by_station():
    walked = check(snow_feet=8)
    failures = walked.failures()
    assert failures
    assert all(not reading.passes() for reading in failures)
    assert not walked.passes()


def test_a_finer_step_finds_at_least_as_much_as_a_coarse_one():
    walked = check(snow_feet=8)
    coarse = len(walked.failures(Length.from_feet(250)))
    fine = len(walked.failures(Length.from_feet(25)))
    assert fine >= coarse


def test_changing_the_snow_makes_a_new_check():
    dry = check(snow_feet=0)
    deep = dry.with_snow(Length.from_feet(8))
    assert dry.snow.is_zero()
    assert deep.snow == Length.from_feet(8)
    assert deep.span is dry.span


def test_a_check_refuses_a_carrier_above_the_rope_or_negative_snow():
    with pytest.raises(LineError):
        check(drop_feet=-1)
    with pytest.raises(LineError):
        check(snow_feet=-1)


def test_a_reading_prints_both_sides_of_the_comparison():
    reading = check().reading_at(Length.from_feet(0))
    assert str(reading) == "23.0 ft of 12 ft wanted at station 0"


def test_a_climbing_span_is_tightest_near_its_low_end():
    walked = check(upper_elev=6700, snow_feet=8)
    assert walked.tightest().station < Length.from_feet(700)
