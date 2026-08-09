from datetime import date
from decimal import Decimal

import pytest

from hill import JOHN_PAUL, STRAWBERRY
from sheaveline.upkeep.interval import UpkeepError
from sheaveline.upkeep.partlife import FittedPart, PartKind, PartRegister
from sheaveline.upkeep.stock import (
    Shelf,
    ShelfItem,
    days_until_spent,
    shortages,
)

ON = date(2025, 10, 1)
FITTED = date(2024, 11, 1)

LINER = PartKind(code="LIN", name="sheave liner", cycles=1_000_000)
TYRE = PartKind(code="TYR", name="grip conveyor tyre", hours=4000)
SEAL = PartKind(code="SEA", name="gearbox output seal", days=730)


def item(kind=LINER, on_hand=4, lead=42, least=0):
    return ShelfItem(kind=kind, on_hand=on_hand, lead_days=lead, least=least)


def part(kind=LINER, where="JP-T4", lift=JOHN_PAUL, cycles_at=900_000, hours_at="40000.0"):
    return FittedPart(
        kind=kind,
        lift=lift,
        where=where,
        fitted=FITTED,
        hours_at_fitting=Decimal(hours_at),
        cycles_at_fitting=cycles_at,
    )


def test_a_shelf_item_cannot_hold_a_negative_number():
    with pytest.raises(UpkeepError):
        item(on_hand=-1)
    with pytest.raises(UpkeepError):
        item(lead=-1)
    with pytest.raises(UpkeepError):
        item(least=-1)


def test_what_is_spare_is_what_is_over_the_reserve():
    assert item(on_hand=4, least=2).spare() == 2
    assert item(on_hand=1, least=2).spare() == 0


def test_a_part_number_is_on_the_shelf_once():
    with pytest.raises(UpkeepError, match="on the shelf once"):
        Shelf(items=(item(), item()))


def test_the_shelf_answers_what_is_on_it():
    made = Shelf.of([item(LINER, 4), item(TYRE, 2)])
    assert len(made) == 2
    assert made.on_hand("lin") == 4
    assert made.on_hand("nothing") == 0
    assert made.item("TYR").kind is TYRE


def test_fitting_a_part_takes_one_off_the_shelf():
    made = Shelf.of([item(LINER, 4)]).took("LIN")
    assert made.on_hand("LIN") == 3
    assert made.took("LIN", 3).on_hand("LIN") == 0


def test_taking_more_than_is_there():
    with pytest.raises(UpkeepError, match="1 on hand and 2 was asked for"):
        Shelf.of([item(LINER, 1)]).took("LIN", 2)


def test_taking_something_that_is_not_stocked():
    with pytest.raises(UpkeepError, match="no TYR on the shelf"):
        Shelf.of([item(LINER)]).took("TYR")


def test_a_delivery_puts_them_back():
    made = Shelf.of([item(LINER, 1)]).received("LIN", 6)
    assert made.on_hand("LIN") == 7
    with pytest.raises(UpkeepError, match="has something in it"):
        made.received("LIN", 0)


def test_a_part_on_the_calendar_runs_out_without_any_rate():
    made = part(kind=SEAL)
    assert days_until_spent(made, ON) == (made.calendar_due() - ON).days


def test_a_part_on_cycles_needs_a_rate_before_it_can_be_projected():
    made = part(kind=LINER, cycles_at=900_000)
    assert days_until_spent(made, ON, cycles=1_500_000) is None
    assert days_until_spent(made, ON, cycles=1_500_000, cycles_a_day=4000) == 100


def test_a_part_on_hours_needs_one_too():
    made = part(kind=TYRE, hours_at="40000.0")
    assert days_until_spent(made, ON, hours=Decimal("42000.0")) is None
    assert days_until_spent(made, ON, hours=Decimal("42000.0"), hours_a_day=Decimal(8)) == 250


def test_a_part_already_spent_runs_out_today_rather_than_yesterday():
    made = part(kind=LINER, cycles_at=900_000)
    assert days_until_spent(made, ON, cycles=9_000_000, cycles_a_day=4000) == 0


def test_the_soonest_clock_is_the_one_that_ends_it():
    """A hundred days of cycles left and thirty one of calendar. The calendar wins."""
    both = PartKind(code="BOT", name="both", cycles=1_000_000, days=365)
    made = part(kind=both, cycles_at=900_000)
    assert days_until_spent(made, ON, cycles=1_500_000, cycles_a_day=4000) == 31
    assert days_until_spent(made, ON, cycles=1_500_000) == 31
    cycles_only = PartKind(code="CYC", name="cycles only", cycles=1_000_000)
    assert (
        days_until_spent(
            part(kind=cycles_only, cycles_at=900_000), ON, cycles=1_500_000, cycles_a_day=4000
        )
        == 100
    )


def test_nothing_is_short_when_the_shelf_covers_the_line():
    register = PartRegister.of([part(where="JP-T4"), part(where="JP-T5")])
    shelf = Shelf.of([item(LINER, on_hand=4)])
    assert shortages(shelf, register, JOHN_PAUL, ON, cycles=1_500_000, cycles_a_day=4000) == ()


def test_a_shelf_that_cannot_cover_the_line_is_short():
    register = PartRegister.of([part(where=f"JP-T{one}") for one in range(1, 5)])
    shelf = Shelf.of([item(LINER, on_hand=1)])
    found = shortages(shelf, register, JOHN_PAUL, ON, cycles=1_500_000, cycles_a_day=4000)
    assert len(found) == 1
    assert found[0].wanted == 4
    assert found[0].short_by() == 3
    assert found[0].estimated


def test_the_reserve_is_not_counted_as_available():
    register = PartRegister.of([part(where="JP-T4")])
    shelf = Shelf.of([item(LINER, on_hand=2, least=2)])
    found = shortages(shelf, register, JOHN_PAUL, ON, cycles=1_500_000, cycles_a_day=4000)
    assert found[0].short_by() == 1


def test_the_order_date_is_the_lead_time_before_it_is_wanted():
    register = PartRegister.of([part(where="JP-T4")])
    shelf = Shelf.of([item(LINER, on_hand=0, lead=42)])
    found = shortages(shelf, register, JOHN_PAUL, ON, cycles=1_500_000, cycles_a_day=4000)
    assert (found[0].order_by - ON).days == 100 - 42


def test_a_part_number_nobody_stocks_is_still_reported():
    register = PartRegister.of([part(where="JP-T4")])
    found = shortages(Shelf.of(), register, JOHN_PAUL, ON, cycles=1_500_000, cycles_a_day=4000)
    assert found[0].on_hand == 0
    assert found[0].kind is LINER


def test_a_part_that_runs_out_after_the_window_is_left_alone():
    register = PartRegister.of([part(where="JP-T4")])
    shelf = Shelf.of([item(LINER, on_hand=0)])
    assert (
        shortages(shelf, register, JOHN_PAUL, ON, cycles=1_500_000, cycles_a_day=4000, within=30)
        == ()
    )


def test_a_part_on_another_machine_is_not_this_machines_problem():
    register = PartRegister.of([part(where="SB-T2", lift=STRAWBERRY)])
    shelf = Shelf.of([item(LINER, on_hand=0)])
    assert shortages(shelf, register, JOHN_PAUL, ON, cycles=1_500_000, cycles_a_day=4000) == ()


def test_a_shortage_that_should_already_be_on_order_says_so():
    register = PartRegister.of([part(where="JP-T4")])
    shelf = Shelf.of([item(LINER, on_hand=0, lead=200)])
    found = shortages(shelf, register, JOHN_PAUL, ON, cycles=1_500_000, cycles_a_day=4000)
    assert found[0].is_urgent(ON)
    assert "order by" in str(found[0])
    assert "estimated" in str(found[0])


def test_a_calendar_only_shortage_is_not_labelled_an_estimate():
    register = PartRegister.of([part(kind=SEAL, where="JP-DRV")])
    shelf = Shelf.of([item(SEAL, on_hand=0)])
    found = shortages(shelf, register, JOHN_PAUL, ON, within=400)
    assert found
    assert not found[0].estimated
    assert "estimated" not in str(found[0])
