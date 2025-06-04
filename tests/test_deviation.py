import pytest

from hill import JOHN_PAUL, hold_down_assembly, support_assembly
from sheaveline.line.catenary import LineError, SagModel
from sheaveline.line.deviation import (
    TowerLoading,
    arrival_slope,
    departure_slope,
    sag_slope,
)
from sheaveline.measures.angle import Angle
from sheaveline.measures.length import Length
from sheaveline.measures.load import Load, LoadPerFoot
from sheaveline.plant.span import Span, Support
from sheaveline.plant.tower import AssemblyKind, Tower

SAG = SagModel(LoadPerFoot.from_pounds_per_foot("3.12"), Load.from_pounds(34500))


def support(label, station, elevation):
    return Support.terminal(label, Length.from_feet(station), Length.from_feet(elevation))


def span(index, lower, upper):
    return Span(index, lower, upper)


def tower(station, elevation, up=None, down=None):
    return Tower(
        ref=JOHN_PAUL.tower(4),
        station=Length.from_feet(station),
        base_elevation=Length.from_feet(elevation - 30),
        height=Length.from_feet(30),
        uphill=up or support_assembly(4, "0.7"),
        downhill=down or support_assembly(4, "0.7"),
    )


def loading(lower_elev=6400, mid_elev=6500, upper_elev=6560, up=None, down=None):
    below = span(1, support("a", 0, lower_elev), support("t", 1000, mid_elev))
    above = span(2, support("t", 1000, mid_elev), support("b", 2000, upper_elev))
    return TowerLoading(tower(1000, mid_elev, up, down), below, above, SAG)


def test_the_sag_tips_the_rope_at_both_ends_of_a_span():
    level = span(1, support("a", 0, 6400), support("b", 1000, 6400))
    tip = sag_slope(SAG, level)
    assert tip.millidegrees > 0
    assert arrival_slope(SAG, level) == tip
    assert departure_slope(SAG, level) == -tip


def test_the_rope_arrives_steeper_than_the_chord_and_leaves_flatter():
    climbing = span(1, support("a", 0, 6400), support("b", 1000, 6500))
    assert arrival_slope(SAG, climbing) > climbing.slope()
    assert departure_slope(SAG, climbing) < climbing.slope()


def test_a_tower_where_the_slope_eases_presses_the_rope_down():
    held = loading(lower_elev=6400, mid_elev=6500, upper_elev=6560)
    assert held.deviation().millidegrees > 0
    assert held.presses_down()
    assert held.assembly_kind_seen() is AssemblyKind.SUPPORT


def test_a_tower_where_the_slope_steepens_lifts_the_rope_off():
    held = loading(lower_elev=6400, mid_elev=6440, upper_elev=6600)
    assert held.deviation().millidegrees < 0
    assert not held.presses_down()
    assert held.assembly_kind_seen() is AssemblyKind.HOLD_DOWN


def test_the_force_into_the_assembly_is_tension_times_the_deviation():
    held = loading()
    expected = SAG.tension.scaled(str(held.deviation().radians))
    assert held.radial_force() == expected


def test_the_force_grows_with_the_deviation():
    gentle = loading(upper_elev=6540).radial_force().pounds
    sharp = loading(upper_elev=6510).radial_force().pounds
    assert sharp > gentle


def test_the_schedule_and_the_rope_can_disagree_about_what_a_tower_is():
    held = loading(
        lower_elev=6400,
        mid_elev=6440,
        upper_elev=6600,
        up=support_assembly(),
        down=support_assembly(),
    )
    assert held.assembly_kind_seen() is AssemblyKind.HOLD_DOWN
    assert not held.agrees_with_schedule()


def test_a_tower_built_the_way_the_rope_behaves_agrees():
    held = loading(
        lower_elev=6400,
        mid_elev=6440,
        upper_elev=6600,
        up=hold_down_assembly(),
        down=hold_down_assembly(),
    )
    assert held.agrees_with_schedule()


def test_the_designed_deviation_is_read_off_both_assemblies():
    held = loading(up=support_assembly(4, "0.7"), down=support_assembly(4, "0.7"))
    assert held.designed_deviation() == Angle.from_degrees("1.4")


def test_a_tower_close_to_its_design_figure_matches():
    held = loading(up=support_assembly(4, "0.7"), down=support_assembly(4, "0.7"))
    error = held.deviation_error()
    assert held.matches_design(abs(error) + Angle.from_degrees("0.01"))
    assert not held.matches_design(Angle.from_degrees(0))


def test_a_hold_down_with_almost_no_force_on_it_lifts_off():
    barely = loading(
        lower_elev=6400,
        mid_elev=6402,
        upper_elev=6406,
        up=hold_down_assembly(),
        down=hold_down_assembly(),
    )
    assert barely.tower.is_hold_down()
    assert barely.lifts_off()


def test_a_hold_down_with_real_force_on_it_stays_seated():
    firm = loading(
        lower_elev=6400,
        mid_elev=6440,
        upper_elev=6700,
        up=hold_down_assembly(),
        down=hold_down_assembly(),
    )
    assert not firm.lifts_off()


def test_a_support_tower_is_never_asked_about_lift_off():
    assert not loading().lifts_off()


def test_the_minimum_hold_down_force_is_a_fraction_of_the_tension():
    assert loading().minimum_hold_down_force() == Load.from_pounds(690)


def test_the_load_splits_across_the_sheaves_on_the_loaded_side():
    held = loading(up=support_assembly(6, "0.7"), down=support_assembly(4, "0.7"))
    whole = abs(held.radial_force().pounds)
    assert held.per_sheave().pounds == Load.from_pounds(whole).scaled(f"{1 / 6:.10f}").pounds


def test_a_tower_has_to_sit_between_the_two_spans_it_is_given():
    below = span(1, support("a", 0, 6400), support("t", 1000, 6500))
    above = span(2, support("t", 1000, 6500), support("b", 2000, 6560))
    with pytest.raises(LineError):
        TowerLoading(tower(1200, 6500), below, above, SAG)


def test_a_loading_prints_the_two_numbers_a_foreman_wants():
    printed = str(loading())
    assert printed.startswith("JP-T4 at ")
    assert "lb into the assembly" in printed
