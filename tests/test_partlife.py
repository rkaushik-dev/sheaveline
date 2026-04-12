from datetime import date
from decimal import Decimal

import pytest

from hill import JOHN_PAUL, STRAWBERRY
from sheaveline.upkeep.interval import Basis, UpkeepError
from sheaveline.upkeep.partlife import FittedPart, PartKind, PartRegister

FITTED = date(2024, 11, 1)
ON = date(2025, 1, 15)


def kind(code="LIN", name="sheave liner", hours=None, cycles=1_000_000, days=None):
    return PartKind(code=code, name=name, hours=hours, cycles=cycles, days=days)


def part(
    part_kind=None,
    lift=JOHN_PAUL,
    where="JP-T4",
    fitted=FITTED,
    hours_at=Decimal("40000.0"),
    cycles_at=900_000,
):
    return FittedPart(
        kind=part_kind or kind(),
        lift=lift,
        where=where,
        fitted=fitted,
        hours_at_fitting=hours_at,
        cycles_at_fitting=cycles_at,
    )


def test_a_part_kind_wears_out_on_at_least_one_clock():
    with pytest.raises(UpkeepError):
        PartKind("LIN", "sheave liner")
    with pytest.raises(UpkeepError):
        kind(code="  ")
    with pytest.raises(UpkeepError):
        kind(name="")
    with pytest.raises(UpkeepError):
        kind(cycles=0)


def test_a_part_kind_names_the_clocks_it_runs_on():
    assert kind(cycles=1000).bases() == frozenset({Basis.CYCLES})
    assert kind(hours=500, cycles=None).bases() == frozenset({Basis.HOURS})
    both = kind(hours=500, cycles=1000, days=365)
    assert both.bases() == {Basis.HOURS, Basis.CYCLES, Basis.CALENDAR}


def test_a_part_kind_prints_every_life_it_has():
    printed = str(kind(hours=500, cycles=1_000_000, days=365))
    assert "500 hours" in printed
    assert "1,000,000 cycles" in printed
    assert "365 days" in printed


def test_a_fitted_part_is_fitted_somewhere():
    with pytest.raises(UpkeepError):
        part(where="  ")
    with pytest.raises(UpkeepError):
        part(hours_at=Decimal(-1))


def test_the_usage_is_measured_from_the_meters_at_fitting():
    one = part(cycles_at=900_000)
    assert one.cycles_used(1_400_000) == 500_000
    assert one.hours_used(Decimal("40500.0")) == Decimal("500.0")
    assert one.days_used(ON) == 75


def test_a_meter_that_has_gone_backwards_is_refused():
    one = part()
    with pytest.raises(UpkeepError):
        one.cycles_used(800_000)
    with pytest.raises(UpkeepError):
        one.hours_used(Decimal("39000.0"))
    with pytest.raises(UpkeepError):
        one.days_used(date(2024, 1, 1))


def test_half_a_life_reads_as_half_used():
    one = part(part_kind=kind(cycles=1_000_000), cycles_at=900_000)
    assert one.used(ON, cycles=1_400_000) == Decimal("0.5")


def test_a_part_at_the_end_of_its_life_is_due():
    one = part(part_kind=kind(cycles=1_000_000), cycles_at=900_000)
    assert not one.is_due(ON, cycles=1_800_000)
    assert one.is_due(ON, cycles=1_900_000)


def test_a_part_at_ninety_percent_wants_ordering():
    one = part(part_kind=kind(cycles=1_000_000), cycles_at=900_000)
    assert not one.should_be_ordered(ON, cycles=1_700_000)
    assert one.should_be_ordered(ON, cycles=1_800_000)


def test_the_worst_clock_is_the_one_that_ends_the_part():
    both = part(
        part_kind=kind(hours=1000, cycles=1_000_000, days=365),
        hours_at=Decimal("40000.0"),
        cycles_at=900_000,
    )
    # A quarter of the hours, a tenth of the cycles, a fifth of the calendar.
    used = both.used(ON, hours=Decimal("40250.0"), cycles=1_000_000)
    assert used == Decimal("0.25")
    assert both.spent_clock(ON, hours=Decimal("40250.0"), cycles=1_000_000) is Basis.HOURS


def test_the_calendar_can_be_the_clock_that_ends_it():
    perishing = part(
        part_kind=kind(hours=100_000, cycles=100_000_000, days=90),
        hours_at=Decimal("40000.0"),
        cycles_at=900_000,
    )
    assert perishing.spent_clock(ON) is Basis.CALENDAR
    assert not perishing.is_due(ON)
    assert perishing.is_due(date(2025, 2, 15))


def test_a_clock_not_given_is_read_as_one_that_has_not_moved():
    one = part(part_kind=kind(cycles=1_000_000), cycles_at=900_000)
    assert one.used(ON) == 0
    assert one.readings(None, None) == (Decimal("40000.0"), 900_000)
    assert one.readings(Decimal("41000.0"), 950_000) == (Decimal("41000.0"), 950_000)


def test_a_clock_the_part_does_not_run_on_counts_for_nothing():
    one = part(part_kind=kind(cycles=1_000_000))
    assert one.used_on(Basis.HOURS, ON, Decimal("99999.0")) == 0
    assert one.used_on(Basis.CALENDAR, ON) == 0


def test_the_calendar_due_date_is_worked_out_where_there_is_one():
    assert part(part_kind=kind(days=90)).calendar_due() == date(2025, 1, 30)
    assert part(part_kind=kind(cycles=1000)).calendar_due() is None


def test_the_register_keeps_parts_by_machine():
    held = PartRegister.of([part(lift=JOHN_PAUL), part(lift=STRAWBERRY, where="SB-T2")])
    assert len(held) == 2
    assert len(held.on_lift(JOHN_PAUL)) == 1
    assert held.on_lift(STRAWBERRY)[0].where == "SB-T2"


def test_the_parts_that_are_due_are_listed():
    held = PartRegister.of(
        [
            part(where="JP-T4", part_kind=kind(cycles=1_000_000), cycles_at=900_000),
            part(where="JP-T5", part_kind=kind(cycles=10_000_000), cycles_at=900_000),
        ]
    )
    due = held.due(JOHN_PAUL, ON, cycles=1_950_000)
    assert [one.where for one in due] == ["JP-T4"]


def test_the_parts_to_order_are_the_ones_close_but_not_yet_done():
    held = PartRegister.of(
        [
            part(where="JP-T4", part_kind=kind(cycles=1_000_000), cycles_at=900_000),
            part(where="JP-T5", part_kind=kind(cycles=1_000_000), cycles_at=1_000_000),
        ]
    )
    at = 1_950_000
    assert [one.where for one in held.due(JOHN_PAUL, ON, cycles=at)] == ["JP-T4"]
    assert [one.where for one in held.to_order(JOHN_PAUL, ON, cycles=at)] == ["JP-T5"]


def test_the_worst_part_on_a_machine_is_found():
    held = PartRegister.of(
        [
            part(where="JP-T4", part_kind=kind(cycles=1_000_000), cycles_at=900_000),
            part(where="JP-T5", part_kind=kind(cycles=10_000_000), cycles_at=900_000),
        ]
    )
    worst = held.worst(JOHN_PAUL, ON, cycles=1_500_000)
    assert worst is not None
    assert worst.where == "JP-T4"
    assert held.worst(STRAWBERRY, ON) is None


def test_replacing_a_part_puts_the_new_one_on_and_takes_the_old_one_off():
    going = part(where="JP-T4")
    held = PartRegister.of([going])
    coming = part(where="JP-T4", fitted=ON, cycles_at=1_500_000)
    after = held.replace(going, coming)
    assert len(after) == 1
    assert after.on_lift(JOHN_PAUL)[0].fitted == ON


def test_replacing_a_part_that_is_not_on_the_register():
    with pytest.raises(UpkeepError):
        PartRegister.of().replace(part(), part())


def test_a_part_prints_where_it_is_and_when_it_went_on():
    assert str(part()) == "LIN at JP-T4, fitted 2024-11-01"
