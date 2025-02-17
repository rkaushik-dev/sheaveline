from decimal import Decimal

import pytest

from sheaveline.errors import ParseError, RangeError, UnitMismatch
from sheaveline.measures.length import Length
from sheaveline.measures.load import (
    PASSENGER_DESIGN_LOAD,
    ZERO_LOAD,
    Load,
    LoadPerFoot,
)


def test_pounds_are_the_base_unit():
    assert Load.from_pounds(34500).pounds == 34500
    assert Load.from_kips("34.5").pounds == 34500
    assert Load.from_tons(2).pounds == 4000


def test_kilograms_read_back_close_enough_for_a_manual():
    load = Load.from_kilograms(1000)
    assert load.pounds == 2205
    assert load.kilograms.quantize(Decimal("0.1")) == Decimal("1000.2")


def test_kips_are_the_face_a_tension_chart_uses():
    assert Load.from_pounds(34500).kips == Decimal("34.5")


@pytest.mark.parametrize(
    ("text", "pounds"),
    [
        ("34500 lb", 34500),
        ("34,500 lb", 34500),
        ("34500lbs", 34500),
        ("170 pounds", 170),
        ("34.5 kip", 34500),
        ("1000 kg", 2205),
        ("2 tons", 4000),
        ("8600 lbf", 8600),
    ],
)
def test_parse_reads_the_shapes_a_spec_sheet_uses(text, pounds):
    assert Load.parse(text).pounds == pounds


@pytest.mark.parametrize("text", ["", "heavy", "34500", "5 stone", "1.2.3 lb"])
def test_parse_refuses_what_is_not_a_load(text):
    with pytest.raises(ParseError):
        Load.parse(text)


def test_a_design_load_is_one_hundred_and_seventy_pounds():
    assert Load.from_pounds(170) == PASSENGER_DESIGN_LOAD
    assert PASSENGER_DESIGN_LOAD.scaled(4) == Load.from_pounds(680)


def test_arithmetic_and_ordering():
    a = Load.from_pounds(12000)
    b = Load.from_pounds(3400)
    assert a + b == Load.from_pounds(15400)
    assert a - b == Load.from_pounds(8600)
    assert -b == Load.from_pounds(-3400)
    assert b < a
    assert a > b
    assert a >= Load.from_pounds(12000)
    assert b <= Load.from_pounds(3400)
    assert a != b
    assert a != 12000
    assert len({a, Load.from_pounds(12000)}) == 1


def test_ratio_is_dimensionless():
    assert Load.from_pounds(30000).ratio(Load.from_pounds(10000)) == 3
    with pytest.raises(RangeError):
        Load.from_pounds(1).ratio(ZERO_LOAD)


def test_the_constructor_refuses_a_float():
    with pytest.raises(UnitMismatch):
        Load(1.5)
    with pytest.raises(UnitMismatch):
        LoadPerFoot(2.9)


def test_spreading_a_weight_over_a_length_gives_a_rope_quote():
    rope = Load.from_pounds(9000).over(Length.from_feet(3000))
    assert rope.pounds_per_foot == 3
    assert rope.millipounds_per_foot == 3000


def test_spreading_over_nothing_is_refused():
    with pytest.raises(RangeError):
        Load.from_pounds(1).over(Length.from_feet(0))


def test_a_run_of_rope_weighs_what_the_quote_says():
    rope = LoadPerFoot.from_pounds_per_foot("3.12")
    assert rope.times(Length.from_feet(1000)) == Load.from_pounds(3120)
    assert rope.times(Length.from_feet_inches(0, 6)) == Load.from_pounds(2)


def test_weight_per_foot_is_never_negative():
    with pytest.raises(RangeError):
        LoadPerFoot(-1)


def test_weight_per_foot_compares_and_prints():
    light = LoadPerFoot.from_pounds_per_foot("1.85")
    heavy = LoadPerFoot.from_pounds_per_foot("3.12")
    assert light < heavy
    assert heavy > light
    assert light != heavy
    assert str(heavy) == "3.12 lb/ft"
    assert repr(heavy) == "LoadPerFoot(3120)"
    assert len({light, LoadPerFoot(1850)}) == 1


def test_formatting_groups_the_thousands():
    assert Load.from_pounds(34500).format_pounds() == "34,500 lb"
    assert str(Load.from_pounds(170)) == "170 lb"
    assert repr(ZERO_LOAD) == "Load.from_pounds(0)"
