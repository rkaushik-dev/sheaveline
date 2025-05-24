from decimal import Decimal

import pytest

from sheaveline.line.catenary import LineError, SagModel, line_weight
from sheaveline.measures.length import Length
from sheaveline.measures.load import Load, LoadPerFoot
from sheaveline.plant.span import Span, Support

ROPE = LoadPerFoot.from_pounds_per_foot("3.12")


def model(pounds_per_foot="3.12", tension=34500):
    return SagModel(
        LoadPerFoot.from_pounds_per_foot(pounds_per_foot),
        Load.from_pounds(tension),
    )


def span(lower_station=0, lower_elev=6400, upper_station=1000, upper_elev=6400):
    return Span(
        1,
        Support.terminal("a", Length.from_feet(lower_station), Length.from_feet(lower_elev)),
        Support.terminal("b", Length.from_feet(upper_station), Length.from_feet(upper_elev)),
    )


def test_a_model_needs_weight_and_tension():
    with pytest.raises(LineError):
        SagModel(LoadPerFoot.from_pounds_per_foot(0), Load.from_pounds(34500))
    with pytest.raises(LineError):
        SagModel(ROPE, Load.from_pounds(0))


def test_the_midspan_sag_is_the_standard_parabola():
    # 3.12 lb/ft over 1000 ft at 34,500 lb is 3.12 * 1e6 / 276000.
    assert model().sag_over(Length.from_feet(1000)).feet.quantize(Decimal("0.01")) == Decimal(
        "11.30"
    )


def test_sag_grows_with_the_square_of_the_span():
    short = model().sag_over(Length.from_feet(500))
    long = model().sag_over(Length.from_feet(1000))
    assert (long.ratio(short)).quantize(Decimal("0.01")) == 4


def test_more_tension_pulls_the_rope_up():
    slack = model(tension=20000).sag_over(Length.from_feet(1000))
    tight = model(tension=40000).sag_over(Length.from_feet(1000))
    assert tight < slack


def test_more_weight_pulls_the_rope_down():
    light = model(pounds_per_foot="3.12").sag_over(Length.from_feet(1000))
    heavy = model(pounds_per_foot="6.24").sag_over(Length.from_feet(1000))
    assert heavy.mils == light.mils * 2


def test_there_is_no_sag_at_the_supports_and_most_at_the_middle():
    horizontal = Length.from_feet(1000)
    assert model().sag_at(horizontal, Length.from_feet(0)).is_zero()
    assert model().sag_at(horizontal, Length.from_feet(1000)).is_zero()
    assert model().sag_at(horizontal, Length.from_feet(500)) == model().sag_over(horizontal)


def test_the_sag_shape_is_symmetric_about_the_middle():
    horizontal = Length.from_feet(1000)
    left = model().sag_at(horizontal, Length.from_feet(250))
    right = model().sag_at(horizontal, Length.from_feet(750))
    assert left == right


def test_a_point_outside_the_span_has_no_sag_to_report():
    with pytest.raises(LineError):
        model().sag_at(Length.from_feet(1000), Length.from_feet(1200))


def test_the_rope_sits_below_the_chord_by_the_sag():
    level = span()
    at_mid = model().rope_elevation(level, Length.from_feet(500))
    assert at_mid < Length.from_feet(6400)
    assert Length.from_feet(6400) - at_mid == model().sag_over(Length.from_feet(1000))


def test_the_rope_touches_the_chord_at_both_supports():
    level = span()
    assert model().rope_elevation(level, Length.from_feet(0)) == Length.from_feet(6400)
    assert model().rope_elevation(level, Length.from_feet(1000)) == Length.from_feet(6400)


def test_on_a_level_span_the_low_point_is_the_middle():
    assert model().low_point(span()) == Length.from_feet(500)


def test_on_a_climbing_span_the_low_point_moves_downhill():
    climbing = span(upper_elev=6440)
    assert model().low_point(climbing) < Length.from_feet(500)


def test_on_a_dropping_span_the_low_point_moves_uphill():
    dropping = span(upper_elev=6360)
    assert model().low_point(dropping) > Length.from_feet(500)


def test_on_a_span_steeper_than_the_sag_the_low_point_is_the_bottom_support():
    steep = span(upper_elev=6800)
    assert model().low_point(steep) == Length.from_feet(0)


def test_on_a_span_dropping_harder_than_the_sag_the_low_point_is_the_top():
    steep = span(upper_elev=6000)
    assert model().low_point(steep) == Length.from_feet(1000)


def test_the_tension_that_would_hold_a_wanted_sag():
    wanted = Length.from_feet(10)
    needed = model().tension_for_sag(Length.from_feet(1000), wanted)
    assert needed == Load.from_pounds(39000)
    lifted = model().with_tension(needed)
    assert lifted.sag_over(Length.from_feet(1000)) == wanted


def test_a_wanted_sag_of_nothing_has_no_answer():
    with pytest.raises(LineError):
        model().tension_for_sag(Length.from_feet(1000), Length.from_feet(0))


def test_the_sag_ratio_is_how_a_drawing_quotes_it():
    ratio = model().sag_ratio(Length.from_feet(1000))
    assert ratio.quantize(Decimal("0.0001")) == Decimal("0.0113")
    with pytest.raises(LineError):
        model().sag_ratio(Length.from_feet(0))


def test_swapping_the_weight_leaves_the_tension_alone():
    heavier = model().with_weight(LoadPerFoot.from_pounds_per_foot("4.00"))
    assert heavier.tension == model().tension
    assert heavier.weight != model().weight


def test_the_carriers_add_their_share_to_the_weight_on_the_line():
    loaded = line_weight(ROPE, Load.from_pounds(1090), Length.from_feet(109))
    assert loaded.pounds_per_foot == Decimal("13.12")
    assert loaded > ROPE


def test_carriers_with_no_spacing_have_no_share():
    with pytest.raises(LineError):
        line_weight(ROPE, Load.from_pounds(1090), Length.from_feet(0))


def test_a_model_prints_its_two_numbers():
    assert str(model()) == "3.12 lb/ft at 34,500 lb"
