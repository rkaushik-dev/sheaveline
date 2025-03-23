import pytest

from sheaveline.measures.speed import Speed
from sheaveline.plant.classes import (
    CarrierType,
    DriveType,
    GripType,
    LiftClass,
    PlantError,
    TerminalRole,
)


def test_every_class_names_its_grip():
    for lift_class in LiftClass:
        assert isinstance(lift_class.grip, GripType)
    assert LiftClass.DETACHABLE_CHAIR.grip is GripType.DETACHABLE
    assert LiftClass.FIXED_CHAIR.grip is GripType.FIXED
    assert LiftClass.SURFACE_TOW.grip is GripType.TOW


def test_only_the_detachables_detach():
    assert LiftClass.DETACHABLE_CHAIR.is_detachable
    assert LiftClass.DETACHABLE_GONDOLA.is_detachable
    assert not LiftClass.PULSE_GONDOLA.is_detachable
    assert not LiftClass.AERIAL_TRAM.is_detachable


def test_a_surface_lift_is_the_only_one_not_in_the_air():
    assert not LiftClass.SURFACE_TOW.is_aerial
    assert not LiftClass.SURFACE_TOW.needs_evacuation_plan
    for lift_class in LiftClass:
        if lift_class is not LiftClass.SURFACE_TOW:
            assert lift_class.is_aerial
            assert lift_class.needs_evacuation_plan


def test_a_tram_runs_back_and_forth_rather_than_round():
    assert LiftClass.AERIAL_TRAM.is_reversible
    assert not LiftClass.DETACHABLE_CHAIR.is_reversible


def test_the_line_speed_limit_is_a_property_of_the_class():
    assert LiftClass.FIXED_CHAIR.line_speed_limit == Speed.from_feet_per_minute(500)
    assert LiftClass.DETACHABLE_CHAIR.line_speed_limit > LiftClass.FIXED_CHAIR.line_speed_limit
    assert LiftClass.AERIAL_TRAM.line_speed_limit == Speed.from_feet_per_minute(2400)


def test_every_class_has_at_least_one_carrier_it_takes():
    for lift_class in LiftClass:
        assert lift_class.carriers()


def test_a_fixed_grip_chair_does_not_take_a_six_place():
    assert LiftClass.FIXED_CHAIR.accepts(CarrierType.CHAIR_4)
    assert not LiftClass.FIXED_CHAIR.accepts(CarrierType.CHAIR_6)
    with pytest.raises(PlantError):
        LiftClass.FIXED_CHAIR.require(CarrierType.CHAIR_6)


def test_a_gondola_takes_cabins_and_nothing_else():
    assert LiftClass.DETACHABLE_GONDOLA.carriers() == frozenset({CarrierType.CABIN_8})
    with pytest.raises(PlantError):
        LiftClass.PULSE_GONDOLA.require(CarrierType.CHAIR_4)


def test_a_surface_lift_takes_neither_chairs_nor_cabins():
    for carrier in (CarrierType.CHAIR_2, CarrierType.CABIN_8, CarrierType.TRAM_CAR):
        assert not LiftClass.SURFACE_TOW.accepts(carrier)
    assert LiftClass.SURFACE_TOW.accepts(CarrierType.TBAR)


def test_the_require_check_passes_quietly_when_the_pairing_is_good():
    assert LiftClass.DETACHABLE_CHAIR.require(CarrierType.CHAIR_6) is None


def test_carriers_report_how_many_they_seat():
    assert CarrierType.CHAIR_2.seats == 2
    assert CarrierType.CHAIR_6.seats == 6
    assert CarrierType.CABIN_8.seats == 8
    assert CarrierType.PLATTER.seats == 1


def test_every_carrier_type_has_a_seat_count():
    for carrier in CarrierType:
        assert carrier.seats >= 1


def test_enclosed_and_surface_carriers_are_told_apart():
    assert CarrierType.CABIN_8.is_enclosed
    assert CarrierType.TRAM_CAR.is_enclosed
    assert not CarrierType.CHAIR_4.is_enclosed
    assert CarrierType.TBAR.is_surface
    assert not CarrierType.CABIN_8.is_surface


def test_a_tow_hanger_is_not_slip_tested():
    assert GripType.DETACHABLE.is_tested_for_slip
    assert GripType.FIXED.is_tested_for_slip
    assert not GripType.TOW.is_tested_for_slip
    assert GripType.DETACHABLE.detaches
    assert not GripType.FIXED.detaches


def test_a_diesel_is_its_own_standby():
    assert DriveType.DIESEL_HYDRAULIC.is_prime_mover
    assert not DriveType.DIESEL_HYDRAULIC.has_variable_speed
    assert DriveType.AC_DRIVE.has_variable_speed
    assert not DriveType.AC_DRIVE.is_prime_mover


def test_terminal_roles_separate_driving_from_tensioning():
    assert TerminalRole.DRIVE.drives
    assert TerminalRole.DRIVE_TENSION.drives
    assert not TerminalRole.RETURN.drives
    assert TerminalRole.RETURN_TENSION.tensions
    assert TerminalRole.DRIVE_TENSION.tensions
    assert not TerminalRole.DRIVE.tensions


def test_exactly_one_terminal_role_both_drives_and_tensions():
    both = [role for role in TerminalRole if role.drives and role.tensions]
    assert both == [TerminalRole.DRIVE_TENSION]
