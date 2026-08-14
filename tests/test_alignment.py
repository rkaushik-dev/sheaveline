from datetime import date
from decimal import Decimal

import pytest

from hill import jp_towers
from sheaveline.measures.angle import Angle
from sheaveline.measures.length import Length
from sheaveline.plant.alignment import (
    PLUMB_TOLERANCE,
    SETTLEMENT_TOLERANCE,
    AlignmentHistory,
    AlignmentSurvey,
    TowerShot,
)
from sheaveline.plant.classes import PlantError
from sheaveline.plant.identity import TowerRef

LINE = jp_towers()
T4 = TowerRef.parse("JP-T4")
T5 = TowerRef.parse("JP-T5")
AUTUMN = date(2024, 10, 1)


def as_built(ref, offset="0 in", settled="0 in"):
    tower = next(one for one in LINE if one.ref == ref)
    return TowerShot(
        ref=ref,
        station=tower.station,
        crossarm_elevation=tower.crossarm_elevation() - Length.parse(settled),
        offset_at_top=Length.parse(offset),
    )


def survey(*shots, taken=AUTUMN, surveyor="H Ogilvie"):
    return AlignmentSurvey.of(LINE, taken, surveyor, shots or [as_built(one.ref) for one in LINE])


def test_a_tower_shot_where_the_drawing_says_it_is_has_moved_nothing():
    made = survey()
    assert made.settlement(T4) == Length(0)
    assert made.lean(T4) == Angle(0)
    assert made.is_clean()
    assert made.findings() == ()


def test_a_survey_names_who_shot_it():
    with pytest.raises(PlantError, match="names who shot it"):
        survey(surveyor="  ")


def test_a_tower_is_shot_once():
    with pytest.raises(PlantError, match="shot once"):
        AlignmentSurvey(LINE, AUTUMN, "H Ogilvie", (as_built(T4), as_built(T4)))


def test_a_tower_from_another_machine_does_not_belong_in_this_survey():
    other = TowerShot(
        ref=TowerRef.parse("SB-T2"),
        station=Length.from_feet(600),
        crossarm_elevation=Length.from_feet(6500),
        offset_at_top=Length(0),
    )
    with pytest.raises(PlantError, match="not on JP"):
        survey(other)


def test_a_tower_nobody_shot_is_named():
    made = survey(as_built(T4))
    assert T5 in made.missed()
    assert not made.is_clean()
    assert any("was not shot" in one for one in made.findings())


def test_the_offset_becomes_an_angle_over_the_tower_height():
    made = survey(as_built(T4, offset="6 in"))
    tower = made.tower(T4)
    assert made.lean(T4) == Angle.from_rise_run(Length.from_inches(6), tower.height)


def test_a_short_tower_and_a_tall_one_with_the_same_offset_are_not_the_same_trouble():
    shortest = min(LINE, key=lambda one: one.height.mils)
    tallest = max(LINE, key=lambda one: one.height.mils)
    made = survey(as_built(shortest.ref, offset="6 in"), as_built(tallest.ref, offset="6 in"))
    assert made.lean(shortest.ref) > made.lean(tallest.ref)


def test_a_tower_out_of_plumb_is_named():
    made = survey(as_built(T4, offset="18 in"))
    assert T4 in made.out_of_plumb()
    assert not made.is_clean()
    assert any("leans" in one for one in made.findings())


def test_a_tower_inside_the_tolerance_is_left_alone():
    made = survey(as_built(T4, offset="0.5 in"))
    assert made.out_of_plumb() == ()


def test_a_tower_that_has_settled_too_far_is_named():
    made = survey(as_built(T4, settled="4 in"))
    assert T4 in made.settled_too_far()
    assert made.settlement(T4) == Length.from_inches(4)
    assert any("settled" in one for one in made.findings())


def test_settling_inside_the_tolerance_is_left_alone():
    made = survey(as_built(T4, settled="1 in"))
    assert made.settled_too_far() == ()
    assert Length.from_inches("2.000") == SETTLEMENT_TOLERANCE


def test_asking_about_a_tower_nobody_shot():
    made = survey(as_built(T4))
    with pytest.raises(PlantError, match="was not shot"):
        made.lean(T5)
    with pytest.raises(PlantError, match="was not shot"):
        made.settlement(T5)


def test_asking_about_a_tower_that_is_not_on_the_line():
    with pytest.raises(PlantError, match="there is no"):
        survey().tower(TowerRef.parse("JP-T9"))


def test_a_history_has_surveys_in_it():
    with pytest.raises(PlantError, match="has surveys in it"):
        AlignmentHistory(surveys=())


def test_two_surveys_on_the_same_day_cannot_both_be_the_trend():
    with pytest.raises(PlantError, match="same day"):
        AlignmentHistory.of([survey(), survey()])


def test_one_survey_carries_no_rate():
    made = AlignmentHistory.of([survey(as_built(T4))])
    assert made.lean_a_year(T4) is None
    assert made.settling_a_year(T4) is None
    assert made.years_until_out_of_plumb(T4) is None


def test_a_tower_going_over_reads_as_an_angle_a_year():
    made = AlignmentHistory.of(
        [
            survey(as_built(T4, offset="0 in"), taken=date(2020, 10, 1)),
            survey(as_built(T4, offset="4 in"), taken=date(2024, 10, 1)),
        ]
    )
    rate = made.lean_a_year(T4)
    assert rate is not None
    assert rate.millidegrees > 0
    tower = made.latest().tower(T4)
    over_four_years = Angle.from_rise_run(Length.from_inches(4), tower.height)
    assert abs(rate.millidegrees * 4 - over_four_years.millidegrees) <= 8


def test_a_footing_going_down_reads_as_a_length_a_year():
    made = AlignmentHistory.of(
        [
            survey(as_built(T4, settled="0 in"), taken=date(2022, 10, 1)),
            survey(as_built(T4, settled="4 in"), taken=date(2024, 10, 1)),
        ]
    )
    rate = made.settling_a_year(T4)
    assert rate is not None
    assert abs(rate.mils - Length.from_inches(2).mils) <= 4


def test_a_tower_that_has_not_moved_carries_no_projection():
    made = AlignmentHistory.of(
        [
            survey(as_built(T4), taken=date(2020, 10, 1)),
            survey(as_built(T4), taken=date(2024, 10, 1)),
        ]
    )
    assert made.lean_a_year(T4) == Angle(0)
    assert made.years_until_out_of_plumb(T4) is None


def test_when_the_lean_reaches_the_tolerance():
    made = AlignmentHistory.of(
        [
            survey(as_built(T4, offset="0 in"), taken=date(2020, 10, 1)),
            survey(as_built(T4, offset="4 in"), taken=date(2024, 10, 1)),
        ]
    )
    years = made.years_until_out_of_plumb(T4)
    assert years is not None
    assert years > 0


def test_a_tower_already_over_the_tolerance_has_no_years_left():
    made = AlignmentHistory.of(
        [
            survey(as_built(T4, offset="0 in"), taken=date(2020, 10, 1)),
            survey(as_built(T4, offset="24 in"), taken=date(2024, 10, 1)),
        ]
    )
    assert made.years_until_out_of_plumb(T4) == Decimal(0)


def test_the_towers_going_over_come_back_soonest_first():
    made = AlignmentHistory.of(
        [
            survey(
                as_built(T4, offset="0 in"),
                as_built(T5, offset="0 in"),
                taken=date(2020, 10, 1),
            ),
            survey(
                as_built(T4, offset="1 in"),
                as_built(T5, offset="5 in"),
                taken=date(2024, 10, 1),
            ),
        ]
    )
    assert made.moving() == (T5, T4)
    assert Angle.from_degrees("0.50") == PLUMB_TOLERANCE
