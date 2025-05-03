from datetime import date
from decimal import Decimal

import pytest

from sheaveline.measures.length import Length
from sheaveline.measures.load import Load
from sheaveline.measures.temperature import Temperature
from sheaveline.ropework.tension import (
    TensionKind,
    TensionReading,
    TensionSystem,
)
from sheaveline.ropework.wear import RopeworkError


def system(kind=TensionKind.HYDRAULIC, carriage_feet=6, travel_feet=16, pounds=34500):
    return TensionSystem(
        kind=kind,
        nominal=Load.from_pounds(pounds),
        travel=Length.from_feet(travel_feet),
        carriage=Length.from_feet(carriage_feet),
    )


def test_a_counterweight_holds_one_tension_and_a_ram_does_not():
    assert TensionKind.COUNTERWEIGHT.holds_constant
    assert not TensionKind.HYDRAULIC.holds_constant
    assert TensionKind.COUNTERWEIGHT.is_passive
    assert TensionKind.SCREW.is_passive
    assert not TensionKind.HYDRAULIC.is_passive


def test_a_screw_take_up_is_not_allowed_under_passengers():
    assert not TensionKind.SCREW.allowed_on_passenger_lifts
    assert TensionKind.COUNTERWEIGHT.allowed_on_passenger_lifts
    assert TensionKind.HYDRAULIC.allowed_on_passenger_lifts


def test_a_system_checks_its_own_numbers():
    with pytest.raises(RopeworkError):
        system(pounds=0)
    with pytest.raises(RopeworkError):
        system(travel_feet=0)
    with pytest.raises(RopeworkError):
        system(carriage_feet=-1)


def test_the_carriage_reports_how_far_it_has_left_to_go():
    held = system(carriage_feet=6, travel_feet=16)
    assert held.remaining_out() == Length.from_feet(10)
    assert held.remaining_in() == Length.from_feet(6)
    assert held.position_percent() == Decimal("37.5")


def test_a_carriage_inside_its_travel_with_room_at_both_ends():
    assert system(carriage_feet=6).is_within_travel()
    assert system(carriage_feet=6).has_reserve()


def test_a_carriage_within_a_tenth_of_a_stop_has_no_reserve():
    assert not system(carriage_feet=1).has_reserve()
    assert not system(carriage_feet=15).has_reserve()
    assert system(carriage_feet=1).is_within_travel()


def test_a_carriage_past_the_stop_is_out_of_travel():
    assert not system(carriage_feet=18).is_within_travel()


def test_a_shortening_moves_the_carriage_by_half_of_what_came_out():
    moved = system(carriage_feet=6).after_shortening(Length.from_feet(40))
    assert moved.carriage == Length.from_feet(26)
    with pytest.raises(RopeworkError):
        system().after_shortening(Length.from_feet(0))


def test_the_carriage_says_how_many_more_cuts_it_has_room_for():
    held = system(carriage_feet=2, travel_feet=16)
    assert held.shortenings_left(Length.from_feet(8)) == 3
    assert system(carriage_feet=15).shortenings_left(Length.from_feet(8)) == 0
    with pytest.raises(RopeworkError):
        held.shortenings_left(Length.from_feet(0))


def test_a_counterweight_is_held_to_a_tighter_band_than_a_ram():
    weight = system(kind=TensionKind.COUNTERWEIGHT)
    ram = system(kind=TensionKind.HYDRAULIC)
    assert weight.tolerance() == Load.from_pounds(690)
    assert ram.tolerance() == Load.from_pounds(1725)
    assert weight.tolerance() < ram.tolerance()


def test_a_measured_tension_inside_the_band_is_accepted():
    ram = system(kind=TensionKind.HYDRAULIC)
    assert ram.accepts(Load.from_pounds(35000))
    assert ram.accepts(Load.from_pounds(33000))
    assert not ram.accepts(Load.from_pounds(31000))


def test_the_same_reading_can_pass_on_one_system_and_fail_on_another():
    reading = Load.from_pounds(35800)
    assert system(kind=TensionKind.HYDRAULIC).accepts(reading)
    assert not system(kind=TensionKind.COUNTERWEIGHT).accepts(reading)


def test_moving_the_carriage_makes_a_new_record():
    before = system(carriage_feet=6)
    after = before.moved_to(Length.from_feet(9))
    assert before.carriage == Length.from_feet(6)
    assert after.carriage == Length.from_feet(9)
    assert after.expected_tension() == before.expected_tension()


def test_a_system_prints_for_the_morning_sheet():
    printed = str(system(kind=TensionKind.COUNTERWEIGHT, carriage_feet=8))
    assert printed == "counterweight at 34,500 lb, carriage 50% out"


def reading(pounds=34800, carriage_feet=7, taken=date(2025, 1, 15), degrees=18):
    return TensionReading(
        taken=taken,
        carriage=Length.from_feet(carriage_feet),
        measured=Load.from_pounds(pounds),
        temperature=Temperature.from_degrees_f(degrees),
    )


def test_a_reading_checks_its_own_numbers():
    with pytest.raises(RopeworkError):
        reading(pounds=0)
    with pytest.raises(RopeworkError):
        reading(carriage_feet=-2)


def test_a_reading_is_compared_against_the_system_it_came_off():
    held = system(carriage_feet=6)
    assert reading().deviation_from(held) == Load.from_pounds(300)
    assert reading().agrees_with(held)
    assert not reading(pounds=30000).agrees_with(held)


def test_a_reading_says_how_far_the_carriage_has_travelled_since():
    held = system(carriage_feet=6)
    assert reading(carriage_feet=7).carriage_moved(held) == Length.from_feet(1)
    assert reading(carriage_feet=5).carriage_moved(held) == Length.from_feet(-1)


def test_a_reading_prints_short():
    assert str(reading()) == "34,800 lb on 2025-01-15"
