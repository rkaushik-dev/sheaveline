from datetime import date

import pytest

from hill import (
    JOHN_PAUL,
    STRAWBERRY,
    bullwheel,
    john_paul,
    jp_carriers,
    jp_towers,
    support_assembly,
)
from sheaveline.measures.angle import Bearing
from sheaveline.measures.length import Length
from sheaveline.measures.speed import Speed
from sheaveline.plant.carrier import Carrier, CarrierSet, Grip
from sheaveline.plant.classes import (
    CarrierType,
    GripType,
    LiftClass,
    PlantError,
    TerminalRole,
)
from sheaveline.plant.identity import LiftCode, RopeSerial
from sheaveline.plant.profile import LineProfile
from sheaveline.plant.terminal import Terminal
from sheaveline.plant.tower import Tower, TowerLine

from sheaveline.measures.load import Load  # isort: skip


def test_the_quad_describes_itself():
    lift = john_paul()
    assert lift.code == JOHN_PAUL
    assert lift.lift_class is LiftClass.DETACHABLE_CHAIR
    assert str(lift) == "JP John Paul, detachable chairlift"


def test_the_line_lengths_come_off_the_terminals_and_the_towers():
    lift = john_paul()
    assert lift.horizontal_length() == Length.from_feet(4800)
    assert lift.vertical_rise() == Length.from_feet(1526)
    assert lift.slope_length() > lift.horizontal_length()
    assert lift.slope_length().feet.quantize(Length.from_feet(1).feet) > 5000


def test_the_spans_run_terminal_to_terminal_through_every_tower():
    spans = john_paul().spans()
    assert len(spans) == 8
    assert spans[1].lower.label == "John Paul base"
    assert spans[8].upper.label == "John Paul summit"


def test_the_drive_and_the_tension_are_found_by_role():
    lift = john_paul()
    assert lift.drive_terminal().role is TerminalRole.DRIVE
    assert lift.tension_terminal().role is TerminalRole.RETURN_TENSION
    assert lift.drive_terminal().label == "John Paul base"


def test_the_rope_is_twice_the_line_plus_the_bullwheels():
    lift = john_paul()
    expected = lift.expected_rope_length()
    assert expected > lift.slope_length().scaled(2)
    assert lift.rope_length_agrees()


def test_a_rope_of_the_wrong_length_is_caught():
    lift = john_paul()
    short = lift.rope.__class__(
        serial=lift.rope.serial,
        spec=lift.rope.spec,
        installed=lift.rope.installed,
        length=Length.from_feet(9000),
        splices=1,
    )
    assert not john_paul(rope=short).rope_length_agrees()


def test_the_ride_time_falls_out_of_the_speed_and_the_length():
    lift = john_paul()
    assert lift.ride_time() == lift.design_speed.time_to_cover(lift.slope_length())
    assert lift.ride_time().minutes > 5


def test_the_fleet_is_counted_by_what_is_on_the_line():
    lift = john_paul()
    assert lift.carriers_on_line() == 84
    assert lift.seats_on_line() == 336


def test_the_lift_reports_its_age():
    lift = john_paul()
    assert lift.age_in_years(date(2025, 1, 15)) == 20
    assert lift.age_in_years(date(2024, 12, 3)) == 20
    assert lift.age_in_years(date(2024, 12, 2)) == 19
    with pytest.raises(PlantError):
        lift.age_in_years(date(2000, 1, 1))


def test_the_line_runs_uphill_one_way_and_down_the_other():
    lift = john_paul()
    assert lift.uphill_bearing() == Bearing.from_degrees(72)
    assert lift.downhill_bearing() == Bearing.from_degrees(252)


def test_a_tower_line_from_another_lift_is_refused():
    other = TowerLine(
        [
            Tower(
                ref=STRAWBERRY.tower(1),
                station=Length.from_feet(600),
                base_elevation=Length.from_feet(6496),
                height=Length.from_feet(34),
                uphill=support_assembly(),
                downhill=support_assembly(),
            )
        ]
    )
    with pytest.raises(PlantError):
        john_paul(towers=other)


def test_a_tower_outside_the_terminals_is_refused():
    lift = john_paul()
    towers = jp_towers()
    beyond = TowerLine(
        [
            *list(towers)[:6],
            Tower(
                ref=JOHN_PAUL.tower(7),
                station=Length.from_feet(5200),
                base_elevation=Length.from_feet(7900),
                height=Length.from_feet(28),
                uphill=support_assembly(),
                downhill=support_assembly(),
            ),
        ]
    )
    with pytest.raises(PlantError):
        john_paul(towers=beyond)
    assert lift.towers.last().station < lift.top.station


def test_carriers_that_do_not_belong_on_the_class_are_refused():
    grip = Grip(
        kind=GripType.FIXED,
        model="TB41",
        jaws=2,
        spring_pack="green",
        rated_slip=Load.from_pounds(2100),
        installed=date(2019, 10, 4),
    )
    fixed = CarrierSet(
        [
            Carrier(
                number=number,
                kind=CarrierType.CHAIR_4,
                tare=Load.from_pounds(410),
                hanger=Length.from_feet_inches(6, 4),
                grip=grip,
                restraining_bar=True,
            )
            for number in range(1, 20)
        ]
    )
    with pytest.raises(PlantError):
        john_paul(carriers=fixed)


def test_a_rope_too_thick_for_the_bullwheel_is_refused():
    narrow = Terminal(
        role=TerminalRole.DRIVE,
        label="John Paul base",
        station=Length.from_feet(0),
        rope_elevation=Length.from_feet(6412),
        bullwheel=bullwheel(feet=5, inches=0),
        drive=john_paul().bottom.drive,
    )
    with pytest.raises(PlantError):
        john_paul(bottom=narrow)


def test_a_speed_above_what_the_class_allows_is_refused():
    with pytest.raises(PlantError):
        john_paul(design_speed=Speed.from_feet_per_minute(1400))
    with pytest.raises(PlantError):
        john_paul(design_speed=Speed.from_feet_per_minute(0))
    with pytest.raises(PlantError):
        john_paul(lift_class=LiftClass.FIXED_CHAIR, carriers=jp_carriers())


def test_a_survey_that_stops_short_of_a_terminal_is_refused():
    short = LineProfile.from_survey([(0, 6400), (2000, 6940)])
    with pytest.raises(PlantError):
        john_paul(ground=short)


def test_a_rope_older_than_the_machine_is_refused():
    lift = john_paul()
    early = lift.rope.__class__(
        serial=RopeSerial.parse("FAT-2003-00811"),
        spec=lift.rope.spec,
        installed=date(2004, 1, 1),
        length=lift.rope.length,
        splices=1,
    )
    with pytest.raises(PlantError):
        john_paul(rope=early)


def test_a_lift_code_is_carried_through_to_the_towers():
    assert john_paul().towers.lift == LiftCode("JP")
