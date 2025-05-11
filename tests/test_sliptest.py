from datetime import date
from decimal import Decimal

import pytest

from hill import jp_carriers, sb_carriers
from sheaveline.measures.load import Load
from sheaveline.plant.carrier import CarrierSet
from sheaveline.ropework.sliptest import SlipProgramme, SlipTest
from sheaveline.ropework.wear import RopeworkError

RATED = Load.from_pounds(2600)


def slip(carrier=1, pounds=2600, tested=date(2025, 1, 15)):
    return SlipTest(
        carrier=carrier,
        tested=tested,
        tester="D Ruiz",
        slipped_at=Load.from_pounds(pounds),
        rated=RATED,
    )


def programme(*tests, fleet=None, failed_before=False):
    return SlipProgramme.of(fleet or jp_carriers(), "2024/25", tests, failed_before)


def test_a_slip_test_checks_its_own_record():
    with pytest.raises(RopeworkError):
        slip(carrier=0)
    with pytest.raises(RopeworkError):
        SlipTest(1, date(2025, 1, 15), " ", RATED, RATED)
    with pytest.raises(RopeworkError):
        slip(pounds=0)
    with pytest.raises(RopeworkError):
        SlipTest(1, date(2025, 1, 15), "D Ruiz", RATED, Load.from_pounds(0))


def test_a_grip_that_slips_near_its_rating_passes():
    assert slip(pounds=2600).passed()
    assert slip(pounds=2300).passed()
    assert slip(pounds=2900).passed()


def test_a_grip_that_slips_well_outside_the_band_fails():
    assert not slip(pounds=2100).passed()
    assert not slip(pounds=3100).passed()


def test_the_band_is_fifteen_percent_either_side():
    low, high = slip().band()
    assert low == Load.from_pounds(2210)
    assert high == Load.from_pounds(2990)


def test_a_grip_that_lets_go_early_is_told_from_one_that_holds_too_hard():
    assert slip(pounds=2100).slipped_early()
    assert not slip(pounds=3100).slipped_early()
    assert slip(pounds=2100).deviation().quantize(Decimal("0.01")) == Decimal("-0.19")


def test_a_slip_test_is_built_from_the_grip_it_came_off():
    grip = next(iter(jp_carriers())).grip_of()
    built = SlipTest.of(3, date(2025, 1, 15), "D Ruiz", Load.from_pounds(2550), grip)
    assert built.rated == grip.rated_slip
    assert built.passed()


def test_a_fixed_grip_never_goes_on_the_slip_bench():
    grip = next(iter(sb_carriers())).grip_of()
    with pytest.raises(RopeworkError):
        SlipTest.of(3, date(2025, 1, 15), "D Ruiz", Load.from_pounds(2000), grip)


def test_a_test_prints_its_outcome():
    assert str(slip(carrier=7, pounds=2550)) == "carrier 7 slipped at 2,550 lb, pass"
    assert str(slip(carrier=7, pounds=2000)) == "carrier 7 slipped at 2,000 lb, fail"


def test_the_ordinary_sample_is_a_tenth_of_the_fleet():
    assert programme().fleet_size() == 84
    assert programme().ordinary_sample() == 9
    assert programme().required_sample() == 9


def test_a_small_fleet_still_gets_four_grips_tested():
    small = CarrierSet(list(jp_carriers())[:20])
    assert programme(fleet=small).ordinary_sample() == 4


def test_a_fleet_smaller_than_the_minimum_is_tested_whole():
    tiny = CarrierSet(list(jp_carriers())[:3])
    assert programme(fleet=tiny).required_sample() == 3


def test_a_failure_this_season_pulls_the_whole_fleet_in():
    walked = programme(slip(1, 2600), slip(2, 2000))
    assert walked.failures()
    assert walked.required_sample() == 84
    assert not walked.is_complete()


def test_a_failure_last_season_does_the_same_before_any_test_is_run():
    assert programme(failed_before=True).required_sample() == 84


def test_a_clean_season_leaves_the_sample_where_it_was():
    walked = programme(*[slip(number, 2550) for number in range(1, 10)])
    assert walked.failures() == ()
    assert walked.required_sample() == 9
    assert walked.is_complete()
    assert walked.outstanding() == 0


def test_the_outstanding_count_falls_as_tests_come_in():
    walked = programme(slip(1), slip(2), slip(3))
    assert walked.tested_count() == 3
    assert walked.outstanding() == 6


def test_adding_a_failing_test_reopens_a_finished_programme():
    done = programme(*[slip(number, 2550) for number in range(1, 10)])
    assert done.is_complete()
    reopened = done.with_test(slip(10, 2000))
    assert not reopened.is_complete()
    assert reopened.outstanding() == 84 - 10


def test_a_carrier_is_slip_tested_once_a_season():
    with pytest.raises(RopeworkError):
        programme(slip(1), slip(1))


def test_a_carrier_from_another_lift_is_refused():
    with pytest.raises(RopeworkError):
        programme(slip(900))


def test_a_programme_names_its_season():
    with pytest.raises(RopeworkError):
        SlipProgramme.of(jp_carriers(), " ")
    assert str(programme(slip(1))) == "2024/25 slip tests, 1 of 9 done"


def test_the_carriers_still_to_do_are_listed():
    walked = programme(slip(1), slip(2))
    outstanding = walked.untested_carriers()
    assert 1 not in outstanding
    assert 3 in outstanding
    assert len(outstanding) == 82


def test_early_slips_are_picked_out_from_the_other_failures():
    walked = programme(slip(1, 2000), slip(2, 3200))
    assert [test.carrier for test in walked.early_slips()] == [1]
    assert len(walked.failures()) == 2
