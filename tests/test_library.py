import pytest

from sheaveline.plant.classes import LiftClass
from sheaveline.upkeep.interval import Basis, UpkeepError
from sheaveline.upkeep.library import (
    STANDARD_LIBRARY,
    codes_for,
    critical_codes_for,
    library_for,
    requirement,
)


def test_every_code_in_the_library_is_unique():
    codes = [job.code for job in STANDARD_LIBRARY]
    assert len(set(codes)) == len(codes)


def test_every_job_belongs_to_at_least_one_class():
    for job in STANDARD_LIBRARY:
        assert job.classes


def test_every_code_starts_with_the_letter_for_its_interval():
    for job in STANDARD_LIBRARY:
        letter = job.code[0]
        if letter == "D":
            assert job.interval.days == 1
        elif letter == "W":
            assert job.interval.days == 7
        elif letter == "M":
            assert job.interval.days == 30
        elif letter == "A":
            assert job.interval.days == 365
        elif letter == "F":
            assert job.interval.days == 1826
        elif letter == "H":
            assert job.interval.runs_on(Basis.HOURS)
        elif letter == "C":
            assert job.interval.runs_on(Basis.CYCLES)
        else:
            raise AssertionError(f"{job.code} does not follow the naming")


def test_a_job_can_be_fetched_by_code_in_either_case():
    assert requirement("A1").description.startswith("annual inspection")
    assert requirement("a1") == requirement("A1")


def test_asking_for_a_job_that_is_not_in_the_library():
    with pytest.raises(UpkeepError):
        requirement("Z9")


def test_a_surface_tow_carries_far_fewer_jobs_than_a_detachable():
    tow = codes_for(LiftClass.SURFACE_TOW)
    quad = codes_for(LiftClass.DETACHABLE_CHAIR)
    assert len(tow) < len(quad)
    assert set(tow) < set(quad)


def test_the_evacuation_jobs_are_only_on_aerial_machines():
    for code in ("M3", "A4"):
        assert code in codes_for(LiftClass.FIXED_CHAIR)
        assert code not in codes_for(LiftClass.SURFACE_TOW)


def test_the_grip_jobs_are_only_on_detachables():
    for code in ("W2", "C1"):
        assert code in codes_for(LiftClass.DETACHABLE_CHAIR)
        assert code not in codes_for(LiftClass.FIXED_CHAIR)


def test_the_daily_jobs_are_on_every_machine():
    for lift_class in LiftClass:
        codes = codes_for(lift_class)
        assert {"D1", "D2", "D3"} <= set(codes)


def test_the_library_for_a_class_matches_what_applies_to_it():
    for lift_class in LiftClass:
        for job in library_for(lift_class):
            assert job.applies_to(lift_class)


def test_only_the_paint_job_is_not_critical():
    for lift_class in LiftClass:
        soft = set(codes_for(lift_class)) - set(critical_codes_for(lift_class))
        assert soft == {"A5"}


def test_the_two_clock_jobs_carry_both_a_calendar_and_a_meter():
    for code in ("H1", "H2", "C1"):
        job = requirement(code)
        assert job.interval.runs_on(Basis.CALENDAR)
        assert len(job.interval.bases()) == 2


def test_the_hour_based_jobs_are_the_ones_with_hours_on_them():
    hourly = [job.code for job in STANDARD_LIBRARY if job.interval.runs_on(Basis.HOURS)]
    assert hourly == ["H1", "H2"]


def test_the_five_yearly_jobs_are_the_shaft_tests():
    five = [job.code for job in STANDARD_LIBRARY if job.interval.days == 1826]
    assert five == ["F1", "F2"]


def test_a_daily_job_has_no_grace_and_an_annual_one_does():
    assert requirement("D1").interval.grace_days == 0
    assert requirement("A1").interval.grace_days == 30


def test_every_description_reads_as_an_instruction():
    for job in STANDARD_LIBRARY:
        assert job.description == job.description.strip()
        assert job.description[0].islower()
