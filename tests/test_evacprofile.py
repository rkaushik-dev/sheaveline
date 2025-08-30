import pytest

from hill import john_paul
from sheaveline.evac.profile import EvacuationError, EvacuationProfile, Side
from sheaveline.line.catenary import SagModel
from sheaveline.measures.length import Length
from sheaveline.measures.load import Load, LoadPerFoot

SAG = SagModel(LoadPerFoot.from_pounds_per_foot("6.20"), Load.from_pounds(34500))


def profile(snow_feet=2):
    return EvacuationProfile(john_paul(), SAG, Length.from_feet(snow_feet))


def test_the_loop_is_twice_the_line():
    walk = profile()
    assert walk.loop_length() == walk.slope_length().scaled(2)


def test_the_spacing_is_the_loop_over_the_carriers():
    walk = profile()
    assert walk.spacing().mils == walk.loop_length().mils // 84


def test_a_snow_allowance_is_never_negative():
    with pytest.raises(EvacuationError):
        EvacuationProfile(john_paul(), SAG, Length.from_feet(-1))


def test_a_distance_along_the_rope_lands_in_a_span():
    walk = profile()
    span, station = walk.station_at(Length.from_feet(0))
    assert span.index == 1
    assert station == Length.from_feet(0)
    span, station = walk.station_at(walk.slope_length())
    assert span.index == 8
    assert station == Length.from_feet(4800)


def test_a_distance_in_the_middle_lands_where_it_should():
    walk = profile()
    span, station = walk.station_at(Length.from_feet(2000))
    assert 1 < span.index < 8
    assert Length.from_feet(1500) < station < Length.from_feet(2200)


def test_a_distance_off_the_line_is_refused():
    walk = profile()
    with pytest.raises(EvacuationError):
        walk.station_at(Length.from_feet(-1))
    with pytest.raises(EvacuationError):
        walk.station_at(Length.from_feet(9000))


def test_every_carrier_gets_exactly_one_stop_whatever_the_offset():
    walk = profile()
    for feet in (0, 30, 60, 90, 119):
        assert walk.count_at(Length.from_feet(feet)) == 84


def test_carriers_come_out_on_both_sides():
    walk = profile()
    stops = list(walk.stops_at_offset(Length.from_feet(0)))
    sides = {stop.side for stop in stops}
    assert sides == {Side.UP, Side.DOWN}


def test_an_offset_outside_one_spacing_is_refused():
    walk = profile()
    with pytest.raises(EvacuationError):
        list(walk.stops_at_offset(walk.spacing()))
    with pytest.raises(EvacuationError):
        list(walk.stops_at_offset(Length.from_feet(-1)))


def test_a_carrier_hangs_below_the_rope_by_its_hanger():
    walk = profile()
    stop = next(iter(walk.stops_at_offset(Length.from_feet(60))))
    assert stop.seat_height < stop.rope_elevation
    assert stop.rope_elevation - stop.seat_height == Length.from_feet_inches(6, 4)


def test_deeper_snow_lowers_every_carrier_height():
    thin = profile(snow_feet=0)
    deep = thin.with_snow(Length.from_feet(8))
    offset = Length.from_feet(60)
    thin_first = next(iter(thin.stops_at_offset(offset)))
    deep_first = next(iter(deep.stops_at_offset(offset)))
    assert thin_first.height - deep_first.height == Length.from_feet(8)


def test_the_highest_carrier_is_found():
    walk = profile()
    offset = Length.from_feet(0)
    highest = walk.highest(offset)
    assert all(stop.height <= highest.height for stop in walk.stops_at_offset(offset))


def test_the_carriers_above_a_height_are_listed():
    walk = profile()
    offset = Length.from_feet(0)
    limit = Length.from_feet(30)
    above = walk.above(offset, limit)
    assert all(stop.is_above(limit) for stop in above)
    assert len(above) < walk.count_at(offset)


def test_the_worst_offset_is_at_least_as_bad_as_any_sampled_one():
    walk = profile()
    limit = Length.from_feet(30)
    worst = walk.worst_offset(limit)
    count = len(walk.above(worst, limit))
    assert count >= len(walk.above(Length.from_feet(0), limit))


def test_a_stop_prints_where_it_is_and_how_high():
    walk = profile()
    stop = walk.highest(Length.from_feet(0))
    printed = str(stop)
    assert printed.startswith("carrier ")
    assert "ft up" in printed


def test_a_line_with_no_carriers_has_nothing_to_evacuate():
    lift = john_paul()
    empty = EvacuationProfile(lift, SAG)
    assert empty.spacing().mils > 0
