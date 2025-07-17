from datetime import date
from decimal import Decimal

import pytest

from hill import JOHN_PAUL, STRAWBERRY
from sheaveline.measures.duration import Duration
from sheaveline.measures.length import Length
from sheaveline.measures.speed import Speed
from sheaveline.ops.meters import MeterHistory, MeterReading
from sheaveline.ops.preop import OperationsError


def reading(day=1, hours="42000.0", cycles=1_000_000, replaced=False, lift=JOHN_PAUL):
    return MeterReading(
        lift=lift,
        day=date(2025, 1, day),
        hours=Decimal(hours),
        cycles=cycles,
        read_by="M Vetter",
        meter_replaced=replaced,
    )


def history(*readings):
    return MeterHistory.of(JOHN_PAUL, readings or [reading()])


def test_a_reading_is_signed_and_inside_the_dial():
    with pytest.raises(OperationsError):
        MeterReading(JOHN_PAUL, date(2025, 1, 1), Decimal("100.0"), 5, "  ")
    with pytest.raises(OperationsError):
        reading(hours="-1")
    with pytest.raises(OperationsError):
        reading(hours="100000.0")
    with pytest.raises(OperationsError):
        reading(cycles=-1)


def test_a_reading_converts_to_running_time():
    assert reading(hours="10.5").running_time() == Duration.from_seconds(37800)


def test_a_reading_prints_for_the_book():
    assert str(reading(hours="42000.0")) == "JP 2025-01-01, 42000.0 hours, 1,000,000 cycles"


def test_a_history_belongs_to_one_machine_and_one_reading_a_day():
    with pytest.raises(OperationsError):
        MeterHistory.of(JOHN_PAUL, [reading(lift=STRAWBERRY)])
    with pytest.raises(OperationsError):
        history(reading(day=1), reading(day=1, hours="42001.0"))
    with pytest.raises(OperationsError):
        MeterHistory.of(JOHN_PAUL, [])


def test_a_history_sorts_by_day():
    held = history(reading(day=3, hours="42020.0"), reading(day=1, hours="42000.0"))
    assert [r.day.day for r in held.readings] == [1, 3]
    assert held.first().hours == Decimal("42000.0")
    assert held.latest().hours == Decimal("42020.0")


def test_the_ordinary_step_is_the_difference():
    held = history(reading(day=1, hours="42000.0"), reading(day=2, hours="42009.5"))
    assert held.hours_run() == Decimal("9.5")


def test_a_wrapped_meter_is_read_as_a_wrap_not_a_hundred_thousand_hours():
    held = history(
        reading(day=1, hours="99995.0", cycles=1_000_000),
        reading(day=2, hours="4.5", cycles=1_000_100),
    )
    assert held.hours_run() == Decimal("9.5")
    assert held.rollovers() == 1


def test_a_replaced_meter_is_read_from_zero():
    held = history(
        reading(day=1, hours="42000.0", cycles=1_000_000),
        reading(day=2, hours="9.5", cycles=100, replaced=True),
    )
    assert held.hours_run() == Decimal("9.5")
    assert held.rollovers() == 0
    assert len(held.replacements()) == 1


def test_a_cycle_counter_that_falls_without_a_replacement_is_an_error():
    held = history(
        reading(day=1, cycles=1_000_000),
        reading(day=2, hours="42009.5", cycles=900_000),
    )
    with pytest.raises(OperationsError):
        held.cycles_run()


def test_cycles_add_up_across_a_replacement():
    held = history(
        reading(day=1, hours="42000.0", cycles=1_000_000),
        reading(day=2, hours="42009.5", cycles=1_000_400),
        reading(day=3, hours="9.5", cycles=300, replaced=True),
    )
    assert held.cycles_run() == 700


def test_two_readings_the_wrong_way_round_are_refused():
    held = history(reading(day=1), reading(day=2, hours="42009.5"))
    with pytest.raises(OperationsError):
        held.step(held.readings[1], held.readings[0])
    with pytest.raises(OperationsError):
        held.cycle_step(held.readings[1], held.readings[0])


def test_a_days_running_needs_the_day_before_it():
    held = history(
        reading(day=1, hours="42000.0"),
        reading(day=2, hours="42009.5"),
        reading(day=3, hours="42018.0"),
    )
    assert held.hours_on(date(2025, 1, 2)) == Decimal("9.5")
    assert held.hours_on(date(2025, 1, 3)) == Decimal("8.5")
    with pytest.raises(OperationsError):
        held.hours_on(date(2025, 1, 1))


def test_a_day_that_was_not_read_has_no_answer():
    with pytest.raises(OperationsError):
        history().on(date(2025, 2, 1))


def test_rope_travel_comes_off_the_hours_and_the_speed():
    held = history(reading(day=1, hours="42000.0"), reading(day=2, hours="42010.0"))
    travelled = held.rope_travel(Speed.from_feet_per_minute(1000))
    assert travelled == Length.from_feet(600_000)


def test_a_single_reading_history_has_run_nothing_yet():
    held = history()
    assert held.hours_run() == 0
    assert held.cycles_run() == 0
    assert len(held) == 1


def test_adding_a_reading_makes_a_new_history():
    held = history(reading(day=1))
    longer = held.with_reading(reading(day=2, hours="42009.5"))
    assert len(held) == 1
    assert len(longer) == 2
    assert longer.hours_run() == Decimal("9.5")
