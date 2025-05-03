from datetime import date
from decimal import Decimal

import pytest

from hill import john_paul
from sheaveline.measures.length import Length
from sheaveline.plant.identity import RopeSerial
from sheaveline.plant.rope import HaulRope
from sheaveline.ropework.mrt import (
    Indication,
    IndicationKind,
    MagneticTest,
    RetestSchedule,
)
from sheaveline.ropework.wear import RopeworkError, WearVerdict

ROPE = john_paul().rope


def other_rope() -> HaulRope:
    return HaulRope(
        serial=RopeSerial.parse("BRI-2019-00777"),
        spec=ROPE.spec,
        installed=ROPE.installed,
        length=ROPE.length,
        splices=1,
    )


def magnetic_test(*indications, rope=ROPE, taken=date(2025, 1, 15)):
    return MagneticTest.of(rope, taken, "Wire Rope Diagnostics", "LMA-250", indications)


def loss(station_feet, percent):
    return Indication.loss(Length.from_feet(station_feet), percent)


def fault(station_feet, wires):
    return Indication.fault(Length.from_feet(station_feet), wires)


def test_a_loss_indication_carries_a_percentage():
    mark = loss(1200, "4.5")
    assert mark.kind is IndicationKind.LOSS_OF_AREA
    assert mark.magnitude == Decimal("4.5")
    assert str(mark) == "4.5% loss of metallic area at 1200 ft"


def test_a_local_fault_is_counted_in_whole_wires():
    mark = fault(2400, 3)
    assert mark.kind is IndicationKind.LOCAL_FAULT
    assert not mark.kind.is_measured_as_percent
    assert str(mark) == "3 wire local fault at 2400 ft"


def test_an_indication_checks_its_own_numbers():
    with pytest.raises(RopeworkError):
        Indication.loss(Length.from_feet(-1), "3")
    with pytest.raises(RopeworkError):
        Indication.loss(Length.from_feet(0), "0")
    with pytest.raises(RopeworkError):
        Indication.loss(Length.from_feet(0), "140")
    with pytest.raises(RopeworkError):
        Indication(Length.from_feet(0), IndicationKind.LOCAL_FAULT, Decimal("2.5"))


def test_a_test_names_the_outfit_and_the_instrument():
    with pytest.raises(RopeworkError):
        MagneticTest.of(ROPE, date(2025, 1, 15), " ", "LMA-250")
    with pytest.raises(RopeworkError):
        MagneticTest.of(ROPE, date(2025, 1, 15), "Wire Rope Diagnostics", " ")


def test_a_test_cannot_predate_the_rope_or_run_past_its_end():
    with pytest.raises(RopeworkError):
        MagneticTest.of(ROPE, date(2015, 1, 1), "WRD", "LMA-250")
    with pytest.raises(RopeworkError):
        magnetic_test(loss(12000, "2"))


def test_a_clean_trace_is_serviceable():
    assert magnetic_test().verdict() is WearVerdict.SERVICEABLE
    assert magnetic_test().peak_loss() == 0
    assert magnetic_test().largest_fault() == 0


def test_the_peak_loss_is_the_worst_one_on_the_trace():
    walked = magnetic_test(loss(600, "2.1"), loss(1800, "7.4"), loss(3000, "3.0"))
    assert walked.peak_loss() == Decimal("7.4")
    assert walked.verdict() is WearVerdict.MONITOR


def test_a_loss_over_ten_percent_condemns_the_rope():
    assert magnetic_test(loss(1800, "10.4")).verdict() is WearVerdict.CONDEMNED
    assert magnetic_test(loss(1800, "10.0")).verdict() is WearVerdict.MONITOR


def test_local_faults_are_read_separately_from_losses():
    walked = magnetic_test(loss(600, "2.0"), fault(2400, 3), fault(3000, 5))
    assert walked.largest_fault() == 5
    assert walked.verdict() is WearVerdict.CONDEMNED


def test_a_small_fault_only_puts_the_rope_on_watch():
    assert magnetic_test(fault(2400, 3)).verdict() is WearVerdict.MONITOR
    assert magnetic_test(fault(2400, 2)).verdict() is WearVerdict.SERVICEABLE


def test_indications_can_be_pulled_out_by_kind():
    walked = magnetic_test(loss(600, "2.0"), fault(2400, 3))
    assert len(walked.of_kind(IndicationKind.LOSS_OF_AREA)) == 1
    assert len(walked.of_kind(IndicationKind.LOCAL_FAULT)) == 1
    assert walked.of_kind(IndicationKind.CORROSION) == ()


def test_an_untested_rope_is_due_four_years_after_it_went_on():
    schedule = RetestSchedule(ROPE)
    assert schedule.next_due(date(2021, 1, 1)) == date(2023, 10, 4)


def test_a_rope_under_ten_gets_two_years_between_tests():
    schedule = RetestSchedule(ROPE, last_test=date(2023, 10, 4))
    assert schedule.interval_years(date(2025, 1, 15)) == 2
    assert schedule.next_due(date(2025, 1, 15)) == date(2025, 10, 4)


def test_a_rope_past_ten_gets_one_year():
    schedule = RetestSchedule(ROPE, last_test=date(2029, 11, 1))
    assert schedule.interval_years(date(2030, 6, 1)) == 1
    assert schedule.next_due(date(2030, 6, 1)) == date(2030, 11, 1)


def test_the_interval_turns_on_the_age_at_the_time_of_asking():
    schedule = RetestSchedule(ROPE, last_test=date(2029, 6, 1))
    assert schedule.next_due(date(2029, 7, 1)) == date(2031, 6, 1)
    assert schedule.next_due(date(2030, 1, 1)) == date(2030, 6, 1)


def test_an_overdue_rope_says_so():
    schedule = RetestSchedule(ROPE, last_test=date(2022, 10, 4))
    assert schedule.is_overdue(date(2025, 1, 15))
    assert not schedule.is_overdue(date(2024, 6, 1))


def test_the_days_until_due_count_down():
    schedule = RetestSchedule(ROPE, last_test=date(2024, 10, 4))
    assert schedule.days_until_due(date(2026, 10, 3)) == 1
    assert schedule.days_until_due(date(2026, 10, 5)) == -1


def test_recording_a_test_moves_the_schedule_on():
    schedule = RetestSchedule(ROPE).after(magnetic_test(taken=date(2024, 3, 5)))
    assert schedule.last_test == date(2024, 3, 5)
    assert schedule.next_due(date(2024, 6, 1)) == date(2026, 3, 5)


def test_a_test_of_another_rope_does_not_move_this_schedule():
    with pytest.raises(RopeworkError):
        RetestSchedule(ROPE).after(magnetic_test(rope=other_rope()))


def test_a_last_test_before_the_rope_went_on_is_refused():
    with pytest.raises(RopeworkError):
        RetestSchedule(ROPE, last_test=date(2015, 1, 1))


def test_a_test_prints_its_verdict():
    assert str(magnetic_test()) == "FAT-2019-04412 tested 2025-01-15, serviceable"
