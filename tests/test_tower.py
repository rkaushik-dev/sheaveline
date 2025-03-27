import pytest

from sheaveline.measures.angle import Angle
from sheaveline.measures.length import Length
from sheaveline.measures.load import Load
from sheaveline.plant.classes import PlantError
from sheaveline.plant.identity import LiftCode
from sheaveline.plant.tower import (
    AssemblyKind,
    SheaveAssembly,
    Tower,
    TowerLine,
)

JP = LiftCode("JP")


def support(sheaves=4, degrees="1.5"):
    return SheaveAssembly(AssemblyKind.SUPPORT, sheaves, Angle.from_degrees(degrees))


def hold_down(sheaves=4, degrees="-1.2"):
    return SheaveAssembly(AssemblyKind.HOLD_DOWN, sheaves, Angle.from_degrees(degrees))


def tower(number, station_feet, *, up=None, down=None, base=7400, height=32):
    return Tower(
        ref=JP.tower(number),
        station=Length.from_feet(station_feet),
        base_elevation=Length.from_feet(base),
        height=Length.from_feet(height),
        uphill=up or support(),
        downhill=down or support(),
    )


def test_a_support_bends_the_rope_down_and_a_hold_down_bends_it_up():
    assert support().kind.carries_down_force
    assert not support().kind.carries_up_force
    assert hold_down().kind.carries_up_force
    assert not hold_down().kind.carries_down_force


def test_a_combination_carries_both():
    both = SheaveAssembly(AssemblyKind.COMBINATION, 8, Angle.from_degrees("0.4"))
    assert both.kind.carries_up_force
    assert both.kind.carries_down_force


def test_the_sign_of_the_deviation_has_to_match_the_kind():
    with pytest.raises(PlantError):
        SheaveAssembly(AssemblyKind.SUPPORT, 4, Angle.from_degrees("-1.5"))
    with pytest.raises(PlantError):
        SheaveAssembly(AssemblyKind.HOLD_DOWN, 4, Angle.from_degrees("1.5"))
    with pytest.raises(PlantError):
        SheaveAssembly(AssemblyKind.COMBINATION, 4, Angle.from_degrees(0))


def test_a_support_with_no_bend_is_refused():
    with pytest.raises(PlantError):
        SheaveAssembly(AssemblyKind.SUPPORT, 4, Angle.from_degrees(0))


def test_sheaves_come_in_even_pairs():
    with pytest.raises(PlantError):
        support(sheaves=5)
    with pytest.raises(PlantError):
        support(sheaves=1)
    assert support(sheaves=8).sheaves == 8


def test_the_line_load_splits_across_the_sheaves():
    assembly = support(sheaves=4)
    assert assembly.per_sheave(Load.from_pounds(4800)) == Load.from_pounds(1200)
    assert support(sheaves=8).per_sheave(Load.from_pounds(4800)) == Load.from_pounds(600)


def test_one_assembly_can_be_compared_against_another():
    assert support(degrees="3.0").is_heavier_than(support(degrees="1.5"))
    assert hold_down(degrees="-3.0").is_heavier_than(support(degrees="1.5"))


def test_an_assembly_prints_for_the_tower_schedule():
    assert str(support(sheaves=6, degrees="1.5")) == "6s support at 1.50 deg"


def test_a_tower_carries_its_place_on_the_line():
    seven = tower(7, 2400)
    assert seven.number == 7
    assert seven.lift == JP
    assert seven.station == Length.from_feet(2400)
    assert str(seven) == "JP-T7 at station 2400"


def test_the_crossarm_sits_above_the_base():
    seven = tower(7, 2400, base=7400, height=32)
    assert seven.crossarm_elevation() == Length.from_feet(7432)


def test_a_tower_adds_up_the_bend_on_both_sides():
    seven = tower(7, 2400, up=support(degrees="1.5"), down=support(degrees="1.0"))
    assert seven.total_deviation() == Angle.from_degrees("2.5")


def test_a_tower_with_a_hold_down_on_either_side_says_so():
    assert tower(7, 2400, down=hold_down()).is_hold_down()
    assert tower(7, 2400, up=hold_down()).is_hold_down()
    assert not tower(7, 2400).is_hold_down()


def test_a_tower_counts_its_sheaves():
    assert tower(7, 2400, up=support(6), down=support(4)).sheave_count() == 10


def test_a_tower_before_the_terminal_or_with_no_height_is_refused():
    with pytest.raises(PlantError):
        tower(1, -10)
    with pytest.raises(PlantError):
        tower(1, 100, height=0)


def test_a_line_orders_its_towers_and_answers_by_number():
    line = TowerLine([tower(2, 1200), tower(1, 600), tower(3, 2000)])
    assert len(line) == 3
    assert [t.number for t in line] == [1, 2, 3]
    assert line[2].station == Length.from_feet(1200)
    assert line.first().number == 1
    assert line.last().number == 3


def test_a_line_needs_towers_numbered_from_one_without_gaps():
    with pytest.raises(PlantError):
        TowerLine([tower(1, 600), tower(3, 2000)])
    with pytest.raises(PlantError):
        TowerLine([tower(2, 600), tower(3, 2000)])


def test_a_line_needs_stations_that_climb():
    with pytest.raises(PlantError):
        TowerLine([tower(1, 1200), tower(2, 600)])
    with pytest.raises(PlantError):
        TowerLine([tower(1, 600), tower(2, 600)])


def test_a_line_belongs_to_one_lift():
    other = Tower(
        ref=LiftCode("SB").tower(2),
        station=Length.from_feet(1200),
        base_elevation=Length.from_feet(7400),
        height=Length.from_feet(30),
        uphill=support(),
        downhill=support(),
    )
    with pytest.raises(PlantError):
        TowerLine([tower(1, 600), other])


def test_an_empty_line_is_refused():
    with pytest.raises(PlantError):
        TowerLine([])


def test_asking_for_a_tower_that_is_not_there():
    line = TowerLine([tower(1, 600), tower(2, 1200)])
    with pytest.raises(PlantError):
        line[3]
    with pytest.raises(PlantError):
        line[0]


def test_a_line_picks_out_its_hold_downs_and_its_worst_tower():
    line = TowerLine(
        [
            tower(1, 600),
            tower(2, 1200, up=hold_down(degrees="-2.5"), down=hold_down(degrees="-2.5")),
            tower(3, 2000, up=support(degrees="3.0")),
        ]
    )
    assert [t.number for t in line.hold_downs()] == [2]
    assert line.steepest().number == 2
    assert line.sheave_count() == 24
    assert line.lift == JP
    assert line.span_stations()[1] == Length.from_feet(1200)
