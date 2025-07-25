from datetime import date, datetime
from decimal import Decimal

import pytest

from hill import JOHN_PAUL, STRAWBERRY, john_paul
from sheaveline.measures.duration import Duration
from sheaveline.ops.logbook import DayLog, EntryKind, LogEntry
from sheaveline.ops.meters import MeterReading
from sheaveline.ops.preop import (
    STANDARD_SHEET,
    CheckOutcome,
    CheckResult,
    OperationsError,
    PreOpSheet,
)
from sheaveline.ops.stop import StopEvent, StopKind, StopLog
from sheaveline.schedule.hours import OperatingWindow, TimeOfDay
from sheaveline.weather.hold import HoldReason
from sheaveline.weather.holdlog import HoldRecord, HoldRegister

DAY = date(2025, 1, 15)
WINDOW = OperatingWindow.parse("9:00", "3:45 pm", "4:15 pm")


def clear_sheet(lift=None):
    machine = lift or john_paul()
    results = [
        CheckResult(item, CheckOutcome.PASS, "M Vetter")
        for item in STANDARD_SHEET
        if item.applies_to(machine)
    ]
    return PreOpSheet.of(machine, DAY, results, "M Vetter", "K Ostler")


def log(sheet=None, holds=None, stops=None):
    return DayLog.start(
        JOHN_PAUL,
        DAY,
        WINDOW,
        sheet=clear_sheet() if sheet is None else sheet,
        holds=holds,
    ).with_stops(stops or StopLog.of(JOHN_PAUL))


def at(hour, minute=0):
    return datetime(2025, 1, 15, hour, minute)


def test_a_machine_with_a_clear_sheet_and_no_holds_may_open():
    book = log()
    assert book.may_open()
    assert book.blockers() == ()


def test_no_sheet_means_no_opening():
    book = DayLog.start(JOHN_PAUL, DAY, WINDOW)
    assert not book.may_open()
    assert book.blockers() == ("no pre opening sheet",)


def test_a_failed_critical_line_keeps_the_machine_shut():
    machine = john_paul()
    results = [
        CheckResult(
            item,
            CheckOutcome.FAIL if item.code == "B2" else CheckOutcome.PASS,
            "M Vetter",
            "rope out at T4" if item.code == "B2" else "",
        )
        for item in STANDARD_SHEET
        if item.applies_to(machine)
    ]
    book = log(sheet=PreOpSheet.of(machine, DAY, results, "M Vetter", "K Ostler"))
    assert not book.may_open()
    assert "B2 failed" in book.blockers()


def test_an_open_weather_hold_keeps_the_machine_shut():
    holds = HoldRegister.of([HoldRecord(JOHN_PAUL, HoldReason.GUST, at(8), "K Ostler")])
    book = log(holds=holds)
    assert not book.may_open()
    assert "gust hold open" in book.blockers()


def test_an_uncleared_stop_keeps_the_machine_shut():
    stops = StopLog.of(
        JOHN_PAUL,
        [StopEvent(JOHN_PAUL, StopKind.SAFETY_CIRCUIT, at(8), "K Ostler")],
    )
    book = log(stops=stops)
    assert not book.may_open()
    assert "safety circuit not cleared" in book.blockers()


def test_opening_writes_a_line_in_the_book():
    book = log().open_at(TimeOfDay.at(9), "K Ostler")
    assert book.is_open()
    assert book.opened == TimeOfDay.at(9)
    assert book.lines()[-1].kind is EntryKind.OPENED
    assert str(book) == "JP 2025-01-15, 9:00, still running"


def test_a_machine_that_is_not_clear_will_not_open():
    book = DayLog.start(JOHN_PAUL, DAY, WINDOW)
    with pytest.raises(OperationsError):
        book.open_at(TimeOfDay.at(9), "K Ostler")


def test_a_machine_opens_once():
    book = log().open_at(TimeOfDay.at(9), "K Ostler")
    with pytest.raises(OperationsError):
        book.open_at(TimeOfDay.at(10), "K Ostler")


def test_closing_needs_an_opening_first():
    with pytest.raises(OperationsError):
        log().close_at(TimeOfDay.at(16), "K Ostler")


def test_a_machine_closes_once_and_after_it_opened():
    book = log().open_at(TimeOfDay.at(9), "K Ostler").close_at(TimeOfDay.at(16), "K Ostler")
    assert not book.is_open()
    assert str(book) == "JP 2025-01-15, 9:00 to 16:00"
    with pytest.raises(OperationsError):
        book.close_at(TimeOfDay.at(17), "K Ostler")


def test_a_log_refuses_a_close_before_the_open():
    with pytest.raises(OperationsError):
        DayLog(
            lift=JOHN_PAUL,
            day=DAY,
            window=WINDOW,
            opened=TimeOfDay.at(12),
            closed=TimeOfDay.at(9),
        )


def test_a_log_refuses_a_close_with_no_open():
    with pytest.raises(OperationsError):
        DayLog(lift=JOHN_PAUL, day=DAY, window=WINDOW, closed=TimeOfDay.at(16))


def test_a_sheet_or_a_stop_log_from_another_machine_is_refused():
    with pytest.raises(OperationsError):
        DayLog.start(JOHN_PAUL, DAY, WINDOW, sheet=clear_sheet(john_paul(code=JOHN_PAUL)))
        DayLog(lift=STRAWBERRY, day=DAY, window=WINDOW, sheet=clear_sheet())
    with pytest.raises(OperationsError):
        DayLog(lift=JOHN_PAUL, day=DAY, window=WINDOW, stops=StopLog.of(STRAWBERRY))


def test_notes_are_kept_apart_from_the_structural_lines():
    book = (
        log()
        .open_at(TimeOfDay.at(9), "K Ostler")
        .note(TimeOfDay.at(11), "ski school loading on the far side", "K Ostler")
        .close_at(TimeOfDay.at(16), "K Ostler")
    )
    assert len(book.lines()) == 3
    assert len(book.notes()) == 1
    assert book.notes()[0].text.startswith("ski school")


def test_a_line_says_something_and_is_signed():
    with pytest.raises(OperationsError):
        LogEntry(at(11), EntryKind.NOTE, "   ", "K Ostler")
    with pytest.raises(OperationsError):
        LogEntry(at(11), EntryKind.NOTE, "something", " ")


def test_a_line_prints_the_way_a_book_reads():
    entry = LogEntry(at(11, 5), EntryKind.NOTE, "wind picking up", "K Ostler")
    assert str(entry) == "11:05 K Ostler: wind picking up"


def test_late_opening_and_late_sweeping_are_both_noticed():
    late = log().open_at(TimeOfDay.at(9, 20), "K Ostler").close_at(TimeOfDay.at(16, 40), "K Ostler")
    assert late.opened_late()
    assert late.swept_late()
    on_time = log().open_at(TimeOfDay.at(9), "K Ostler").close_at(TimeOfDay.at(16), "K Ostler")
    assert not on_time.opened_late()
    assert not on_time.swept_late()


def test_the_running_time_takes_the_stops_off_the_open_time():
    stops = StopLog.of(
        JOHN_PAUL,
        [
            StopEvent(
                JOHN_PAUL,
                StopKind.GATE,
                at(11),
                "K Ostler",
                restarted=at(11, 20),
                restarted_by="K Ostler",
                line_checked=True,
            )
        ],
    )
    book = (
        log(stops=stops).open_at(TimeOfDay.at(9), "K Ostler").close_at(TimeOfDay.at(16), "K Ostler")
    )
    assert book.open_time() == Duration.from_hours(7)
    assert book.running_time() == Duration.from_minutes(400)
    assert book.scheduled_time() == Duration.from_minutes(435)


def test_a_day_that_has_not_finished_has_no_open_time():
    with pytest.raises(OperationsError):
        log().open_at(TimeOfDay.at(9), "K Ostler").open_time()


def test_the_meter_reading_has_to_match_the_machine_and_the_day():
    book = log()
    good = MeterReading(JOHN_PAUL, DAY, Decimal("42000.0"), 1_000_000, "M Vetter")
    assert book.with_meter(good).meter is good
    wrong_day = MeterReading(JOHN_PAUL, date(2025, 1, 16), Decimal("42000.0"), 1, "M Vetter")
    with pytest.raises(OperationsError):
        book.with_meter(wrong_day)
