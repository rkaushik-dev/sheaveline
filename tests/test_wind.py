from decimal import Decimal

import pytest

from sheaveline.errors import ParseError
from sheaveline.measures.angle import Bearing
from sheaveline.measures.speed import Speed
from sheaveline.measures.temperature import Temperature
from sheaveline.measures.wind import CALM, Wind

# The Snowbasin quad runs almost due east up the fall line.
LINE = Bearing.from_degrees(90)


def test_miles_an_hour_is_now_a_face_on_speed():
    assert Speed.from_miles_per_hour(60).miles_per_hour == 60
    assert Speed.parse("35 mph") == Speed.from_miles_per_hour(35)
    assert Speed.parse("35 miles per hour").format_mph() == "35 mph"
    assert Speed.from_miles_per_hour(60).feet_per_minute == 5280


def test_a_wind_carries_a_direction_and_a_gust():
    wind = Wind.from_mph(24, "SW", 38)
    assert wind.speed == Speed.from_miles_per_hour(24)
    assert wind.bearing == Bearing.from_point("SW")
    assert wind.peak == Speed.from_miles_per_hour(38)


def test_without_a_gust_the_peak_is_the_sustained_speed():
    wind = Wind.from_mph(18, "W")
    assert wind.gust is None
    assert wind.peak == wind.speed


def test_a_gust_below_the_sustained_speed_is_refused():
    with pytest.raises(ParseError):
        Wind.from_mph(30, "W", 20)


@pytest.mark.parametrize(
    ("text", "mph", "point", "gust"),
    [
        ("SW 24G38", 24, "SW", 38),
        ("sw 24 g 38", 24, "SW", 38),
        ("W 18", 18, "W", None),
        ("225 24", 24, "SW", None),
        ("270 41G55", 41, "W", 55),
    ],
)
def test_parse_reads_the_station_shorthand(text, mph, point, gust):
    wind = Wind.parse(text)
    assert wind.speed == Speed.from_miles_per_hour(mph)
    assert wind.bearing == Bearing.from_point(point)
    assert wind.gust == (None if gust is None else Speed.from_miles_per_hour(gust))


@pytest.mark.parametrize("text", ["", "windy", "SW", "24", "NNNE 20"])
def test_parse_refuses_what_is_not_an_observation(text):
    with pytest.raises(ParseError):
        Wind.parse(text)


def test_the_wind_blows_the_other_way_from_where_it_comes_from():
    assert Wind.from_mph(20, "W").blowing_toward() == Bearing.from_point("E")


def test_a_wind_straight_up_the_line_has_no_cross_component():
    wind = Wind.from_mph(30, "W")
    assert wind.across(LINE).miles_per_hour.quantize(Decimal("0.1")) == 0
    assert wind.along(LINE).miles_per_hour.quantize(Decimal("0.1")) == 30
    assert not wind.is_headwind(LINE)


def test_a_wind_off_the_top_is_a_headwind():
    wind = Wind.from_mph(30, "E")
    assert wind.is_headwind(LINE)
    assert wind.along(LINE).miles_per_hour.quantize(Decimal("0.1")) == 30


def test_a_wind_square_across_the_line_is_all_cross_component():
    wind = Wind.from_mph(30, "S")
    assert wind.across(LINE).miles_per_hour.quantize(Decimal("0.1")) == 30
    assert wind.along(LINE).miles_per_hour.quantize(Decimal("0.1")) == 0


def test_a_wind_on_the_quarter_splits_between_the_two():
    wind = Wind.from_mph(40, "SW")
    across = wind.across(LINE).miles_per_hour
    along = wind.along(LINE).miles_per_hour
    assert across.quantize(Decimal("0.1")) == Decimal("28.3")
    assert along.quantize(Decimal("0.1")) == Decimal("28.3")


def test_the_cross_component_is_read_off_the_gust():
    wind = Wind.from_mph(20, "S", 45)
    assert wind.across(LINE).miles_per_hour.quantize(Decimal("0.1")) == 20
    assert wind.peak_across(LINE).miles_per_hour.quantize(Decimal("0.1")) == 45


def test_wind_chill_follows_the_weather_service_formula():
    wind = Wind.from_mph(20, "W")
    assert wind.chill(Temperature.from_degrees_f(5)).degrees_f.quantize(Decimal(1)) == -15
    assert wind.chill(Temperature.from_degrees_f(35)).degrees_f.quantize(Decimal(1)) == 24


def test_wind_chill_stands_off_outside_the_range_it_is_defined_for():
    warm = Temperature.from_degrees_f(58)
    assert Wind.from_mph(20, "W").chill(warm) == warm
    cold = Temperature.from_degrees_f(5)
    assert Wind.from_mph(2, "W").chill(cold) == cold
    assert CALM.chill(cold) == cold


def test_calm_has_nothing_in_any_direction():
    assert CALM.across(LINE) == Speed(0)
    assert CALM.along(LINE) == Speed(0)
    assert CALM.peak == Speed(0)


def test_an_observation_prints_the_way_it_arrived():
    assert str(Wind.from_mph(24, "SW", 38)) == "SW 24G38"
    assert str(Wind.from_mph(18, "W")) == "W 18"
    assert Wind.parse("SW 24G38").format_shorthand() == "SW 24G38"
