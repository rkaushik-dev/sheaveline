import pytest

from sheaveline.measures.angle import Angle
from sheaveline.measures.length import Length
from sheaveline.plant.classes import PlantError
from sheaveline.plant.identity import LiftCode
from sheaveline.plant.span import Span, SpanLine, Support, SupportKind
from sheaveline.plant.tower import AssemblyKind, SheaveAssembly, Tower, TowerLine

JP = LiftCode("JP")


def support(label, station, elevation):
    return Support.terminal(label, Length.from_feet(station), Length.from_feet(elevation))


def tower(number, station, base, height=32):
    assembly = SheaveAssembly(AssemblyKind.SUPPORT, 4, Angle.from_degrees("1.2"))
    return Tower(
        ref=JP.tower(number),
        station=Length.from_feet(station),
        base_elevation=Length.from_feet(base),
        height=Length.from_feet(height),
        uphill=assembly,
        downhill=assembly,
    )


BOTTOM = support("bottom terminal", 0, 6400)
TOP = support("top terminal", 4800, 8000)


def test_a_span_measures_along_the_ground_and_up():
    span = Span(1, BOTTOM, support("T1", 600, 6500))
    assert span.horizontal() == Length.from_feet(600)
    assert span.rise() == Length.from_feet(100)
    assert not span.is_downhill()


def test_a_span_that_drops_has_a_negative_rise():
    span = Span(1, support("T3", 1800, 7000), support("T4", 2400, 6960))
    assert span.rise() == Length.from_feet(-40)
    assert span.is_downhill()
    assert span.slope().millidegrees < 0


def test_the_chord_is_longer_than_the_ground_distance():
    span = Span(1, support("a", 0, 0), support("b", 400, 300))
    assert span.chord() == Length.from_feet(500)
    assert span.chord() > span.horizontal()


def test_the_slope_reads_as_a_grade():
    span = Span(1, support("a", 0, 0), support("b", 400, 100))
    assert span.slope().percent_grade.quantize(Angle.from_degrees(0).percent_grade) == 25


def test_the_midpoint_sits_halfway_along():
    span = Span(1, support("a", 600, 0), support("b", 1400, 0))
    assert span.midpoint_station() == Length.from_feet(1000)


def test_the_chord_elevation_reads_anywhere_inside_the_span():
    span = Span(1, support("a", 0, 6400), support("b", 800, 6800))
    assert span.chord_elevation_at(Length.from_feet(0)) == Length.from_feet(6400)
    assert span.chord_elevation_at(Length.from_feet(400)) == Length.from_feet(6600)
    assert span.chord_elevation_at(Length.from_feet(800)) == Length.from_feet(6800)


def test_the_chord_elevation_refuses_a_station_outside_the_span():
    span = Span(1, support("a", 0, 6400), support("b", 800, 6800))
    with pytest.raises(PlantError):
        span.chord_elevation_at(Length.from_feet(900))


def test_a_span_has_to_run_up_the_hill():
    with pytest.raises(PlantError):
        Span(1, TOP, BOTTOM)
    with pytest.raises(PlantError):
        Span(1, BOTTOM, BOTTOM)


def test_spans_are_numbered_from_one():
    with pytest.raises(PlantError):
        Span(0, BOTTOM, TOP)


def test_a_span_names_the_supports_it_runs_between():
    span = Span(1, BOTTOM, TOP)
    assert span.label == "bottom terminal to top terminal"
    assert str(span) == "span 1, bottom terminal to top terminal, 4800 ft"


def test_a_support_built_from_a_tower_reads_the_crossarm():
    seven = tower(7, 2400, 7400, height=32)
    holder = Support.from_tower(seven)
    assert holder.kind is SupportKind.TOWER
    assert holder.is_tower()
    assert holder.rope_elevation == Length.from_feet(7432)
    assert str(holder) == "JP-T7"


def build_line():
    towers = TowerLine(
        [
            tower(1, 600, 6480),
            tower(2, 1600, 6900),
            tower(3, 2600, 7100),
            tower(4, 3800, 7600),
        ]
    )
    return SpanLine.between(BOTTOM, towers, TOP)


def test_a_line_cuts_into_one_more_span_than_it_has_towers():
    line = build_line()
    assert len(line) == 5
    assert len(line.supports()) == 6


def test_spans_come_out_in_order_with_the_terminals_on_the_ends():
    line = build_line()
    assert line[1].lower is BOTTOM
    assert line[5].upper is TOP
    assert [span.index for span in line] == [1, 2, 3, 4, 5]


def test_asking_for_a_span_that_is_not_there():
    line = build_line()
    with pytest.raises(PlantError):
        line[6]
    with pytest.raises(PlantError):
        line[0]


def test_a_line_needs_two_supports():
    with pytest.raises(PlantError):
        SpanLine([BOTTOM])


def test_the_line_reports_its_length_and_rise():
    line = build_line()
    assert line.horizontal_length() == Length.from_feet(4800)
    assert line.vertical_rise() == Length.from_feet(1600)
    assert line.slope_length() > line.horizontal_length()
    assert line.average_slope().percent_grade.quantize(Angle.from_degrees(0).percent_grade) > 30


def test_the_longest_and_steepest_spans_are_found():
    line = build_line()
    assert line.longest().index == 4
    assert line.steepest().index in {2, 4, 5}


def test_the_downhill_spans_are_picked_out():
    dipping = SpanLine(
        [
            support("a", 0, 6400),
            support("b", 800, 6800),
            support("c", 1600, 6700),
            support("d", 2400, 7200),
        ]
    )
    assert [span.index for span in dipping.downhill_spans()] == [2]


def test_the_span_a_station_falls_in():
    line = build_line()
    assert line.containing(Length.from_feet(2000)).index == 3
    assert line.containing(Length.from_feet(0)).index == 1
    assert line.containing(Length.from_feet(4800)).index == 5
    with pytest.raises(PlantError):
        line.containing(Length.from_feet(5200))
