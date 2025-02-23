from decimal import Decimal

import pytest

from sheaveline.errors import ParseError, UnitMismatch
from sheaveline.measures.temperature import (
    FREEZING,
    Temperature,
    TemperatureDelta,
)


def test_tenths_of_a_degree_are_the_base_unit():
    assert Temperature.from_degrees_f(-12).decidegrees_f == -120
    assert Temperature.from_degrees_f("18.4").decidegrees_f == 184
    assert Temperature(184).degrees_f == Decimal("18.4")


def test_celsius_is_the_face_the_drive_manual_uses():
    assert Temperature.from_degrees_c(0) == FREEZING
    assert Temperature.from_degrees_c(-40) == Temperature.from_degrees_f(-40)
    assert Temperature.from_degrees_f(212).degrees_c == 100


def test_celsius_round_trips_within_a_tenth():
    for reading in (-24, -12, 0, 18, 45):
        temperature = Temperature.from_degrees_f(reading)
        again = Temperature.from_degrees_c(temperature.degrees_c)
        assert abs(again.decidegrees_f - temperature.decidegrees_f) <= 1


@pytest.mark.parametrize(
    ("text", "decidegrees"),
    [
        ("-12", -120),
        ("-12 F", -120),
        ("18F", 180),
        ("18 fahrenheit", 180),
        ("0 C", 320),
        ("-24 degC", -112),
        ("  5.5  ", 55),
    ],
)
def test_parse_reads_what_the_station_sends(text, decidegrees):
    assert Temperature.parse(text).decidegrees_f == decidegrees


@pytest.mark.parametrize("text", ["", "cold", "12 K", "1.2.3"])
def test_parse_refuses_what_is_not_a_temperature(text):
    with pytest.raises(ParseError):
        Temperature.parse(text)


def test_two_readings_subtract_to_a_difference():
    warm = Temperature.from_degrees_f(20)
    cold = Temperature.from_degrees_f(-4)
    assert warm - cold == TemperatureDelta.from_degrees_f(24)
    assert cold - warm == TemperatureDelta.from_degrees_f(-24)


def test_two_readings_do_not_add():
    with pytest.raises(UnitMismatch):
        Temperature.from_degrees_f(10) + Temperature.from_degrees_f(10)


def test_a_reading_plus_a_difference_is_a_reading():
    start = Temperature.from_degrees_f(10)
    drop = TemperatureDelta.from_degrees_f(15)
    assert start.minus(drop) == Temperature.from_degrees_f(-5)
    assert start.plus(drop) == Temperature.from_degrees_f(25)


def test_a_difference_in_celsius_carries_no_offset():
    assert TemperatureDelta.from_degrees_f(9).degrees_c == 5
    assert TemperatureDelta.from_degrees_c(5) == TemperatureDelta.from_degrees_f(9)


def test_differences_add_and_negate():
    a = TemperatureDelta.from_degrees_f(5)
    b = TemperatureDelta.from_degrees_f(7)
    assert a + b == TemperatureDelta.from_degrees_f(12)
    assert -a == TemperatureDelta.from_degrees_f(-5)
    assert abs(-a) == a
    assert a < b
    assert b > a
    assert a <= TemperatureDelta.from_degrees_f(5)
    assert a >= TemperatureDelta.from_degrees_f(5)
    assert a != b
    assert len({a, TemperatureDelta(50)}) == 1


def test_freezing_is_thirty_two_and_answers_for_itself():
    assert FREEZING.degrees_f == 32
    assert FREEZING.is_freezing()
    assert Temperature.from_degrees_f(31).is_freezing()
    assert not Temperature.from_degrees_f(33).is_freezing()


def test_ordering_and_equality():
    cold = Temperature.from_degrees_f(-15)
    mild = Temperature.from_degrees_f(28)
    assert cold < mild
    assert mild > cold
    assert cold <= Temperature.from_degrees_f(-15)
    assert cold >= Temperature.from_degrees_f(-15)
    assert cold != mild
    assert cold != -15
    assert len({cold, Temperature(-150)}) == 1


def test_the_constructors_refuse_a_float():
    with pytest.raises(UnitMismatch):
        Temperature(1.5)
    with pytest.raises(UnitMismatch):
        TemperatureDelta(2.5)


def test_formatting():
    assert Temperature.from_degrees_f("-12.4").format_f() == "-12.4 F"
    assert FREEZING.format_c() == "0.0 C"
    assert str(Temperature.from_degrees_f(18)) == "18.0 F"
    assert repr(Temperature.from_degrees_f(18)) == "Temperature(180)"
    assert str(TemperatureDelta.from_degrees_f("2.5")) == "2.5 F"
    assert repr(TemperatureDelta.from_degrees_f(3)) == "TemperatureDelta(30)"
