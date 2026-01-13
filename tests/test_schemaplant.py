import json

from hill import john_paul, strawberry, wildcat
from sheaveline.api.schemas.plant import LiftOut, SpanOut, TerminalOut, TowerOut
from sheaveline.measures.length import Length


def test_a_lift_reads_back_every_figure_a_terminal_tablet_shows():
    lift = john_paul()
    out = LiftOut.of(lift)
    assert out.code == lift.code
    assert out.name == "John Paul"
    assert out.lift_class == "detachable chairlift"
    assert out.horizontal_length == lift.horizontal_length()
    assert out.vertical_rise == lift.vertical_rise()
    assert out.ride_time == lift.ride_time()
    assert out.rope_length_agrees


def test_the_derived_figures_are_computed_rather_than_stored():
    lift = john_paul()
    faster = john_paul(design_speed=lift.design_speed.scaled("0.5"))
    assert LiftOut.of(faster).ride_time > LiftOut.of(lift).ride_time


def test_both_terminals_come_back_the_right_way_up():
    out = LiftOut.of(john_paul())
    assert [one.label for one in out.terminals] == ["John Paul base", "John Paul summit"]
    assert out.terminals[0].role == "drive"
    assert out.terminals[1].tension_travel == Length.from_feet(16)


def test_the_drive_terminal_carries_its_brakes_and_the_other_one_does_not():
    out = LiftOut.of(john_paul())
    drive = out.terminals[0].drive
    assert drive is not None
    assert set(drive.brakes) == {"service", "emergency"}
    assert drive.has_standby_power
    assert out.terminals[1].drive is None


def test_every_tower_comes_back_with_both_assemblies():
    lift = john_paul()
    out = LiftOut.of(lift)
    assert len(out.towers) == len(lift.towers)
    first = out.towers[0]
    assert first.number == 1
    assert first.reference == "JP-T1"
    assert first.crossarm_elevation == lift.towers[1].crossarm_elevation()
    assert first.uphill.kind == "support"
    assert first.downhill.sheaves == 4


def test_the_fleet_is_summarised_rather_than_listed():
    out = LiftOut.of(john_paul())
    assert out.fleet.on_line == 84
    assert out.fleet.seats_each == 4
    assert out.fleet.seats_on_line == 336
    assert out.fleet.withdrawn == 0
    assert out.fleet.missing_numbers == []


def test_the_rope_comes_back_with_its_mill_details():
    lift = john_paul()
    out = LiftOut.of(lift)
    assert out.rope.serial == lift.rope.serial
    assert out.rope.construction == "6x25 filler wire"
    assert out.rope.splices == 1


def test_the_spans_come_back_in_order():
    lift = john_paul()
    out = LiftOut.of(lift)
    assert [span.index for span in out.spans] == list(range(1, len(lift.spans()) + 1))
    assert out.spans[0].label.startswith("John Paul base to")


def test_a_summary_is_the_five_things_a_list_page_shows():
    summary = LiftOut.summary_of(strawberry())
    assert summary.name == "Strawberry"
    assert summary.lift_class == "fixed grip chairlift"
    assert summary.carriers_on_line == 46


def test_everything_serialises_to_strings_a_person_can_read():
    body = json.loads(LiftOut.of(john_paul()).model_dump_json())
    assert body["horizontal_length"] == "4800.00 ft"
    assert body["design_speed"] == "1000 fpm"
    assert body["bearing"] == "72 deg"
    assert body["rope"]["diameter"] == '1.378"'
    assert body["towers"][0]["uphill"]["deviation"].endswith(" deg")


def test_the_surface_tow_comes_back_without_grips_or_a_tension_drive():
    out = LiftOut.of(wildcat())
    assert out.fleet.kind == "t bar"
    assert out.terminals[1].tension_travel is not None
    assert out.terminals[0].drive is not None


def test_a_terminal_and_a_tower_can_be_rendered_on_their_own():
    lift = john_paul()
    assert TerminalOut.of(lift.bottom).label == "John Paul base"
    assert TowerOut.of(lift.towers[3]).number == 3
    assert SpanOut.of(lift.spans()[1]).index == 1


def test_the_schema_says_the_measure_fields_are_strings():
    schema = LiftOut.model_json_schema()
    properties = schema["properties"]
    assert properties["horizontal_length"]["type"] == "string"
    assert properties["design_speed"]["type"] == "string"


def test_a_withdrawn_carrier_shows_in_the_fleet_summary():
    lift = john_paul()
    thinned = lift.carriers.__class__(
        [carrier.withdrawn() if carrier.number == 3 else carrier for carrier in lift.carriers]
    )
    out = LiftOut.of(john_paul(carriers=thinned))
    assert out.fleet.on_line == 83
    assert out.fleet.withdrawn == 1
