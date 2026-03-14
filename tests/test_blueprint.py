import json

import pytest

from hill import john_paul, strawberry, wildcat
from sheaveline.errors import ParseError
from sheaveline.plant.blueprint import read_lift, write_lift
from sheaveline.plant.classes import PlantError


def drawing(lift=None):
    return write_lift(lift or john_paul())


def test_the_drawing_carries_every_part_of_the_machine():
    document = drawing()
    assert document["code"] == "JP"
    assert document["lift_class"] == "DETACHABLE_CHAIR"
    assert len(document["terminals"]) == 2
    assert len(document["towers"]) == 7
    assert document["fleet"]["count"] == 84
    assert document["rope"]["serial"] == "FAT-2019-04412"
    assert len(document["survey"]) == 14


def test_every_measure_in_the_drawing_is_written_as_text():
    document = drawing()
    assert document["design_speed"].endswith(" fpm")
    assert document["towers"][0]["height"].endswith(" ft")
    assert document["rope"]["spec"]["nominal_diameter"].endswith('"')
    assert document["survey"][0]["elevation"].endswith(" ft")


def test_the_drawing_is_plain_json():
    text = json.dumps(drawing())
    assert json.loads(text)["code"] == "JP"


def test_a_machine_read_back_from_its_own_drawing_is_the_same_machine():
    before = john_paul()
    after = read_lift(drawing(before))
    assert after.code == before.code
    assert after.name == before.name
    assert after.lift_class is before.lift_class
    assert after.bearing == before.bearing
    assert after.design_speed == before.design_speed
    assert after.commissioned == before.commissioned


def test_the_line_reads_back_with_the_same_geometry():
    before = john_paul()
    after = read_lift(drawing(before))
    assert after.horizontal_length() == before.horizontal_length()
    assert after.vertical_rise() == before.vertical_rise()
    assert after.slope_length() == before.slope_length()
    assert len(after.spans()) == len(before.spans())


def test_every_tower_reads_back_with_both_assemblies():
    before = john_paul()
    after = read_lift(drawing(before))
    for was, now in zip(before.towers, after.towers, strict=True):
        assert now.ref == was.ref
        assert now.station == was.station
        assert now.height == was.height
        assert now.uphill == was.uphill
        assert now.downhill == was.downhill


def test_the_drive_reads_back_with_its_brakes():
    before = john_paul()
    after = read_lift(drawing(before))
    was = before.drive_terminal().drive_train()
    now = after.drive_terminal().drive_train()
    assert now.rated_horsepower == was.rated_horsepower
    assert now.gearbox_ratio == was.gearbox_ratio
    assert now.auxiliary is was.auxiliary
    assert {one.kind for one in now.brakes} == {one.kind for one in was.brakes}


def test_the_fleet_reads_back_with_its_grip():
    before = john_paul()
    after = read_lift(drawing(before))
    assert after.carriers_on_line() == before.carriers_on_line()
    assert after.carriers[1].grip_of().model == before.carriers[1].grip_of().model
    assert after.carriers[1].tare == before.carriers[1].tare


def test_the_rope_reads_back_with_its_spec():
    before = john_paul()
    after = read_lift(drawing(before))
    assert after.rope == before.rope


def test_the_triple_and_the_tow_round_trip_as_well():
    for lift in (strawberry(), wildcat()):
        after = read_lift(drawing(lift))
        assert after.code == lift.code
        assert after.lift_class is lift.lift_class
        assert after.carriers_on_line() == lift.carriers_on_line()


def test_a_surface_carrier_reads_back_with_no_grip():
    after = read_lift(drawing(wildcat()))
    assert all(carrier.grip is None for carrier in after.carriers)


def test_the_terminals_are_read_the_right_way_up_whichever_order_they_are_given():
    document = drawing()
    document["terminals"] = list(reversed(document["terminals"]))
    after = read_lift(document)
    assert after.bottom.label == "John Paul base"
    assert after.top.label == "John Paul summit"


def test_a_missing_field_says_which_one_and_where():
    document = drawing()
    del document["design_speed"]
    with pytest.raises(PlantError) as refused:
        read_lift(document)
    assert "design_speed" in str(refused.value)


def test_a_missing_field_inside_a_tower_says_which_tower():
    document = drawing()
    del document["towers"][2]["height"]
    with pytest.raises(PlantError) as refused:
        read_lift(document)
    assert "tower 3" in str(refused.value)


def test_a_name_that_is_not_a_class_is_refused():
    document = drawing()
    document["lift_class"] = "MONORAIL"
    with pytest.raises(PlantError) as refused:
        read_lift(document)
    assert "MONORAIL" in str(refused.value)


def test_a_date_that_is_not_a_date_is_refused():
    document = drawing()
    document["commissioned"] = "the winter of 2004"
    with pytest.raises(PlantError):
        read_lift(document)


def test_a_measure_that_does_not_parse_is_refused():
    document = drawing()
    document["towers"][0]["height"] = "quite tall"
    with pytest.raises(ParseError):
        read_lift(document)


def test_a_drawing_with_one_terminal_is_refused():
    document = drawing()
    document["terminals"] = document["terminals"][:1]
    with pytest.raises(PlantError):
        read_lift(document)


def test_a_fleet_of_nothing_is_refused():
    document = drawing()
    document["fleet"]["count"] = 0
    with pytest.raises(PlantError):
        read_lift(document)


def test_a_survey_with_no_shots_is_refused():
    document = drawing()
    document["survey"] = []
    with pytest.raises(PlantError):
        read_lift(document)


def test_the_domain_checks_still_apply_to_a_drawing():
    document = drawing()
    document["design_speed"] = "1400 fpm"
    with pytest.raises(PlantError):
        read_lift(document)


def test_writing_a_machine_read_from_a_drawing_gives_the_same_drawing():
    once = drawing()
    twice = write_lift(read_lift(once))
    assert twice == once
