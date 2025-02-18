from decimal import Decimal

import pytest

from sheaveline.errors import ParseError, RangeError, UnitMismatch
from sheaveline.measures.angle import ZERO_ANGLE, Angle, Bearing
from sheaveline.measures.length import Length


def test_millidegrees_are_the_base_unit():
    assert Angle.from_degrees(3).millidegrees == 3000
    assert Angle.from_degrees("2.75").millidegrees == 2750
    assert Angle.from_millidegrees(1500).degrees == Decimal("1.5")


def test_a_slope_reads_as_a_grade_or_as_degrees():
    slope = Angle.from_percent_grade(50)
    assert slope.millidegrees == 26565
    assert slope.percent_grade.quantize(Decimal("0.01")) == Decimal("50.00")


def test_rise_over_run_is_how_a_survey_hands_it_over():
    slope = Angle.from_rise_run(Length.from_feet(500), Length.from_feet(2000))
    assert slope.percent_grade.quantize(Decimal("0.1")) == Decimal("25.0")
    assert slope.is_uphill()


def test_a_downhill_run_comes_back_negative():
    slope = Angle.from_rise_run(Length.from_feet(-120), Length.from_feet(800))
    assert slope.millidegrees < 0
    assert not slope.is_uphill()


def test_a_zero_run_is_refused():
    with pytest.raises(RangeError):
        Angle.from_rise_run(Length.from_feet(10), Length.from_feet(0))


def test_radians_round_trip():
    for degrees in (0, 15, 26.57, 45, 89):
        angle = Angle.from_degrees(str(degrees))
        assert Angle.from_radians(angle.radians) == angle


@pytest.mark.parametrize(
    ("text", "millidegrees"),
    [
        ("3", 3000),
        ("3 deg", 3000),
        ("2.75 degrees", 2750),
        ("-1.5 d", -1500),
        ("  12  ", 12000),
    ],
)
def test_parse_reads_an_angle(text, millidegrees):
    assert Angle.parse(text).millidegrees == millidegrees


def test_parse_reads_a_grade_when_it_sees_a_percent():
    assert Angle.parse("25%") == Angle.from_percent_grade(25)


@pytest.mark.parametrize("text", ["", "steep", "3 rad", "1.2.3"])
def test_parse_refuses_what_is_not_an_angle(text):
    with pytest.raises(ParseError):
        Angle.parse(text)


def test_arithmetic_and_ordering():
    small = Angle.from_degrees(2)
    large = Angle.from_degrees(9)
    assert small + large == Angle.from_degrees(11)
    assert large - small == Angle.from_degrees(7)
    assert -small == Angle.from_degrees(-2)
    assert abs(Angle.from_degrees(-2)) == small
    assert small < large
    assert large > small
    assert small <= Angle.from_degrees(2)
    assert small >= Angle.from_degrees(2)
    assert small != large
    assert small != 2
    assert len({small, Angle.from_degrees(2)}) == 1


def test_a_flat_angle_says_so():
    assert ZERO_ANGLE.is_flat()
    assert not Angle.from_degrees(1).is_flat()
    assert ZERO_ANGLE.percent_grade == 0


def test_the_constructor_refuses_a_float():
    with pytest.raises(UnitMismatch):
        Angle(1.5)
    with pytest.raises(UnitMismatch):
        Bearing(2.5)


def test_formatting():
    assert Angle.from_degrees("2.755").format_degrees() == "2.76 deg"
    assert Angle.from_percent_grade(25).format_grade() == "25.0%"
    assert str(Angle.from_degrees(3)) == "3.00 deg"
    assert repr(Angle.from_degrees(3)) == "Angle.from_millidegrees(3000)"


def test_a_bearing_wraps_into_one_circle():
    assert Bearing.from_degrees(370) == Bearing.from_degrees(10)
    assert Bearing.from_degrees(-10) == Bearing.from_degrees(350)
    assert Bearing.from_degrees(360) == Bearing.from_degrees(0)


def test_compass_points_read_both_ways():
    assert Bearing.from_point("SW").degrees == 225
    assert Bearing.from_point("nnw").degrees == Decimal("337.5")
    assert Bearing.from_degrees(226).nearest_point() == "SW"
    assert Bearing.from_degrees(0).nearest_point() == "N"
    assert Bearing.from_degrees(359).nearest_point() == "N"


def test_a_bearing_parses_from_either_face():
    assert Bearing.parse("270") == Bearing.from_point("W")
    assert Bearing.parse(" wsw ") == Bearing.from_degrees("247.5")


@pytest.mark.parametrize("text", ["", "NNNE", "up the hill"])
def test_a_bearing_refuses_what_is_not_one(text):
    with pytest.raises(ParseError):
        Bearing.parse(text)


def test_the_reciprocal_is_the_other_way_along_the_line():
    assert Bearing.from_degrees(30).reciprocal() == Bearing.from_degrees(210)
    assert Bearing.from_degrees(210).reciprocal() == Bearing.from_degrees(30)


def test_difference_takes_the_short_way_round():
    assert Bearing.from_degrees(10).difference(Bearing.from_degrees(350)) == Angle.from_degrees(-20)
    assert Bearing.from_degrees(350).difference(Bearing.from_degrees(10)) == Angle.from_degrees(20)
    assert Bearing.from_degrees(0).difference(Bearing.from_degrees(180)) == Angle.from_degrees(180)


def test_separation_ignores_which_way_round():
    across = Bearing.from_degrees(90)
    assert across.separation(Bearing.from_degrees(70)) == Angle.from_degrees(20)
    assert across.separation(Bearing.from_degrees(110)) == Angle.from_degrees(20)


def test_a_bearing_prints_with_its_point():
    assert str(Bearing.from_degrees(225)) == "225 deg (SW)"
    assert repr(Bearing.from_degrees(225)) == "Bearing(225000)"
    assert len({Bearing.from_degrees(225), Bearing.from_point("SW")}) == 1
    assert Bearing.from_degrees(225) != 225
