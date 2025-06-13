import pytest

from sheaveline.capacity.dwell import (
    LoadingGate,
    TerminalCheck,
    TerminalTransit,
)
from sheaveline.capacity.spacing import CapacityError, LineSpacing
from sheaveline.measures.duration import Duration
from sheaveline.measures.length import Length
from sheaveline.measures.speed import Speed
from sheaveline.plant.classes import LiftClass

LINE = Speed.from_feet_per_minute(1000)
WALK = Speed.from_feet_per_minute(120)


def detachable(slow_feet=60, decel_feet=40, accel_feet=40):
    return TerminalTransit(
        line_speed=LINE,
        terminal_speed=WALK,
        deceleration=Length.from_feet(decel_feet),
        slow_section=Length.from_feet(slow_feet),
        acceleration=Length.from_feet(accel_feet),
    )


def fixed(fpm=450, ramp_feet=45):
    return TerminalTransit.fixed_grip(Speed.from_feet_per_minute(fpm), Length.from_feet(ramp_feet))


def spacing(loop_feet=10000, carriers=100, fpm=1000):
    return LineSpacing(
        loop=Length.from_feet(loop_feet),
        carriers=carriers,
        speed=Speed.from_feet_per_minute(fpm),
    )


def test_a_detachable_terminal_slows_the_carrier_down():
    assert detachable().detaches()
    assert not fixed().detaches()


def test_a_transit_checks_its_own_speeds():
    with pytest.raises(CapacityError):
        TerminalTransit(Speed(0), WALK, Length(0), Length.from_feet(60), Length(0))
    with pytest.raises(CapacityError):
        TerminalTransit(LINE, Speed(0), Length(0), Length.from_feet(60), Length(0))
    with pytest.raises(CapacityError):
        TerminalTransit(WALK, LINE, Length(0), Length.from_feet(60), Length(0))


def test_the_slow_section_sets_the_loading_window():
    assert detachable(slow_feet=60).loading_window() == Duration.from_seconds(30)
    assert detachable(slow_feet=120).loading_window() == Duration.from_seconds(60)


def test_a_fixed_grip_window_is_the_ramp_at_line_speed():
    assert fixed(fpm=450, ramp_feet=45).loading_window() == Duration.from_seconds(6)


def test_the_transit_covers_the_ramps_as_well_as_the_slow_section():
    through = detachable()
    assert through.transit_time() > through.loading_window()
    assert through.length() == Length.from_feet(140)


def test_slowing_the_line_barely_touches_a_detachable_window():
    fast = detachable()
    slow = TerminalTransit(
        line_speed=Speed.from_feet_per_minute(600),
        terminal_speed=WALK,
        deceleration=Length.from_feet(40),
        slow_section=Length.from_feet(60),
        acceleration=Length.from_feet(40),
    )
    assert slow.loading_window() == fast.loading_window()
    assert slow.transit_time() > fast.transit_time()


def test_a_crew_needs_time_that_grows_with_the_seats():
    assert LoadingGate(detachable(), 4).time_needed() == Duration.from_seconds(7)
    assert LoadingGate(detachable(), 6).time_needed() == Duration.from_seconds(10)


def test_a_second_attendant_halves_the_seating_time():
    one = LoadingGate(detachable(), 6, attendants=1)
    two = one.with_attendants(2)
    assert two.time_needed() < one.time_needed()


def test_a_gate_checks_its_own_staffing():
    with pytest.raises(CapacityError):
        LoadingGate(detachable(), 0)
    with pytest.raises(CapacityError):
        LoadingGate(detachable(), 4, attendants=0)


def test_a_detachable_gate_keeps_up_easily():
    gate = LoadingGate(detachable(), 6)
    assert gate.keeps_up()
    assert gate.slack() > Duration.from_seconds(15)


def test_a_fixed_grip_gate_is_the_one_that_struggles():
    gate = LoadingGate(fixed(fpm=450, ramp_feet=30), 4)
    assert not gate.keeps_up()
    assert gate.slack().is_zero()


def test_a_second_attendant_can_rescue_a_tight_gate():
    gate = LoadingGate(fixed(fpm=450, ramp_feet=45), 6)
    assert not gate.keeps_up()
    assert gate.with_attendants(2).keeps_up()
    assert gate.attendants_needed() == 2


def test_a_gate_nobody_can_load_is_refused_rather_than_staffed():
    gate = LoadingGate(fixed(fpm=1000, ramp_feet=10), 8)
    with pytest.raises(CapacityError):
        gate.attendants_needed()


def test_both_constraints_can_hold_at_once():
    check = TerminalCheck(spacing(), LoadingGate(detachable(), 4), LiftClass.DETACHABLE_CHAIR)
    assert check.holds()
    assert check.binding_constraint() == "neither"


def test_the_interval_can_bind_while_the_gate_is_fine():
    tight = spacing(loop_feet=10000, carriers=100, fpm=1000)
    check = TerminalCheck(tight, LoadingGate(detachable(), 4), LiftClass.FIXED_CHAIR)
    assert not check.interval_holds()
    assert check.gate_holds()
    assert check.binding_constraint() == "interval"


def test_the_gate_can_bind_while_the_interval_is_fine():
    easy = spacing(loop_feet=10000, carriers=40, fpm=1000)
    check = TerminalCheck(easy, LoadingGate(fixed(fpm=450, ramp_feet=20), 6), LiftClass.FIXED_CHAIR)
    assert check.interval_holds()
    assert not check.gate_holds()
    assert check.binding_constraint() == "gate"
    assert check.most_carriers() == 0


def test_both_can_bind_together():
    tight = spacing(loop_feet=10000, carriers=100, fpm=1000)
    check = TerminalCheck(
        tight, LoadingGate(fixed(fpm=450, ramp_feet=20), 6), LiftClass.FIXED_CHAIR
    )
    assert check.binding_constraint() == "both"


def test_the_check_reports_the_class_floor_it_is_using():
    check = TerminalCheck(spacing(), LoadingGate(detachable(), 4), LiftClass.DETACHABLE_CHAIR)
    assert check.floor() == Duration.from_seconds(6)
    assert check.most_carriers() == 100


def test_the_check_prints_both_numbers_and_what_binds():
    check = TerminalCheck(spacing(), LoadingGate(detachable(), 4), LiftClass.DETACHABLE_CHAIR)
    assert str(check) == "interval 0:06, window 0:30, neither binding"
