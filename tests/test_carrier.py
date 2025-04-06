from datetime import date

import pytest

from sheaveline.measures.length import Length
from sheaveline.measures.load import Load
from sheaveline.plant.carrier import Carrier, CarrierSet, Grip
from sheaveline.plant.classes import CarrierType, GripType, LiftClass, PlantError

INSTALLED = date(2019, 9, 12)


def grip(kind=GripType.DETACHABLE, **overrides):
    fields = {
        "kind": kind,
        "model": "AK6",
        "jaws": 1,
        "spring_pack": "blue",
        "rated_slip": Load.from_pounds(2600),
        "installed": INSTALLED,
    }
    fields.update(overrides)
    return Grip(**fields)


def carrier(number=1, kind=CarrierType.CHAIR_6, **overrides):
    surface = kind.is_surface
    fields = {
        "number": number,
        "kind": kind,
        "tare": Load.from_pounds(480),
        "hanger": Length.from_feet_inches(6, 4),
        "grip": None if surface else grip(),
        "restraining_bar": not kind.is_enclosed and not surface,
    }
    fields.update(overrides)
    return Carrier(**fields)


def test_a_grip_holds_up_to_its_rated_slip():
    assert grip().holds(Load.from_pounds(2400))
    assert grip().holds(Load.from_pounds(2600))
    assert not grip().holds(Load.from_pounds(2700))


def test_a_tow_hanger_is_not_a_grip():
    with pytest.raises(PlantError):
        grip(kind=GripType.TOW)


def test_a_grip_checks_its_own_description():
    with pytest.raises(PlantError):
        grip(jaws=3)
    with pytest.raises(PlantError):
        grip(rated_slip=Load.from_pounds(0))
    with pytest.raises(PlantError):
        grip(model="   ")


def test_a_grip_reports_its_age_and_refuses_a_date_before_it_went_on():
    assert grip().age_in_days(date(2020, 9, 11)) == 365
    with pytest.raises(PlantError):
        grip().age_in_days(date(2019, 1, 1))


def test_both_grip_kinds_are_slip_tested():
    assert grip().is_slip_tested()
    assert grip(kind=GripType.FIXED).is_slip_tested()
    assert str(grip()) == "AK6 detachable grip"


def test_a_carrier_carries_a_hundred_and_seventy_pounds_a_seat():
    assert carrier().seats == 6
    assert carrier().design_load() == Load.from_pounds(1020)
    assert carrier().gross() == Load.from_pounds(1500)


def test_a_carrier_off_the_rope_needs_a_grip_and_a_surface_one_does_not():
    with pytest.raises(PlantError):
        carrier(kind=CarrierType.CHAIR_4, grip=None)
    with pytest.raises(PlantError):
        carrier(kind=CarrierType.TBAR, grip=grip(), restraining_bar=False)
    assert carrier(kind=CarrierType.TBAR, restraining_bar=False).grip is None


def test_an_enclosed_carrier_has_no_bar():
    with pytest.raises(PlantError):
        carrier(kind=CarrierType.CABIN_8, restraining_bar=True)
    assert carrier(kind=CarrierType.CABIN_8, restraining_bar=False).kind.is_enclosed


def test_a_carrier_checks_its_own_numbers():
    with pytest.raises(PlantError):
        carrier(number=0)
    with pytest.raises(PlantError):
        carrier(tare=Load.from_pounds(0))
    with pytest.raises(PlantError):
        carrier(hanger=Length.from_feet(0))


def test_asking_a_surface_carrier_for_its_grip():
    with pytest.raises(PlantError):
        carrier(kind=CarrierType.PLATTER, restraining_bar=False).grip_of()
    assert carrier().grip_of().model == "AK6"


def test_a_carrier_belongs_on_a_class_that_takes_it_with_a_matching_grip():
    six = carrier(kind=CarrierType.CHAIR_6)
    assert six.suits(LiftClass.DETACHABLE_CHAIR)
    assert not six.suits(LiftClass.FIXED_CHAIR)
    with pytest.raises(PlantError):
        six.require_suits(LiftClass.FIXED_CHAIR)


def test_the_right_carrier_with_the_wrong_grip_still_does_not_belong():
    quad = carrier(kind=CarrierType.CHAIR_4, grip=grip(kind=GripType.DETACHABLE))
    assert not quad.suits(LiftClass.FIXED_CHAIR)
    assert quad.suits(LiftClass.DETACHABLE_CHAIR)
    fixed = carrier(kind=CarrierType.CHAIR_4, grip=grip(kind=GripType.FIXED))
    assert fixed.suits(LiftClass.FIXED_CHAIR)


def test_a_surface_carrier_belongs_only_on_a_surface_lift():
    bar = carrier(kind=CarrierType.TBAR, restraining_bar=False)
    assert bar.suits(LiftClass.SURFACE_TOW)
    assert not bar.suits(LiftClass.FIXED_CHAIR)


def test_a_carrier_comes_off_the_line_without_being_deleted():
    off = carrier().withdrawn()
    assert not off.in_service
    assert off.number == 1
    assert str(off) == "carrier 1, six place chair (off line)"
    assert str(carrier()) == "carrier 1, six place chair"


def build_set(count=6, kind=CarrierType.CHAIR_6):
    return CarrierSet([carrier(number, kind=kind) for number in range(1, count + 1)])


def test_a_set_sorts_and_answers_by_number():
    fleet = CarrierSet([carrier(3), carrier(1), carrier(2)])
    assert [c.number for c in fleet] == [1, 2, 3]
    assert fleet[2].number == 2
    assert len(fleet) == 3
    assert fleet.kind is CarrierType.CHAIR_6


def test_asking_for_a_carrier_that_is_not_in_the_set():
    with pytest.raises(PlantError):
        build_set()[99]


def test_a_set_refuses_duplicates_an_empty_fleet_and_a_mixed_one():
    with pytest.raises(PlantError):
        CarrierSet([carrier(1), carrier(1)])
    with pytest.raises(PlantError):
        CarrierSet([])
    with pytest.raises(PlantError):
        CarrierSet([carrier(1), carrier(2, kind=CarrierType.CHAIR_4)])


def test_withdrawn_carriers_do_not_count_toward_the_load():
    fleet = CarrierSet([carrier(1), carrier(2), carrier(3).withdrawn()])
    assert len(fleet.on_line()) == 2
    assert len(fleet.withdrawn()) == 1
    assert fleet.seats_on_line() == 12
    assert fleet.design_load() == Load.from_pounds(2040)
    assert fleet.gross() == Load.from_pounds(3000)


def test_a_whole_set_can_be_checked_against_a_class():
    assert build_set().require_suits(LiftClass.DETACHABLE_CHAIR) is None
    with pytest.raises(PlantError):
        build_set().require_suits(LiftClass.FIXED_CHAIR)


def test_gaps_in_the_painted_numbers_are_reported():
    fleet = CarrierSet([carrier(1), carrier(2), carrier(5)])
    assert fleet.missing_numbers() == (3, 4)
    assert build_set().missing_numbers() == ()
