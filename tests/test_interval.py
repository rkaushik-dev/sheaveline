from datetime import date
from decimal import Decimal

import pytest

from sheaveline.plant.classes import LiftClass
from sheaveline.upkeep.interval import (
    AERIAL,
    Basis,
    Interval,
    Requirement,
    UpkeepError,
)


def test_an_interval_runs_on_at_least_one_clock():
    with pytest.raises(UpkeepError):
        Interval()
    assert Interval(days=30).bases() == frozenset({Basis.CALENDAR})
    assert Interval(hours=500).bases() == frozenset({Basis.HOURS})
    assert Interval(cycles=100000).bases() == frozenset({Basis.CYCLES})


def test_an_interval_can_run_on_two_clocks_at_once():
    both = Interval(days=365, hours=1500)
    assert both.bases() == frozenset({Basis.CALENDAR, Basis.HOURS})
    assert both.runs_on(Basis.CALENDAR)
    assert both.runs_on(Basis.HOURS)
    assert not both.runs_on(Basis.CYCLES)


def test_an_interval_of_nothing_is_refused():
    with pytest.raises(UpkeepError):
        Interval(days=0)
    with pytest.raises(UpkeepError):
        Interval(hours=-5)
    with pytest.raises(UpkeepError):
        Interval(days=30, grace_days=-1)


def test_the_named_intervals_are_what_they_say():
    assert Interval.daily().days == 1
    assert Interval.weekly().days == 7
    assert Interval.monthly().days == 30
    assert Interval.annual().days == 365
    assert Interval.five_yearly().days == 1826


def test_the_named_intervals_carry_sensible_grace():
    assert Interval.daily().grace_days == 0
    assert Interval.monthly().grace_days == 7
    assert Interval.annual().grace_days == 30


def test_the_next_one_is_counted_from_when_the_last_was_due():
    monthly = Interval.monthly()
    assert monthly.due_date(date(2025, 1, 1)) == date(2025, 1, 31)
    assert monthly.last_day_of_grace(date(2025, 1, 1)) == date(2025, 2, 7)


def test_a_job_with_no_calendar_clock_has_no_due_date():
    hourly = Interval(hours=500)
    assert hourly.due_date(date(2025, 1, 1)) is None
    assert hourly.last_day_of_grace(date(2025, 1, 1)) is None


def test_an_hour_based_job_falls_due_on_the_meter():
    hourly = Interval(hours=500)
    assert hourly.due_hours(Decimal("42000.0")) == Decimal("42500.0")
    assert Interval(days=30).due_hours(Decimal("42000.0")) is None


def test_a_cycle_based_job_falls_due_on_the_counter():
    counted = Interval(cycles=100_000)
    assert counted.due_cycles(1_000_000) == 1_100_000
    assert Interval(days=30).due_cycles(1_000_000) is None


def test_an_interval_on_two_clocks_answers_both():
    both = Interval(days=365, hours=1500)
    assert both.due_date(date(2025, 1, 1)) == date(2026, 1, 1)
    assert both.due_hours(Decimal("42000.0")) == Decimal("43500.0")


def test_an_interval_prints_every_clock_it_runs_on():
    assert str(Interval(days=30)) == "every 30 days"
    assert str(Interval(hours=500)) == "every 500 hours"
    assert str(Interval(days=365, hours=1500)) == "every 365 days or every 1500 hours"
    assert str(Interval(cycles=100_000)) == "every 100,000 cycles"


def test_a_requirement_carries_a_code_and_a_description():
    with pytest.raises(UpkeepError):
        Requirement("  ", "something", Interval.monthly())
    with pytest.raises(UpkeepError):
        Requirement("M1", "  ", Interval.monthly())


def test_a_requirement_says_which_machines_it_is_for():
    everything = Requirement("M1", "grease the sheaves", Interval.monthly())
    aerial_only = Requirement("M2", "evacuation drill", Interval.annual(), classes=AERIAL)
    assert everything.applies_to(LiftClass.SURFACE_TOW)
    assert aerial_only.applies_to(LiftClass.FIXED_CHAIR)
    assert not aerial_only.applies_to(LiftClass.SURFACE_TOW)


def test_a_requirement_prints_its_interval():
    job = Requirement("A1", "annual inspection", Interval.annual())
    assert str(job) == "A1 annual inspection, every 365 days"


def test_a_requirement_can_be_marked_as_not_critical():
    tidy = Requirement("E1", "paint the terminal", Interval.annual(), critical=False)
    assert not tidy.critical
    assert Requirement("A1", "annual", Interval.annual()).critical
