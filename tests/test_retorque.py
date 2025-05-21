from datetime import date
from decimal import Decimal

import pytest

from hill import jp_carriers, sb_carriers
from sheaveline.measures.torque import Torque
from sheaveline.ropework.retorque import RetorqueCycle, TorqueCheck
from sheaveline.ropework.wear import RopeworkError

SPEC = Torque.from_inch_pounds(450)


def check(carrier=1, found=450, checked=date(2025, 1, 15), reset=None):
    return TorqueCheck(
        carrier=carrier,
        checked=checked,
        checker="M Vetter",
        specified=SPEC,
        found=Torque.from_inch_pounds(found),
        reset_to=None if reset is None else Torque.from_inch_pounds(reset),
    )


def cycle(*checks, fleet=None, interval=365):
    return RetorqueCycle.of(fleet or sb_carriers(), SPEC, checks, interval)


def test_a_check_checks_its_own_record():
    with pytest.raises(RopeworkError):
        check(carrier=0)
    with pytest.raises(RopeworkError):
        TorqueCheck(1, date(2025, 1, 15), " ", SPEC, SPEC)
    with pytest.raises(RopeworkError):
        TorqueCheck(1, date(2025, 1, 15), "M Vetter", Torque(0), SPEC)


def test_a_grip_inside_a_tenth_of_the_figure_is_in_band():
    assert check(found=450).within_band()
    assert check(found=410).within_band()
    assert check(found=495).within_band()
    assert not check(found=390).within_band()


def test_slack_means_below_the_band_and_not_above_it():
    assert check(found=380).found_slack()
    assert not check(found=520).found_slack()
    assert not check(found=430).found_slack()


def test_the_shortfall_is_how_far_under_it_read():
    assert check(found=380).shortfall() == Torque.from_inch_pounds(70)


def test_a_grip_left_as_found_is_only_correct_if_it_was_in_band():
    assert check(found=440).left_correct()
    assert not check(found=380).left_correct()
    assert not check(found=380).was_reset()


def test_a_grip_reset_to_the_figure_is_correct_however_it_was_found():
    reset = check(found=380, reset=450)
    assert reset.was_reset()
    assert reset.left_correct()
    assert reset.found_slack()


def test_a_grip_reset_to_the_wrong_figure_is_still_wrong():
    assert not check(found=380, reset=390).left_correct()


def test_a_check_prints_what_the_wrench_found():
    assert str(check(carrier=7, found=380)) == "carrier 7 found 380 in lb slack"
    assert str(check(carrier=7, found=445)) == "carrier 7 found 445 in lb in band"


def test_only_a_fixed_grip_fleet_goes_on_a_retorque_cycle():
    with pytest.raises(RopeworkError):
        cycle(fleet=jp_carriers())


def test_a_cycle_checks_its_interval_and_its_carriers():
    with pytest.raises(RopeworkError):
        cycle(interval=0)
    with pytest.raises(RopeworkError):
        cycle(check(carrier=900))


def test_a_clean_round_leaves_the_interval_alone():
    walked = cycle(*[check(number, 445) for number in range(1, 21)])
    assert walked.slack_rate() == 0
    assert walked.interval_days() == 365


def test_one_slack_grip_in_twenty_leaves_the_interval_alone():
    walked = cycle(check(1, 380), *[check(number, 445) for number in range(2, 21)])
    assert walked.slack_rate() == Decimal("0.05")
    assert walked.interval_days() == 365


def test_more_than_one_in_ten_slack_halves_the_interval():
    walked = cycle(*[check(number, 380) for number in range(1, 4)], check(4, 445))
    assert walked.slack_rate() == Decimal("0.75")
    assert walked.interval_days() == 182


def test_the_shortened_interval_pulls_grips_into_being_overdue():
    checks = [check(number, 380, date(2024, 6, 1)) for number in range(1, 4)]
    tight = cycle(*checks)
    loose = cycle(check(1, 445, date(2024, 6, 1)))
    on = date(2025, 1, 15)
    assert 1 in tight.overdue(on)
    assert loose.overdue(on) == ()


def test_an_empty_cycle_has_no_slack_rate_and_the_base_interval():
    assert cycle().slack_rate() == 0
    assert cycle().interval_days() == 365


def test_the_last_check_on_a_carrier_is_the_most_recent_one():
    walked = cycle(
        check(3, 400, date(2023, 5, 1)),
        check(3, 445, date(2024, 5, 1)),
    )
    last = walked.last_check(3)
    assert last is not None
    assert last.checked == date(2024, 5, 1)
    assert walked.last_check(4) is None


def test_a_grip_falls_due_an_interval_after_it_was_last_done():
    walked = cycle(check(3, 445, date(2024, 5, 1)))
    assert walked.due_on(3, date(2025, 1, 15)) == date(2025, 5, 1)


def test_a_grip_never_checked_falls_due_an_interval_from_today():
    walked = cycle(check(3, 445, date(2024, 5, 1)))
    assert walked.due_on(9, date(2025, 1, 15)) == date(2026, 1, 15)
    assert 9 in walked.never_checked()
    assert 3 not in walked.never_checked()


def test_grips_left_outside_the_band_are_listed():
    walked = cycle(check(1, 380), check(2, 380, reset=450), check(3, 445))
    assert [c.carrier for c in walked.left_wrong()] == [1]


def test_adding_a_check_can_change_the_interval():
    walked = cycle(check(1, 445))
    assert walked.interval_days() == 365
    after = walked.with_check(check(2, 370))
    assert after.interval_days() == 182


def test_a_cycle_prints_a_one_line_summary():
    walked = cycle(check(1, 380), check(2, 445))
    assert str(walked) == "2 checks, 1 slack, interval 182 days"
