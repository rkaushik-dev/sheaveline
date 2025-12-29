from decimal import Decimal

import pytest

from sheaveline.errors import ParseError, RangeError, UnitMismatch
from sheaveline.measures.length import ZERO_LENGTH, Length


def test_mils_are_the_base_unit():
    assert Length.from_inches(1).mils == 1000
    assert Length.from_feet(1).mils == 12000
    assert Length.from_mils(1378).inches == Decimal("1.378")


def test_feet_and_inches_combine():
    assert Length.from_feet_inches(12, 6).mils == 12 * 12000 + 6000
    assert Length.from_feet_inches(0, 3).inches == Decimal("3")


def test_negative_feet_carry_the_sign_into_the_inches():
    length = Length.from_feet_inches(-4, 6)
    assert length.mils == -(4 * 12000 + 6000)
    feet, inches = length.split_feet_inches()
    assert feet == -4
    assert inches == Decimal("-6")


def test_rope_diameter_keeps_three_places():
    assert Length.from_inches("1.378").mils == 1378
    assert Length.from_inches(Decimal("0.875")).mils == 875


def test_rounding_is_half_away_from_zero():
    assert Length.from_inches("0.0005").mils == 1
    assert Length.from_inches("-0.0005").mils == -1
    assert Length.from_inches("0.00049").mils == 0


@pytest.mark.parametrize(
    ("text", "mils"),
    [
        ("148", 148000),
        ("148 ft", 148 * 12000),
        ("148ft", 148 * 12000),
        ("12'6\"", 12 * 12000 + 6000),
        ("12' 6", 12 * 12000 + 6000),
        ("1.378 in", 1378),
        ("6 feet 3 inches", 6 * 12000 + 3000),
        ("  40'  ", 40 * 12000),
        ("4800.00 ft", 4800 * 12000),
        ("148.5 feet", 148 * 12000 + 6000),
    ],
)
def test_parse_reads_the_shapes_a_drawing_uses(text, mils):
    assert Length.parse(text).mils == mils


@pytest.mark.parametrize(
    "text", ["", "   ", "ft", "twelve feet", "12 ft 6 ft", "1.2.3", "12.5 ft 6 in"]
)
def test_parse_refuses_what_is_not_a_length(text):
    with pytest.raises(ParseError):
        Length.parse(text)


def test_parse_error_says_what_it_wanted():
    with pytest.raises(ParseError) as caught:
        Length.parse("nine")
    assert caught.value.wanted == "a length"
    assert caught.value.text == "nine"


def test_arithmetic_stays_exact():
    a = Length.from_feet(148)
    b = Length.from_feet_inches(3, 6)
    assert (a + b).mils == 148 * 12000 + 42000
    assert (a - b).mils == 148 * 12000 - 42000
    assert (-b).mils == -42000
    assert abs(-b) == b


def test_scaling_rounds_to_the_nearest_mil():
    assert Length.from_inches(1).scaled("0.3333").mils == 333
    assert Length.from_feet(100).scaled(2) == Length.from_feet(200)


def test_ratio_is_dimensionless():
    assert Length.from_feet(200).ratio(Length.from_feet(100)) == Decimal(2)


def test_ratio_refuses_a_zero_divisor():
    with pytest.raises(RangeError):
        Length.from_feet(1).ratio(ZERO_LENGTH)


def test_ordering_and_equality():
    short = Length.from_feet(10)
    long = Length.from_feet(11)
    assert short < long
    assert long > short
    assert short <= Length.from_feet(10)
    assert short >= Length.from_feet(10)
    assert short == Length.from_inches(120)
    assert short != long
    assert len({short, Length.from_inches(120)}) == 1


def test_a_length_never_equals_a_bare_number():
    assert Length.from_inches(1) != 1000


def test_the_constructor_refuses_a_float():
    with pytest.raises(UnitMismatch):
        Length(1.5)
    with pytest.raises(UnitMismatch):
        Length(True)


def test_formatting_reads_like_a_drawing():
    assert Length.from_feet_inches(12, 6).format_feet_inches() == "12' 6\""
    assert Length.from_feet(148).format_feet_inches() == "148' 0\""
    assert Length.from_inches("1.378").format_inches() == '1.378"'
    assert str(Length.from_feet_inches(3, Decimal("1.5"))) == "3' 1.5\""


def test_repr_round_trips():
    length = Length.from_feet_inches(7, 9)
    assert eval(repr(length)) == length  # noqa: S307


def test_zero_is_shared_and_answers_for_itself():
    assert ZERO_LENGTH.is_zero()
    assert not Length.from_mils(1).is_zero()
    assert ZERO_LENGTH.feet == 0
