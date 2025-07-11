from datetime import datetime
from decimal import Decimal

import pytest

from hill import JOHN_PAUL, STRAWBERRY
from sheaveline.measures.duration import Duration
from sheaveline.ops.preop import OperationsError
from sheaveline.ops.stop import StopEvent, StopKind, StopLog


def at(hour, minute=0):
    return datetime(2025, 1, 15, hour, minute)


def stop(
    kind=StopKind.OPERATOR,
    stopped=None,
    restarted=None,
    note="",
    checked=False,
    lift=JOHN_PAUL,
):
    return StopEvent(
        lift=lift,
        kind=kind,
        stopped=stopped or at(10),
        stopped_by="K Ostler",
        note=note,
        restarted=restarted,
        restarted_by=None if restarted is None else "K Ostler",
        line_checked=checked,
    )


def test_the_board_hears_about_some_kinds_and_not_others():
    assert StopKind.DEROPEMENT.is_reportable
    assert StopKind.EVACUATION.is_reportable
    assert StopKind.ROPE_POSITION.is_reportable
    assert not StopKind.GATE.is_reportable
    assert not StopKind.POWER.is_reportable


def test_a_power_cut_and_a_wind_hold_do_not_count_against_the_machine():
    assert not StopKind.POWER.counts_against_the_machine
    assert not StopKind.WEATHER.counts_against_the_machine
    assert not StopKind.SCHEDULED.counts_against_the_machine
    assert StopKind.GATE.counts_against_the_machine
    assert StopKind.SAFETY_CIRCUIT.counts_against_the_machine


def test_a_stop_is_signed_and_ordered():
    with pytest.raises(OperationsError):
        StopEvent(JOHN_PAUL, StopKind.OPERATOR, at(10), "  ")
    with pytest.raises(OperationsError):
        stop(stopped=at(12), restarted=at(10))


def test_a_restart_carries_a_time_and_a_name_or_neither():
    with pytest.raises(OperationsError):
        StopEvent(JOHN_PAUL, StopKind.OPERATOR, at(10), "K Ostler", restarted=at(11))


def test_a_reportable_stop_is_written_up():
    with pytest.raises(OperationsError):
        stop(kind=StopKind.DEROPEMENT)
    assert stop(kind=StopKind.DEROPEMENT, note="carrier 34 off at T4").kind.is_reportable


def test_a_short_ordinary_stop_needs_nothing_before_it_runs_again():
    quick = stop(stopped=at(10), restarted=at(10, 3))
    assert not quick.needs_a_line_check()
    assert quick.length() == Duration.from_minutes(3)


def test_a_long_stop_needs_the_line_ridden_whatever_kind_it_was():
    long_one = stop(stopped=at(10))
    assert long_one.needs_a_line_check(at(10, 25))
    assert not long_one.needs_a_line_check(at(10, 5))


def test_some_kinds_need_the_line_ridden_however_short_they_were():
    for kind in (StopKind.DEROPEMENT, StopKind.ROPE_POSITION, StopKind.SAFETY_CIRCUIT):
        brief = stop(kind=kind, note="written up", stopped=at(10), restarted=at(10, 2))
        assert brief.needs_a_line_check()


def test_a_stop_that_needs_a_check_will_not_restart_without_one():
    circuit = stop(kind=StopKind.SAFETY_CIRCUIT)
    assert not circuit.may_restart(at(10, 5))
    assert circuit.with_line_check().may_restart(at(10, 5))


def test_restarting_without_the_check_is_refused_rather_than_recorded():
    circuit = stop(kind=StopKind.SAFETY_CIRCUIT)
    with pytest.raises(OperationsError):
        circuit.restart(at(10, 10), "M Vetter")
    ran = circuit.restart(at(10, 10), "M Vetter", line_checked=True)
    assert not ran.is_open()
    assert ran.line_checked


def test_a_short_gate_stop_restarts_with_no_ceremony():
    gate = stop(kind=StopKind.GATE)
    ran = gate.restart(at(10, 2), "K Ostler")
    assert ran.length() == Duration.from_minutes(2)


def test_a_stop_cannot_be_closed_twice():
    ran = stop(restarted=at(10, 5))
    assert not ran.is_open()
    with pytest.raises(OperationsError):
        ran.restart(at(10, 10), "K Ostler")


def test_an_open_stop_needs_a_moment_to_be_measured():
    open_stop = stop()
    with pytest.raises(OperationsError):
        open_stop.length()
    with pytest.raises(OperationsError):
        open_stop.length(at(9))
    assert open_stop.length(at(10, 30)) == Duration.from_minutes(30)


def test_a_stop_prints_its_state():
    assert str(stop()) == "JP operator stop at 10:00, still stopped"
    assert str(stop(restarted=at(10, 5))) == "JP operator stop at 10:00, running"


def test_a_log_belongs_to_one_machine():
    with pytest.raises(OperationsError):
        StopLog.of(JOHN_PAUL, [stop(lift=STRAWBERRY)])


def test_a_log_sorts_and_counts():
    log = StopLog.of(
        JOHN_PAUL,
        [
            stop(stopped=at(14), restarted=at(14, 5)),
            stop(stopped=at(9), restarted=at(9, 10)),
        ],
    )
    assert len(log) == 2
    assert [s.stopped.hour for s in log.stops] == [9, 14]


def test_downtime_adds_up_the_closed_stops():
    log = StopLog.of(
        JOHN_PAUL,
        [
            stop(stopped=at(9), restarted=at(9, 10)),
            stop(kind=StopKind.POWER, stopped=at(11), restarted=at(11, 30)),
            stop(stopped=at(14)),
        ],
    )
    assert log.downtime() == Duration.from_minutes(40)
    assert log.chargeable_downtime() == Duration.from_minutes(10)
    assert len(log.open_stops()) == 1


def test_availability_only_charges_the_machine_for_its_own_stops():
    log = StopLog.of(
        JOHN_PAUL,
        [
            stop(stopped=at(9), restarted=at(9, 30)),
            stop(kind=StopKind.WEATHER, stopped=at(11), restarted=at(13)),
        ],
    )
    scheduled = Duration.from_hours(8)
    assert log.availability(scheduled).quantize(Decimal("0.001")) == Decimal("0.938")
    with pytest.raises(OperationsError):
        log.availability(Duration(0))


def test_the_reportable_stops_can_be_pulled_out():
    log = StopLog.of(
        JOHN_PAUL,
        [
            stop(stopped=at(9), restarted=at(9, 5)),
            stop(
                kind=StopKind.DEROPEMENT,
                note="carrier 34 off at T4",
                stopped=at(11),
                restarted=at(13),
                checked=True,
            ),
        ],
    )
    assert len(log.reportable()) == 1
    assert log.longest().kind is StopKind.DEROPEMENT
    assert len(log.of_kind(StopKind.OPERATOR)) == 1


def test_a_log_with_nothing_closed_has_no_longest():
    with pytest.raises(OperationsError):
        StopLog.of(JOHN_PAUL, [stop()]).longest()


def test_adding_a_stop_makes_a_new_log():
    log = StopLog.of(JOHN_PAUL)
    longer = log.with_stop(stop())
    assert len(log) == 0
    assert len(longer) == 1
