from decimal import Decimal

import pytest

from sheaveline.measures.length import Length
from sheaveline.plant.classes import DriveType, LiftClass, PlantError, TerminalRole
from sheaveline.plant.terminal import (
    Brake,
    BrakeKind,
    Bullwheel,
    DriveTrain,
    Terminal,
    check_pair,
)

WHEEL = Bullwheel(Length.from_feet_inches(11, 6), "polyurethane")
SERVICE = Brake(BrakeKind.SERVICE, 900)
EMERGENCY = Brake(BrakeKind.EMERGENCY, 2400)
BACKSTOP = Brake(BrakeKind.BACKSTOP, 1800, hydraulic=False)


def drive_train(**overrides):
    fields = {
        "drive_type": DriveType.AC_DRIVE,
        "rated_horsepower": 700,
        "gearbox_ratio": Decimal("18.5"),
        "brakes": (SERVICE, EMERGENCY),
        "auxiliary": DriveType.DIESEL_HYDRAULIC,
    }
    fields.update(overrides)
    return DriveTrain(**fields)


def test_a_brake_needs_torque():
    with pytest.raises(PlantError):
        Brake(BrakeKind.SERVICE, 0)
    assert str(SERVICE) == "service brake at 900 lb ft"


def test_a_backstop_is_mechanical():
    with pytest.raises(PlantError):
        Brake(BrakeKind.BACKSTOP, 1800)
    assert not BACKSTOP.hydraulic


def test_brakes_know_what_they_act_on():
    assert EMERGENCY.kind.acts_on_bullwheel
    assert not SERVICE.kind.acts_on_bullwheel
    assert BACKSTOP.kind.holds_against_rollback
    assert not EMERGENCY.kind.holds_against_rollback


def test_a_bullwheel_measures_its_way_round():
    assert WHEEL.circumference().feet.quantize(Decimal("0.01")) == Decimal("36.13")


def test_rope_speed_falls_out_of_the_wheel_and_the_revolutions():
    speed = WHEEL.rope_speed_at(14)
    assert speed.feet_per_minute.quantize(Decimal("0.1")) == Decimal("505.8")
    assert WHEEL.revolutions_for(speed).quantize(Decimal("0.01")) == 14


def test_speed_and_revolutions_are_the_same_question_twice():
    for rpm in (6, 12, 20, 28):
        speed = WHEEL.rope_speed_at(rpm)
        assert WHEEL.revolutions_for(speed).quantize(Decimal("0.001")) == rpm


def test_a_wheel_will_not_take_a_rope_thicker_than_a_sixtieth_of_it():
    assert WHEEL.largest_rope() == Length.from_inches("2.3")
    assert WHEEL.accepts_rope(Length.from_inches("1.378"))
    assert not WHEEL.accepts_rope(Length.from_inches("2.5"))


def test_a_bullwheel_needs_a_size_and_a_liner():
    with pytest.raises(PlantError):
        Bullwheel(Length.from_feet(0), "polyurethane")
    with pytest.raises(PlantError):
        Bullwheel(Length.from_feet(11), "  ")
    assert str(WHEEL) == "11' 6\" bullwheel, polyurethane liner"


def test_a_drive_train_always_has_a_service_brake():
    with pytest.raises(PlantError):
        drive_train(brakes=(EMERGENCY,))
    assert drive_train().has_brake(BrakeKind.SERVICE)


def test_a_drive_train_carries_one_brake_of_each_kind():
    with pytest.raises(PlantError):
        drive_train(brakes=(SERVICE, Brake(BrakeKind.SERVICE, 700), EMERGENCY))


def test_a_drive_train_checks_its_own_numbers():
    with pytest.raises(PlantError):
        drive_train(rated_horsepower=0)
    with pytest.raises(PlantError):
        drive_train(gearbox_ratio=Decimal(0))


def test_a_named_brake_can_be_fetched_or_refused():
    assert drive_train().brake(BrakeKind.EMERGENCY) == EMERGENCY
    with pytest.raises(PlantError):
        drive_train().brake(BrakeKind.BACKSTOP)


def test_standby_power_is_an_auxiliary_or_a_diesel():
    assert drive_train().has_standby_power()
    assert not drive_train(auxiliary=None).has_standby_power()
    assert drive_train(drive_type=DriveType.DIESEL_HYDRAULIC, auxiliary=None).has_standby_power()


def test_the_motor_turns_faster_than_the_wheel_by_the_gear_ratio():
    assert drive_train().motor_speed_for(Decimal(14)) == Decimal("259.0")


def test_an_aerial_lift_needs_an_emergency_brake_and_standby_power():
    assert drive_train().suits(LiftClass.DETACHABLE_CHAIR)
    assert not drive_train(brakes=(SERVICE,)).suits(LiftClass.FIXED_CHAIR)
    assert not drive_train(auxiliary=None).suits(LiftClass.FIXED_CHAIR)


def test_a_surface_lift_is_held_to_neither():
    assert drive_train(brakes=(SERVICE,), auxiliary=None).suits(LiftClass.SURFACE_TOW)


def terminal(role, **overrides):
    fields = {
        "role": role,
        "label": "bottom",
        "station": Length.from_feet(0),
        "rope_elevation": Length.from_feet(6412),
        "bullwheel": WHEEL,
        "drive": drive_train() if role.drives else None,
        "tension_travel": Length.from_feet(14) if role.tensions else None,
    }
    fields.update(overrides)
    return Terminal(**fields)


def test_a_driving_terminal_carries_a_drive_train():
    with pytest.raises(PlantError):
        terminal(TerminalRole.DRIVE, drive=None)
    assert terminal(TerminalRole.DRIVE).drive_train().rated_horsepower == 700


def test_a_terminal_that_does_not_drive_must_not_carry_one():
    with pytest.raises(PlantError):
        terminal(TerminalRole.RETURN, drive=drive_train())
    with pytest.raises(PlantError):
        terminal(TerminalRole.RETURN).drive_train()


def test_a_tensioning_terminal_carries_its_travel():
    with pytest.raises(PlantError):
        terminal(TerminalRole.RETURN_TENSION, tension_travel=None)
    assert terminal(TerminalRole.RETURN_TENSION).travel() == Length.from_feet(14)


def test_a_terminal_that_does_not_tension_must_not_carry_travel():
    with pytest.raises(PlantError):
        terminal(TerminalRole.DRIVE, tension_travel=Length.from_feet(14))
    with pytest.raises(PlantError):
        terminal(TerminalRole.DRIVE).travel()


def test_a_terminal_prints_its_role():
    assert str(terminal(TerminalRole.DRIVE)) == "bottom (drive)"


def test_a_lift_drives_from_one_end_and_tensions_at_one_end():
    bottom = terminal(TerminalRole.DRIVE)
    top = terminal(
        TerminalRole.RETURN_TENSION,
        label="top",
        station=Length.from_feet(4800),
        rope_elevation=Length.from_feet(8012),
    )
    assert check_pair(bottom, top) is None


def test_two_drive_terminals_or_none_is_refused():
    bottom = terminal(TerminalRole.DRIVE)
    other = terminal(TerminalRole.DRIVE, label="top", station=Length.from_feet(4800))
    with pytest.raises(PlantError):
        check_pair(bottom, other)
    plain = terminal(TerminalRole.RETURN)
    with pytest.raises(PlantError):
        check_pair(plain, terminal(TerminalRole.RETURN_TENSION, station=Length.from_feet(4800)))


def test_the_terminals_have_to_be_the_right_way_up_the_hill():
    bottom = terminal(TerminalRole.DRIVE, station=Length.from_feet(4800))
    top = terminal(TerminalRole.RETURN_TENSION, label="top", station=Length.from_feet(0))
    with pytest.raises(PlantError):
        check_pair(bottom, top)
