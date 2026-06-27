from datetime import date

import pytest

from hill import john_paul, strawberry
from sheaveline.measures.length import Length
from sheaveline.ropework.trend import (
    LEAD_TIME_DAYS,
    Retirement,
    WearTrend,
    forecast,
    orderable,
)
from sheaveline.ropework.wear import DiameterReading, RopeworkError, WearSurvey

ROPE = john_paul().rope
ON = date(2025, 9, 1)


def reading(inches, station_feet=1200):
    return DiameterReading(
        station=Length.from_feet(station_feet),
        across_crowns=Length.from_inches(inches),
        across_valleys=Length.from_inches(inches),
    )


def walk(taken, inches, rope=None):
    return WearSurvey.of(rope or ROPE, taken, "R Halvorsen", [reading(inches)])


def file_of(*pairs, rope=None):
    return [walk(taken, inches, rope) for taken, inches in pairs]


STEADY = (
    (date(2023, 1, 15), "1.378"),
    (date(2024, 1, 15), "1.354"),
    (date(2025, 1, 15), "1.330"),
)


def test_a_trend_is_drawn_through_the_worst_station_on_every_walk():
    made = WearTrend.of(ROPE, file_of(*STEADY))
    assert made.walks() == 3
    assert made.first().taken == date(2023, 1, 15)
    assert made.last().smallest == Length.from_inches("1.330")


def test_the_walks_come_back_in_order_whichever_way_they_are_given():
    made = WearTrend.of(ROPE, file_of(STEADY[2], STEADY[0], STEADY[1]))
    assert [one.taken for one in made.points] == [one for one, _ in STEADY]


def test_one_walk_is_not_a_line():
    with pytest.raises(RopeworkError, match="needs 2 walks"):
        WearTrend.of(ROPE, file_of(STEADY[0]))


def test_two_walks_on_the_same_day_cannot_both_be_the_trend():
    with pytest.raises(RopeworkError, match="same day"):
        WearTrend.of(ROPE, file_of(STEADY[0], (date(2023, 1, 15), "1.370")))


def test_a_walk_on_another_rope_does_not_belong_in_this_file():
    other = strawberry().rope
    with pytest.raises(RopeworkError, match=str(other.serial)):
        WearTrend.of(ROPE, file_of(*STEADY[:2], rope=other))


def test_the_wear_and_what_is_left_are_measured_from_the_file():
    made = WearTrend.of(ROPE, file_of(*STEADY))
    assert made.worn() == Length.from_inches("0.048")
    assert made.left() == Length.from_inches("0.048")
    assert made.days_covered() == 731


def test_the_rate_is_worked_out_a_day():
    made = WearTrend.of(ROPE, file_of(*STEADY))
    assert round(float(made.mils_a_day()), 4) == round(48 / 731, 4)


def test_a_rope_that_has_not_measurably_worn_carries_no_line():
    flat = WearTrend.of(ROPE, file_of((date(2024, 1, 15), "1.378"), (date(2025, 1, 15), "1.378")))
    assert not flat.is_wearing()
    made = Retirement(flat, ON)
    assert made.days_left() is None
    assert made.comes_off() is None
    assert made.order_by() is None
    assert made.survives(date(2026, 4, 1)) is None
    assert "not wearing measurably" in str(made)


def test_the_projection_reaches_the_condemning_diameter():
    made = forecast(ROPE, file_of(*STEADY), ON)
    assert made.days_left() == 731
    assert made.comes_off() == date(2027, 1, 16)


def test_a_rope_already_at_the_limit_comes_off_today():
    worn = file_of((date(2024, 1, 15), "1.320"), (date(2025, 1, 15), "1.280"))
    made = forecast(ROPE, worn, ON)
    assert made.days_left() == 0
    assert made.comes_off() == date(2025, 1, 15)


def test_the_order_goes_in_a_lead_time_before_the_rope_comes_off():
    made = forecast(ROPE, file_of(*STEADY), ON)
    assert (made.comes_off() - made.order_by()).days == LEAD_TIME_DAYS
    assert not made.is_overdue_to_order()


def test_a_rope_wearing_fast_wants_ordering_already():
    fast = file_of((date(2025, 1, 15), "1.340"), (date(2025, 6, 15), "1.300"))
    made = forecast(ROPE, fast, ON)
    assert made.is_overdue_to_order()
    assert made.comes_off() < date(2026, 1, 1)


def test_a_shorter_lead_time_moves_the_order_date_and_nothing_else():
    slow = forecast(ROPE, file_of(*STEADY), ON, lead_time=30)
    long = forecast(ROPE, file_of(*STEADY), ON, lead_time=300)
    assert slow.comes_off() == long.comes_off()
    assert slow.order_by() > long.order_by()


def test_a_lead_time_nobody_has():
    with pytest.raises(RopeworkError, match="not negative"):
        forecast(ROPE, file_of(*STEADY), ON, lead_time=-1)


def test_whether_the_rope_reaches_the_end_of_a_season():
    made = forecast(ROPE, file_of(*STEADY), ON)
    assert made.survives(date(2026, 4, 15))
    assert not made.survives(date(2028, 4, 15))


def test_how_much_of_a_line_the_file_carries_is_said_in_one_word():
    two = forecast(ROPE, file_of(*STEADY[:2]), ON)
    assert two.confidence() == "two points"
    assert forecast(ROPE, file_of(*STEADY), ON).confidence() == "provisional"
    many = file_of(
        (date(2021, 1, 15), "1.378"),
        (date(2022, 1, 15), "1.366"),
        (date(2023, 1, 15), "1.354"),
        (date(2024, 1, 15), "1.342"),
        (date(2025, 1, 15), "1.330"),
    )
    assert forecast(ROPE, many, ON).confidence() == "settled"


def test_a_settled_file_needs_a_year_behind_it_as_well_as_five_walks():
    tight = file_of(
        (date(2025, 1, 1), "1.378"),
        (date(2025, 2, 1), "1.374"),
        (date(2025, 3, 1), "1.370"),
        (date(2025, 4, 1), "1.366"),
        (date(2025, 5, 1), "1.362"),
    )
    assert forecast(ROPE, tight, ON).confidence() == "provisional"


def test_the_ropes_that_want_ordering_come_back_soonest_first():
    fast = file_of((date(2025, 1, 15), "1.340"), (date(2025, 6, 15), "1.300"))
    slower = file_of((date(2025, 1, 15), "1.350"), (date(2025, 6, 15), "1.320"))
    found = orderable([(ROPE, file_of(*STEADY)), (ROPE, fast), (ROPE, slower)], ON)
    assert len(found) == 2
    assert found[0].comes_off() < found[1].comes_off()


def test_a_rope_with_one_walk_is_left_out_rather_than_guessed_at():
    assert orderable([(ROPE, file_of(STEADY[0]))], ON) == ()


def test_a_forecast_prints_what_the_purchase_order_needs():
    printed = str(forecast(ROPE, file_of(*STEADY), ON))
    assert str(ROPE.serial) in printed
    assert "order by" in printed
