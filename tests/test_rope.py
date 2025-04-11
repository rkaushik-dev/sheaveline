from datetime import date
from decimal import Decimal

import pytest

from sheaveline.measures.length import Length
from sheaveline.measures.load import Load, LoadPerFoot
from sheaveline.plant.classes import PlantError
from sheaveline.plant.identity import RopeSerial
from sheaveline.plant.rope import (
    HaulRope,
    LayDirection,
    RopeConstruction,
    RopeGrade,
    RopeSpec,
)

SERIAL = RopeSerial.parse("FAT-2019-04412")


def spec(**overrides):
    fields = {
        "construction": RopeConstruction.SIX_BY_TWENTY_FIVE,
        "grade": RopeGrade.EIP,
        "lay": LayDirection.RIGHT_REGULAR,
        "nominal_diameter": Length.from_inches("1.378"),
        "breaking_strength": Load.from_pounds(196000),
        "weight": LoadPerFoot.from_pounds_per_foot("3.12"),
    }
    fields.update(overrides)
    return RopeSpec(**fields)


def rope(**overrides):
    fields = {
        "serial": SERIAL,
        "spec": spec(),
        "installed": date(2019, 10, 4),
        "length": Length.from_feet(9800),
        "splices": 1,
    }
    fields.update(overrides)
    return HaulRope(**fields)


def test_construction_decides_how_many_wires_can_be_seen():
    assert RopeConstruction.SIX_BY_TWENTY_FIVE.strands == 6
    assert RopeConstruction.SIX_BY_TWENTY_FIVE.outer_wires_per_strand == 12
    assert RopeConstruction.SIX_BY_TWENTY_FIVE.outer_wires == 72
    assert RopeConstruction.EIGHT_BY_NINETEEN.outer_wires == 72
    assert RopeConstruction.SIX_BY_THIRTY_SIX.outer_wires == 84


def test_every_construction_names_its_wires():
    for construction in RopeConstruction:
        assert construction.outer_wires > 0


def test_lang_lay_is_told_from_regular():
    assert LayDirection.RIGHT_LANG.is_lang
    assert LayDirection.LEFT_LANG.is_lang
    assert not LayDirection.RIGHT_REGULAR.is_lang


def test_a_spec_checks_its_own_numbers():
    with pytest.raises(PlantError):
        spec(nominal_diameter=Length.from_inches(0))
    with pytest.raises(PlantError):
        spec(breaking_strength=Load.from_pounds(0))
    with pytest.raises(PlantError):
        spec(weight=LoadPerFoot.from_pounds_per_foot(0))


def test_the_lay_length_is_six_and_a_half_diameters():
    assert spec().lay_length() == Length.from_inches("8.957")


def test_the_condemning_diameter_is_seven_percent_down():
    assert spec().condemning_diameter() == Length.from_inches("1.282")
    assert spec().condemning_diameter() < spec().nominal_diameter


def test_a_run_of_rope_weighs_what_the_spec_says():
    assert spec().weight_of(Length.from_feet(1000)) == Load.from_pounds(3120)


def test_the_design_factor_is_breaking_strength_over_tension():
    assert spec().design_factor_at(Load.from_pounds(49000)) == 4
    assert spec().design_factor_at(Load.from_pounds(28000)) == 7


def test_four_to_one_is_the_line_between_safe_and_not():
    assert spec().safe_at(Load.from_pounds(49000))
    assert spec().safe_at(Load.from_pounds(40000))
    assert not spec().safe_at(Load.from_pounds(50000))


def test_the_highest_tension_the_spec_allows():
    assert spec().maximum_tension() == Load.from_pounds(49000)


def test_a_design_factor_needs_a_real_tension():
    with pytest.raises(PlantError):
        spec().design_factor_at(Load.from_pounds(0))


def test_a_spec_prints_the_way_a_reel_tag_reads():
    assert str(spec()) == '1.378" 6x25 filler wire'


def test_a_haul_rope_reads_its_diameter_through_its_spec():
    assert rope().diameter == Length.from_inches("1.378")
    assert rope().weight() == Load.from_pounds(30576)


def test_a_rope_reports_its_age_in_days_and_in_seasons():
    assert rope().age_in_days(date(2020, 10, 3)) == 365
    assert rope().seasons_in_service(date(2025, 10, 4)) == 6
    assert rope().seasons_in_service(date(2020, 1, 1)) == 0


def test_a_date_before_the_rope_went_on_is_refused():
    with pytest.raises(PlantError):
        rope().age_in_days(date(2019, 1, 1))


def test_a_rope_cannot_go_on_before_it_was_drawn():
    with pytest.raises(PlantError):
        rope(installed=date(2018, 5, 1))


def test_a_rope_checks_its_own_length_and_splices():
    with pytest.raises(PlantError):
        rope(length=Length.from_feet(0))
    with pytest.raises(PlantError):
        rope(splices=-1)


def test_a_loop_has_a_splice_and_a_tram_rope_does_not():
    assert rope().is_endless()
    assert not rope(splices=0).is_endless()


def test_a_new_splice_makes_a_new_record():
    spliced = rope().with_splice()
    assert spliced.splices == 2
    assert rope().splices == 1
    assert spliced.serial == rope().serial


def test_a_rope_prints_for_the_rope_file():
    assert str(rope()) == 'FAT-2019-04412 1.378" 6x25 filler wire, 9800 ft'


def test_the_grades_are_ordered_the_way_the_mill_names_them():
    assert RopeGrade.EEIP.value.startswith("extra extra")
    assert {grade.name for grade in RopeGrade} == {"IPS", "EIP", "EEIP"}


def test_a_heavier_grade_does_not_change_the_geometry():
    heavy = spec(grade=RopeGrade.EEIP, breaking_strength=Load.from_pounds(216000))
    assert heavy.lay_length() == spec().lay_length()
    assert heavy.maximum_tension() > spec().maximum_tension()
    assert heavy.design_factor_at(Load.from_pounds(49000)).quantize(Decimal("0.01")) == Decimal(
        "4.41"
    )
