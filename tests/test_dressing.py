from datetime import date
from decimal import Decimal

import pytest

from hill import john_paul, strawberry
from sheaveline.measures.length import Length
from sheaveline.ropework.dressing import (
    DRESSING_INTERVAL,
    WORST_PENALTY,
    Dressing,
    DressingLog,
)
from sheaveline.ropework.trend import forecast
from sheaveline.ropework.wear import DiameterReading, RopeworkError, WearSurvey

ROPE = john_paul().rope
SERIAL = str(ROPE.serial)
ON = date(2025, 1, 15)
MILE = Length.from_feet(5280)


def applied(miles=0, on=ON, product="Fuchs Ceplattyn", serial=SERIAL):
    return Dressing(
        rope_serial=serial,
        on=on,
        travel_at=MILE.scaled(Decimal(miles)),
        product=product,
        by="R Halvorsen",
    )


def log(*dressings, interval=DRESSING_INTERVAL):
    return DressingLog.of(ROPE, dressings, interval)


def miles(count):
    return MILE.scaled(Decimal(count))


def test_a_dressing_names_what_went_on_and_who_put_it_on():
    with pytest.raises(RopeworkError, match="names what went on"):
        applied(product="  ")
    with pytest.raises(RopeworkError, match="negative"):
        Dressing(SERIAL, ON, Length(-1), "Ceplattyn", "R Halvorsen")


def test_a_dressing_says_how_far_the_rope_had_run():
    assert applied(miles=12_500).miles_at() == Decimal(12_500)
    assert "12500 miles" in str(applied(miles=12_500))


def test_a_dressing_from_another_rope_does_not_belong_in_this_log():
    other = str(strawberry().rope.serial)
    with pytest.raises(RopeworkError, match=other):
        log(applied(serial=other))


def test_a_rope_cannot_be_dressed_at_a_distance_it_has_not_run():
    with pytest.raises(RopeworkError, match="has not run"):
        DressingLog(ROPE, (applied(miles=20_000), applied(miles=5_000)))


def test_the_applications_come_back_in_the_order_they_went_on():
    made = log(applied(miles=20_000), applied(miles=5_000))
    assert [one.miles_at() for one in made.dressings] == [Decimal(5_000), Decimal(20_000)]
    assert made.last().miles_at() == Decimal(20_000)


def test_a_rope_nobody_has_dressed_is_measured_from_the_day_it_went_on():
    made = log()
    assert made.last() is None
    assert made.travel_since(miles(10_000)) == miles(10_000)
    assert "never been dressed" in str(made)


def test_the_interval_is_measured_in_miles_and_not_in_weeks():
    assert Length.from_feet(5280 * 25_000) == DRESSING_INTERVAL
    made = log(applied(miles=0))
    assert not made.is_due(miles(24_000))
    assert made.is_due(miles(25_000))


def test_what_is_left_before_it_is_due():
    made = log(applied(miles=10_000))
    assert made.travel_until_due(miles(20_000)) == miles(15_000)
    assert made.travel_until_due(miles(60_000)) == Length(0)


def test_a_meter_that_has_gone_backwards_since_the_last_dressing():
    made = log(applied(miles=20_000))
    with pytest.raises(RopeworkError, match="gone backwards"):
        made.travel_since(miles(10_000))


def test_a_rope_inside_its_interval_carries_no_penalty():
    made = log(applied(miles=0))
    assert made.intervals_overdue(miles(24_000)) == 0
    assert made.wear_penalty(miles(24_000)) == Decimal(1)


def test_a_rope_one_interval_late_wears_faster():
    made = log(applied(miles=0))
    penalty = made.wear_penalty(miles(50_000))
    assert penalty > 1
    assert penalty == Decimal("1.35")


def test_the_penalty_stops_rather_than_being_invented_past_the_data():
    made = log(applied(miles=0))
    assert made.wear_penalty(miles(1_000_000)) == WORST_PENALTY


def test_a_shorter_interval_makes_the_same_mileage_later():
    steady = miles(30_000)
    assert log(applied(miles=0)).wear_penalty(steady) > 1
    assert log(applied(miles=0), interval=miles(40_000)).wear_penalty(steady) == 1


def test_an_interval_that_is_not_a_distance():
    with pytest.raises(RopeworkError, match="is a distance"):
        log(interval=Length(0))


def reading(inches):
    return DiameterReading(
        station=Length.from_feet(1200),
        across_crowns=Length.from_inches(inches),
        across_valleys=Length.from_inches(inches),
    )


def walks():
    return [
        WearSurvey.of(ROPE, date(2023, 1, 15), "R Halvorsen", [reading("1.378")]),
        WearSurvey.of(ROPE, date(2025, 1, 15), "R Halvorsen", [reading("1.330")]),
    ]


def test_a_dry_rope_comes_off_sooner_than_the_file_alone_would_say():
    looked_after = forecast(ROPE, walks(), ON)
    neglected = forecast(ROPE, walks(), ON, penalty=Decimal("1.35"))
    assert neglected.days_left() < looked_after.days_left()
    assert neglected.comes_off() < looked_after.comes_off()
    assert neglected.is_penalised()
    assert not looked_after.is_penalised()


def test_looking_after_a_rope_does_not_make_it_wear_more_slowly():
    with pytest.raises(RopeworkError, match="more slowly"):
        forecast(ROPE, walks(), ON, penalty=Decimal("0.5"))


def test_the_penalty_from_the_log_is_the_one_the_forecast_takes():
    made = log(applied(miles=0))
    penalty = made.wear_penalty(miles(50_000))
    assert forecast(ROPE, walks(), ON, penalty=penalty).penalty == Decimal("1.35")
