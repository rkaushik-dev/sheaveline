from datetime import date

import pytest

from hill import (
    JOHN_PAUL,
    STRAWBERRY,
    WILDCAT,
    john_paul,
    strawberry,
    the_resort,
    the_season,
    the_site,
    wildcat,
)
from sheaveline.plant.classes import LiftClass, PlantError
from sheaveline.plant.identity import LiftCode, RopeSerial
from sheaveline.plant.registry import Resort


def test_the_resort_holds_its_lifts_in_code_order():
    resort = the_resort()
    assert len(resort) == 3
    assert resort.codes() == (JOHN_PAUL, STRAWBERRY, WILDCAT)
    assert str(resort) == "Ogden Peak, 3 lifts"


def test_a_lift_is_found_by_code_or_by_text():
    resort = the_resort()
    assert resort.lift(JOHN_PAUL).name == "John Paul"
    assert resort.lift("sb").name == "Strawberry"
    assert JOHN_PAUL in resort
    assert LiftCode("ZZ") not in resort


def test_asking_for_a_lift_that_is_not_there():
    with pytest.raises(PlantError):
        the_resort().lift("zz")


def test_a_resort_needs_a_name_and_at_least_one_lift():
    with pytest.raises(PlantError):
        Resort.of("  ", the_site(), the_season(), [john_paul()])
    with pytest.raises(PlantError):
        Resort.of("Ogden Peak", the_site(), the_season(), [])


def test_two_lifts_cannot_share_a_code():
    with pytest.raises(PlantError):
        Resort.of("Ogden Peak", the_site(), the_season(), [john_paul(), john_paul()])


def test_one_rope_cannot_be_on_two_machines():
    borrowed = strawberry(rope=john_paul().rope)
    with pytest.raises(PlantError):
        Resort.of("Ogden Peak", the_site(), the_season(), [john_paul(), borrowed])


def test_the_machines_are_sorted_by_what_they_are():
    resort = the_resort()
    assert [lift.code for lift in resort.aerial()] == [JOHN_PAUL, STRAWBERRY]
    assert [lift.code for lift in resort.surface()] == [WILDCAT]
    assert [lift.code for lift in resort.detachables()] == [JOHN_PAUL]
    assert resort.of_class(LiftClass.FIXED_CHAIR) == (resort.lift(STRAWBERRY),)


def test_the_hill_adds_up_its_seats_and_its_steel():
    resort = the_resort()
    assert resort.carriers_on_line() == 84 + 46 + 22
    assert resort.seats_on_line() == 84 * 4 + 46 * 3 + 22 * 2
    assert resort.tower_count() == 12
    # John Paul carries six sheave assemblies on towers four and five.
    assert resort.sheave_count() == 60 + 24 + 8


def test_the_rope_serials_come_back_as_a_set():
    serials = the_resort().rope_serials()
    assert RopeSerial.parse("FAT-2019-04412") in serials
    assert len(serials) == 3


def test_the_oldest_and_newest_machines_are_found():
    resort = the_resort()
    assert resort.oldest().code == STRAWBERRY
    assert resort.newest().code == JOHN_PAUL


def test_the_machines_built_before_a_date():
    resort = the_resort()
    early = resort.commissioned_before(date(2000, 1, 1))
    assert [lift.code for lift in early] == [STRAWBERRY, WILDCAT]


def test_only_the_aerial_machines_need_an_evacuation_plan():
    plans = the_resort().needing_evacuation_plans()
    assert [lift.code for lift in plans] == [JOHN_PAUL, STRAWBERRY]


def test_adding_a_lift_makes_a_new_registry():
    resort = the_resort()
    smaller = resort.without_lift(WILDCAT)
    assert len(smaller) == 2
    assert len(resort) == 3
    again = smaller.with_lift(wildcat())
    assert again.codes() == resort.codes()


def test_removing_a_lift_that_is_not_there():
    with pytest.raises(PlantError):
        the_resort().without_lift("zz")


def test_adding_a_lift_that_is_already_there_is_refused():
    with pytest.raises(PlantError):
        the_resort().with_lift(john_paul())


def test_the_resort_carries_the_site_and_the_season():
    resort = the_resort()
    assert resort.site.name == "Ogden Peak"
    assert resort.season.label == "2024/25"
    assert resort.season.is_operating(date(2025, 1, 15))


def test_the_surface_tow_has_no_grips_and_still_belongs():
    tow = the_resort().lift(WILDCAT)
    assert tow.carriers.kind.is_surface
    assert all(carrier.grip is None for carrier in tow.carriers)
    assert not tow.lift_class.needs_evacuation_plan
