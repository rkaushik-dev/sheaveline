from datetime import date

import pytest

from hill import john_paul
from sheaveline.measures.length import Length
from sheaveline.plant.rope import HaulRope, LayDirection
from sheaveline.ropework.breaks import (
    BreakLimits,
    BreakObservation,
    BreakSurvey,
)
from sheaveline.ropework.wear import RopeworkError

ROPE = john_paul().rope


def lang_rope() -> HaulRope:
    spec = ROPE.spec.__class__(
        construction=ROPE.spec.construction,
        grade=ROPE.spec.grade,
        lay=LayDirection.RIGHT_LANG,
        nominal_diameter=ROPE.spec.nominal_diameter,
        breaking_strength=ROPE.spec.breaking_strength,
        weight=ROPE.spec.weight,
    )
    return HaulRope(
        serial=ROPE.serial,
        spec=spec,
        installed=ROPE.installed,
        length=ROPE.length,
        splices=1,
    )


def seen(station_feet, one, six=None, valley=0, splice=False):
    return BreakObservation(
        station=Length.from_feet(station_feet),
        in_one_lay=one,
        in_six_lays=six if six is not None else one,
        valley_breaks=valley,
        at_splice=splice,
    )


def survey(*observations, rope=ROPE, taken=date(2025, 1, 15)):
    return BreakSurvey.of(rope, taken, "R Halvorsen", observations)


def test_a_regular_lay_rope_gets_the_full_allowance():
    limits = BreakLimits.for_spec(ROPE.spec)
    assert limits.one_lay == 6
    assert limits.six_lays == 12
    assert limits.valley == 1
    assert limits.at_splice == 2


def test_a_lang_lay_rope_gets_half_of_it():
    limits = BreakLimits.for_spec(lang_rope().spec)
    assert limits.one_lay == 3
    assert limits.six_lays == 6


def test_the_watch_level_is_half_the_allowance_and_no_valley_breaks():
    watch = BreakLimits.for_spec(ROPE.spec).watch_level()
    assert watch.one_lay == 3
    assert watch.six_lays == 6
    assert watch.valley == 0


def test_an_observation_checks_its_own_counts():
    with pytest.raises(RopeworkError):
        seen(-10, 2)
    with pytest.raises(RopeworkError):
        BreakObservation(Length.from_feet(0), -1, 0)
    with pytest.raises(RopeworkError):
        seen(0, 5, six=2)
    with pytest.raises(RopeworkError):
        seen(0, 2, valley=3)


def test_a_count_inside_the_allowance_passes():
    assert not seen(0, 5, six=9).exceeds(BreakLimits.for_spec(ROPE.spec))


def test_one_too_many_in_a_lay_fails():
    assert seen(0, 7, six=9).exceeds(BreakLimits.for_spec(ROPE.spec))


def test_too_many_across_six_lays_fails_even_when_each_lay_is_fine():
    assert seen(0, 4, six=13).exceeds(BreakLimits.for_spec(ROPE.spec))


def test_two_valley_breaks_fail_on_their_own():
    limits = BreakLimits.for_spec(ROPE.spec)
    assert not seen(0, 3, valley=1).exceeds(limits)
    assert seen(0, 3, valley=2).exceeds(limits)


def test_the_splice_is_held_to_a_tighter_count():
    limits = BreakLimits.for_spec(ROPE.spec)
    assert seen(0, 3, splice=True).exceeds(limits) is not False
    assert seen(0, 3, splice=True).exceeds(limits)
    assert not seen(0, 2, splice=True).exceeds(limits)
    assert not seen(0, 3, splice=False).exceeds(limits)


def test_the_worst_station_prefers_a_valley_break_over_a_bigger_crown_count():
    walked = survey(seen(600, 5), seen(1200, 2, valley=1))
    assert walked.worst().station == Length.from_feet(1200)


def test_the_breaks_add_up_across_the_walk():
    walked = survey(seen(600, 3), seen(1200, 2), seen(2400, 4))
    assert walked.total_breaks() == 9


def test_a_survey_is_signed_and_cannot_predate_the_rope():
    with pytest.raises(RopeworkError):
        BreakSurvey.of(ROPE, date(2025, 1, 15), " ", [])
    with pytest.raises(RopeworkError):
        BreakSurvey.of(ROPE, date(2015, 1, 1), "R Halvorsen", [])


def test_a_station_past_the_end_of_the_rope_is_refused():
    with pytest.raises(RopeworkError):
        survey(seen(12000, 2))


def test_the_clean_length_takes_out_a_lay_for_every_marked_station():
    walked = survey(seen(600, 3), seen(1200, 2))
    assert walked.clean_length() < ROPE.length
    assert survey().clean_length() == ROPE.length
