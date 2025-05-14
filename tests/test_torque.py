from decimal import Decimal

import pytest

from sheaveline.errors import ParseError, RangeError, UnitMismatch
from sheaveline.measures.torque import Torque


def test_inch_pounds_are_the_base_unit():
    assert Torque.from_pound_feet(900).inch_pounds == 10800
    assert Torque.from_inch_pounds(450).pound_feet == Decimal("37.5")


def test_newton_metres_read_back_close_enough_for_a_manual():
    assert Torque.from_newton_metres(100).inch_pounds == 885
    assert Torque.from_inch_pounds(885).newton_metres.quantize(Decimal("0.1")) == 100


@pytest.mark.parametrize(
    ("text", "inch_pounds"),
    [
        ("450 in lb", 450),
        ("450 inch pounds", 450),
        ("900 lb ft", 10800),
        ("900 ft lb", 10800),
        ("900 pound feet", 10800),
        ("100 Nm", 885),
        ("100 newton metres", 885),
    ],
)
def test_parse_reads_the_faces_the_manuals_use(text, inch_pounds):
    assert Torque.parse(text).inch_pounds == inch_pounds


@pytest.mark.parametrize("text", ["", "tight", "450", "450 kg", "1.2.3 in lb"])
def test_parse_refuses_what_is_not_a_torque(text):
    with pytest.raises(ParseError):
        Torque.parse(text)


def test_a_torque_is_never_negative_and_never_a_float():
    with pytest.raises(RangeError):
        Torque(-1)
    with pytest.raises(UnitMismatch):
        Torque(4.5)
    with pytest.raises(UnitMismatch):
        Torque(True)


def test_a_reading_inside_a_band_of_the_set_figure():
    target = Torque.from_inch_pounds(450)
    assert Torque.from_inch_pounds(430).within(target, "0.10")
    assert Torque.from_inch_pounds(495).within(target, "0.10")
    assert not Torque.from_inch_pounds(390).within(target, "0.10")


def test_a_tighter_band_rejects_what_a_looser_one_accepts():
    target = Torque.from_inch_pounds(450)
    reading = Torque.from_inch_pounds(415)
    assert reading.within(target, "0.10")
    assert not reading.within(target, "0.05")


def test_subtraction_gives_the_gap_either_way_round():
    assert Torque.from_inch_pounds(450) - Torque.from_inch_pounds(400) == Torque(50)
    assert Torque.from_inch_pounds(400) - Torque.from_inch_pounds(450) == Torque(50)


def test_ordering_and_equality():
    light = Torque.from_inch_pounds(400)
    heavy = Torque.from_inch_pounds(450)
    assert light < heavy
    assert heavy > light
    assert light <= Torque(400)
    assert light >= Torque(400)
    assert light != heavy
    assert light != 400
    assert len({light, Torque(400)}) == 1


def test_a_slack_bolt_reads_zero():
    assert Torque(0).is_slack()
    assert not Torque(1).is_slack()


def test_scaling_a_torque():
    assert Torque.from_inch_pounds(450).scaled("0.5") == Torque(225)


def test_formatting_in_both_faces():
    assert Torque.from_pound_feet(900).format_pound_feet() == "900 lb ft"
    assert Torque.from_inch_pounds(450).format_inch_pounds() == "450 in lb"
    assert str(Torque.from_inch_pounds(450)) == "450 in lb"
    assert repr(Torque.from_inch_pounds(450)) == "Torque(450)"
