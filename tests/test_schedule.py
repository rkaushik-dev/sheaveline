from datetime import date
from decimal import Decimal

import pytest

from hill import JOHN_PAUL, STRAWBERRY
from sheaveline.ops.meters import MeterHistory, MeterReading
from sheaveline.plant.classes import LiftClass
from sheaveline.upkeep.interval import Basis, UpkeepError
from sheaveline.upkeep.record import InspectionRecord, RecordBook
from sheaveline.upkeep.schedule import UpkeepSchedule

BUILT = date(2024, 11, 1)


def record(code, done, hours=None, cycles=None):
    return InspectionRecord.of(JOHN_PAUL, code, done, "M Vetter", hours, cycles)


def schedule(*records, meter=None, lift_class=LiftClass.DETACHABLE_CHAIR, built=BUILT):
    return UpkeepSchedule(
        lift=JOHN_PAUL,
        lift_class=lift_class,
        commissioned=built,
        book=RecordBook.of(JOHN_PAUL, records),
        meter=meter,
    )


def meter(hours="42000.0", cycles=1_000_000, day=15):
    return MeterHistory.of(
        JOHN_PAUL,
        [MeterReading(JOHN_PAUL, date(2025, 1, day), Decimal(hours), cycles, "M Vetter")],
    )


def test_a_schedule_carries_the_jobs_its_class_has():
    quad = schedule()
    tow = schedule(lift_class=LiftClass.SURFACE_TOW)
    assert len(quad.jobs()) > len(tow.jobs())
    assert str(quad).startswith("JP ladder")


def test_a_book_or_a_meter_from_another_machine_is_refused():
    with pytest.raises(UpkeepError):
        UpkeepSchedule(JOHN_PAUL, LiftClass.FIXED_CHAIR, BUILT, RecordBook.of(STRAWBERRY))


def test_a_job_that_is_not_on_this_class_has_no_due_date():
    with pytest.raises(UpkeepError):
        schedule(lift_class=LiftClass.SURFACE_TOW).due_item("W2")


def test_a_job_never_done_is_due_an_interval_after_the_machine_was_built():
    item = schedule().due_item("M1")
    assert item.due_on == date(2024, 12, 1)
    assert not item.ever_done
    assert item in schedule().never_done()


def test_a_record_from_before_the_machine_was_built_is_refused():
    with pytest.raises(UpkeepError):
        schedule(record("M1", date(2024, 1, 1)))


def test_a_job_done_early_counts_from_the_day_it_was_done():
    walked = schedule(record("A1", date(2025, 1, 6)))
    assert walked.due_item("A1").due_on == date(2026, 1, 6)


def test_a_job_done_on_time_moves_on_by_one_interval():
    walked = schedule(record("M1", date(2024, 12, 1)))
    assert walked.due_item("M1").due_on == date(2024, 12, 31)


def test_a_job_done_inside_grace_keeps_the_old_anchor():
    walked = schedule(record("M1", date(2024, 12, 6)))
    assert walked.due_item("M1").due_on == date(2024, 12, 31)


def test_a_job_done_outside_grace_restarts_the_chain():
    walked = schedule(record("M1", date(2024, 12, 20)))
    assert walked.due_item("M1").due_on == date(2025, 1, 19)


def test_the_ladder_does_not_walk_later_every_month():
    walked = schedule(
        record("M1", date(2024, 12, 4)),
        record("M1", date(2025, 1, 5)),
        record("M1", date(2025, 2, 3)),
    )
    assert walked.due_item("M1").due_on == date(2025, 3, 1)


def test_an_overdue_job_is_reported_with_the_clock_that_fired():
    walked = schedule(record("M1", date(2024, 12, 1)))
    on = date(2025, 1, 15)
    item = walked.due_item("M1")
    assert item.is_overdue(on)
    assert item.fired(on) == frozenset({Basis.CALENDAR})
    assert item in walked.overdue(on)


def test_a_job_inside_its_interval_is_not_overdue():
    walked = schedule(record("M1", date(2025, 1, 10)))
    assert not walked.due_item("M1").is_overdue(date(2025, 1, 15))


def test_an_hour_based_job_fires_on_the_meter_and_not_on_the_calendar():
    walked = schedule(
        record("H1", date(2024, 12, 1), hours=Decimal("41000.0")),
        meter=meter(hours="42600.0"),
    )
    item = walked.due_item("H1")
    assert item.due_at_hours == Decimal("42500.0")
    assert item.hours_overdue(Decimal("42600.0"))
    assert not item.calendar_overdue(date(2025, 1, 15))
    assert item.fired(date(2025, 1, 15), Decimal("42600.0")) == frozenset({Basis.HOURS})


def test_the_same_job_can_fire_on_the_calendar_instead():
    walked = schedule(
        record("H2", date(2023, 6, 1), hours=Decimal("41000.0")),
        meter=meter(hours="41200.0"),
        built=date(2022, 1, 1),
    )
    item = walked.due_item("H2")
    assert item.calendar_overdue(date(2025, 1, 15))
    assert not item.hours_overdue(Decimal("41200.0"))


def test_both_clocks_can_fire_at_once():
    walked = schedule(
        record("H2", date(2023, 6, 1), hours=Decimal("41000.0")),
        meter=meter(hours="43000.0"),
        built=date(2022, 1, 1),
    )
    fired = walked.due_item("H2").fired(date(2025, 1, 15), Decimal("43000.0"))
    assert fired == frozenset({Basis.CALENDAR, Basis.HOURS})


def test_a_cycle_based_job_fires_on_the_counter():
    walked = schedule(
        record("C1", date(2024, 12, 1), cycles=700_000),
        meter=meter(cycles=1_300_000),
    )
    item = walked.due_item("C1")
    assert item.due_at_cycles == 1_200_000
    assert item.cycles_overdue(1_300_000)


def test_without_a_meter_the_meter_clocks_never_fire():
    walked = schedule(record("H1", date(2024, 12, 1), hours=Decimal("41000.0")))
    item = walked.due_item("H1")
    assert walked.current_hours() is None
    assert not item.hours_overdue(None)


def test_the_overdue_list_reads_the_meter_the_schedule_holds():
    walked = schedule(
        record("H1", date(2024, 12, 1), hours=Decimal("41000.0")),
        meter=meter(hours="42600.0"),
    )
    codes = {item.job.code for item in walked.overdue(date(2024, 12, 15))}
    assert "H1" in codes


def test_only_the_critical_overdue_jobs_stop_a_machine_being_current():
    walked = schedule(record("A5", date(2023, 1, 1)), built=date(2022, 1, 1))
    on = date(2025, 1, 15)
    assert any(item.job.code == "A5" for item in walked.overdue(on))
    assert not any(item.job.code == "A5" for item in walked.overdue_critical(on))
    assert not walked.is_current(on)


def test_a_machine_with_everything_fresh_is_current():
    on = date(2025, 1, 15)
    records = []
    for job in schedule().jobs():
        records.append(
            record(
                job.code,
                date(2025, 1, 14),
                hours=Decimal("42000.0"),
                cycles=1_000_000,
            )
        )
    walked = schedule(*records, meter=meter())
    assert walked.is_current(on)
    assert walked.never_done() == ()


def test_the_jobs_falling_due_this_week_can_be_listed():
    walked = schedule(record("M1", date(2025, 1, 1)))
    soon = walked.due_within(date(2025, 1, 25), days=10)
    assert any(item.job.code == "M1" for item in soon)
    assert walked.due_item("M1").days_until(date(2025, 1, 25)) == 6


def test_a_due_item_prints_when_it_is_due():
    item = schedule(record("M1", date(2025, 1, 1))).due_item("M1")
    assert str(item) == "M1 due 2025-01-31"
