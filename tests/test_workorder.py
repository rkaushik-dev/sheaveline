from datetime import date

import pytest

from hill import JOHN_PAUL, STRAWBERRY
from sheaveline.upkeep.interval import UpkeepError
from sheaveline.upkeep.record import Defect, DefectLevel
from sheaveline.upkeep.workorder import (
    Priority,
    WorkOrder,
    WorkOrderBook,
    priority_for,
)

RAISED = date(2025, 1, 15)


def defect(level=DefectLevel.DEFECT, where="JP-T4", detail="sheave liner cracked"):
    return Defect(where, level, detail, RAISED)


def order(number=1, priority=Priority.SCHEDULED, raised=RAISED, closed=None, lift=JOHN_PAUL):
    return WorkOrder(
        number=number,
        lift=lift,
        raised=raised,
        raised_by="M Vetter",
        detail="sheave liner cracked",
        priority=priority,
        where="JP-T4",
        closed=closed,
        closed_by=None if closed is None else "M Vetter",
    )


def test_a_defect_level_decides_the_priority():
    assert priority_for(DefectLevel.SERIOUS) is Priority.IMMEDIATE
    assert priority_for(DefectLevel.DEFECT) is Priority.SCHEDULED
    assert priority_for(DefectLevel.OBSERVATION) is Priority.ROUTINE


def test_each_priority_carries_its_own_target():
    assert Priority.IMMEDIATE.target_days == 0
    assert Priority.URGENT.target_days == 3
    assert Priority.SCHEDULED.target_days == 30
    assert Priority.ROUTINE.target_days == 180


def test_only_an_immediate_order_stops_the_machine():
    assert Priority.IMMEDIATE.stops_the_machine
    for priority in (Priority.URGENT, Priority.SCHEDULED, Priority.ROUTINE):
        assert not priority.stops_the_machine


def test_an_order_raised_from_a_defect_takes_its_priority_from_it():
    made = WorkOrder.from_defect(1, JOHN_PAUL, defect(DefectLevel.SERIOUS), "M Vetter")
    assert made.priority is Priority.IMMEDIATE
    assert made.where == "JP-T4"
    assert made.raised == RAISED


def test_an_order_checks_its_own_record():
    with pytest.raises(UpkeepError):
        order(number=0)
    with pytest.raises(UpkeepError):
        WorkOrder(1, JOHN_PAUL, RAISED, " ", "a thing", Priority.ROUTINE)
    with pytest.raises(UpkeepError):
        WorkOrder(1, JOHN_PAUL, RAISED, "M Vetter", "  ", Priority.ROUTINE)


def test_an_order_cannot_close_before_it_was_raised():
    with pytest.raises(UpkeepError):
        order(closed=date(2025, 1, 1))


def test_an_order_is_closed_by_somebody_on_some_day_or_neither():
    with pytest.raises(UpkeepError):
        WorkOrder(1, JOHN_PAUL, RAISED, "M Vetter", "a thing", Priority.ROUTINE, closed=RAISED)


def test_the_due_date_falls_out_of_the_priority():
    assert order(priority=Priority.IMMEDIATE).due_by() == RAISED
    assert order(priority=Priority.URGENT).due_by() == date(2025, 1, 18)
    assert order(priority=Priority.SCHEDULED).due_by() == date(2025, 2, 14)


def test_an_open_order_goes_overdue_when_its_date_passes():
    urgent = order(priority=Priority.URGENT)
    assert not urgent.is_overdue(date(2025, 1, 18))
    assert urgent.is_overdue(date(2025, 1, 19))


def test_a_closed_order_is_overdue_only_if_it_was_closed_late():
    on_time = order(priority=Priority.URGENT, closed=date(2025, 1, 17))
    late = order(priority=Priority.URGENT, closed=date(2025, 1, 25))
    assert not on_time.is_overdue(date(2025, 3, 1))
    assert late.is_overdue(date(2025, 3, 1))


def test_an_order_reports_its_age():
    assert order().age_in_days(date(2025, 2, 14)) == 30
    assert order(closed=date(2025, 1, 20)).age_in_days(date(2025, 3, 1)) == 5
    with pytest.raises(UpkeepError):
        order().age_in_days(date(2025, 1, 1))


def test_closing_an_order_makes_a_new_one():
    open_order = order()
    closed = open_order.close(date(2025, 1, 20), "M Vetter")
    assert open_order.is_open()
    assert not closed.is_open()
    with pytest.raises(UpkeepError):
        closed.close(date(2025, 1, 25), "M Vetter")
    with pytest.raises(UpkeepError):
        open_order.close(date(2025, 1, 20), "  ")


def test_an_order_prints_a_one_liner():
    assert str(order()) == "1 scheduled on JP, sheave liner cracked (open)"


def test_a_book_hands_out_numbers_from_one():
    book = WorkOrderBook.of()
    assert book.next_number() == 1
    book = book.raise_from(JOHN_PAUL, defect(), "M Vetter")
    assert book.next_number() == 2
    assert len(book) == 1


def test_two_orders_cannot_share_a_number():
    with pytest.raises(UpkeepError):
        WorkOrderBook.of([order(number=1), order(number=1)])


def test_an_order_can_be_fetched_or_refused_by_number():
    book = WorkOrderBook.of([order(number=1)])
    assert book.get(1).number == 1
    with pytest.raises(UpkeepError):
        book.get(9)


def test_closing_through_the_book_keeps_the_order():
    book = WorkOrderBook.of([order(number=1), order(number=2)])
    after = book.close(1, date(2025, 1, 20), "M Vetter")
    assert [o.number for o in after.orders] == [1, 2]
    assert not after.get(1).is_open()
    assert after.get(2).is_open()


def test_the_open_orders_can_be_narrowed_to_one_machine():
    book = WorkOrderBook.of([order(number=1, lift=JOHN_PAUL), order(number=2, lift=STRAWBERRY)])
    assert len(book.open_orders()) == 2
    assert len(book.open_orders(JOHN_PAUL)) == 1


def test_the_overdue_orders_can_be_narrowed_the_same_way():
    book = WorkOrderBook.of(
        [
            order(number=1, priority=Priority.URGENT, lift=JOHN_PAUL),
            order(number=2, priority=Priority.ROUTINE, lift=STRAWBERRY),
        ]
    )
    late = book.overdue(date(2025, 2, 1))
    assert [o.number for o in late] == [1]
    assert book.overdue(date(2025, 2, 1), STRAWBERRY) == ()


def test_an_open_immediate_order_holds_the_machine():
    book = WorkOrderBook.of([order(number=1, priority=Priority.IMMEDIATE)])
    assert JOHN_PAUL in book.holding_machines()
    after = book.close(1, date(2025, 1, 16), "M Vetter")
    assert after.holding_machines() == frozenset()


def test_orders_can_be_pulled_out_by_priority_and_by_place():
    book = WorkOrderBook.of(
        [order(number=1, priority=Priority.URGENT), order(number=2, priority=Priority.ROUTINE)]
    )
    assert [o.number for o in book.of_priority(Priority.URGENT)] == [1]
    assert len(book.at("JP-T4")) == 2
    assert book.at("JP-T9") == ()


def test_a_serious_defect_raised_as_an_order_holds_the_machine_at_once():
    book = WorkOrderBook.of().raise_from(JOHN_PAUL, defect(DefectLevel.SERIOUS), "M Vetter")
    assert book.holding_machines() == frozenset({JOHN_PAUL})
    assert book.get(1).is_overdue(date(2025, 1, 16))
