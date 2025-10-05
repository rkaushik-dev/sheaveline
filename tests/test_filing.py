from datetime import date, datetime

import pytest

from hill import JOHN_PAUL, STRAWBERRY
from sheaveline.board.filing import Filing, FilingKind, FilingSeries
from sheaveline.board.incident import BoardError, Incident, IncidentKind, InjuryLevel
from sheaveline.measures.duration import Duration
from sheaveline.plant.identity import FilingNumber

AT = datetime(2025, 1, 15, 11, 30)


def filing(sequence=1, kind=FilingKind.INCIDENT, lift=JOHN_PAUL, filed=None, acknowledged=None):
    return Filing(
        number=FilingNumber("UT", 2025, sequence),
        kind=kind,
        lift=lift,
        filed=filed or datetime(2025, 1, 15, 12, 30),
        about="chair 34 came off the sheave train at T4",
        filed_by="H Okafor",
        acknowledged=acknowledged,
    )


def series(*filings):
    return FilingSeries.of("UT", 2025, filings)


def deropement():
    return Incident(
        JOHN_PAUL,
        AT,
        IncidentKind.DEROPEMENT,
        InjuryLevel.NONE,
        0,
        "chair 34 off at T4",
        "K Ostler",
    )


def test_a_filing_says_something_and_is_signed():
    with pytest.raises(BoardError):
        Filing(FilingNumber("UT", 2025, 1), FilingKind.INCIDENT, JOHN_PAUL, AT, " ", "H Okafor")
    with pytest.raises(BoardError):
        Filing(FilingNumber("UT", 2025, 1), FilingKind.INCIDENT, JOHN_PAUL, AT, "about", " ")


def test_a_filing_cannot_be_acknowledged_before_it_was_sent():
    with pytest.raises(BoardError):
        filing(acknowledged=date(2025, 1, 1))


def test_only_some_kinds_wait_on_the_board_writing_back():
    assert FilingKind.CAPACITY.needs_an_acknowledgement
    assert FilingKind.MODIFICATION.needs_an_acknowledgement
    assert not FilingKind.INCIDENT.needs_an_acknowledgement
    assert FilingKind.MONTHLY_RETURN.is_routine
    assert not FilingKind.INCIDENT.is_routine


def test_a_filing_waiting_on_an_acknowledgement_is_outstanding():
    waiting = filing(kind=FilingKind.CAPACITY)
    assert waiting.is_outstanding()
    assert not waiting.is_acknowledged()
    answered = waiting.acknowledge(date(2025, 2, 1))
    assert answered.is_acknowledged()
    assert not answered.is_outstanding()


def test_an_incident_filing_is_never_outstanding():
    assert not filing(kind=FilingKind.INCIDENT).is_outstanding()


def test_a_filing_is_acknowledged_once():
    answered = filing(kind=FilingKind.CAPACITY).acknowledge(date(2025, 2, 1))
    with pytest.raises(BoardError):
        answered.acknowledge(date(2025, 3, 1))


def test_lateness_is_measured_against_the_incident_not_the_clerk():
    on_time = filing(filed=datetime(2025, 1, 15, 12, 30))
    assert not on_time.was_late(deropement())
    assert on_time.lateness(deropement()).is_zero()
    monday = filing(filed=datetime(2025, 1, 17, 9, 0))
    assert monday.was_late(deropement())
    assert monday.lateness(deropement()) > Duration.from_hours(40)


def test_a_filing_prints_a_one_liner():
    assert str(filing()) == "UT-2025-0001 incident, JP, 2025-01-15"


def test_a_series_hands_out_the_first_number_when_it_is_empty():
    assert series().next_number() == FilingNumber("UT", 2025, 1)
    assert series().is_gapless()


def test_filing_through_the_series_takes_the_next_number():
    held = series()
    held, first = held.file(
        FilingKind.INCIDENT, JOHN_PAUL, datetime(2025, 1, 15, 12, 30), "a thing", "H Okafor"
    )
    held, second = held.file(
        FilingKind.CAPACITY, STRAWBERRY, datetime(2025, 2, 1, 9, 0), "another", "H Okafor"
    )
    assert first.number.sequence == 1
    assert second.number.sequence == 2
    assert len(held) == 2
    assert held.is_gapless()


def test_a_filing_from_another_year_does_not_go_in_this_series():
    with pytest.raises(BoardError):
        series().file(
            FilingKind.INCIDENT, JOHN_PAUL, datetime(2026, 1, 5, 9, 0), "a thing", "H Okafor"
        )


def test_a_number_from_another_state_or_year_is_refused():
    stranger = Filing(
        FilingNumber("CO", 2025, 1),
        FilingKind.INCIDENT,
        JOHN_PAUL,
        AT,
        "a thing",
        "H Okafor",
    )
    with pytest.raises(BoardError):
        series(stranger)


def test_two_filings_cannot_share_a_number():
    with pytest.raises(BoardError):
        series(filing(sequence=1), filing(sequence=1, lift=STRAWBERRY))


def test_a_gap_in_the_sequence_is_found():
    held = series(filing(sequence=1), filing(sequence=3))
    assert not held.is_gapless()
    assert held.missing_numbers() == (2,)
    assert series().missing_numbers() == ()


def test_filings_can_be_pulled_out_by_machine_and_by_kind():
    held = series(
        filing(sequence=1, lift=JOHN_PAUL, kind=FilingKind.INCIDENT),
        filing(sequence=2, lift=STRAWBERRY, kind=FilingKind.CAPACITY),
    )
    assert len(held.for_lift(JOHN_PAUL)) == 1
    assert len(held.of_kind(FilingKind.CAPACITY)) == 1


def test_the_outstanding_filings_are_the_ones_still_waiting():
    held = series(
        filing(sequence=1, kind=FilingKind.INCIDENT),
        filing(sequence=2, kind=FilingKind.CAPACITY),
        filing(sequence=3, kind=FilingKind.MODIFICATION, acknowledged=date(2025, 2, 1)),
    )
    assert [f.number.sequence for f in held.outstanding()] == [2]


def test_acknowledging_through_the_series_keeps_the_order():
    held = series(filing(sequence=1, kind=FilingKind.CAPACITY), filing(sequence=2))
    answered = held.acknowledge(FilingNumber("UT", 2025, 1), date(2025, 2, 1))
    assert answered.outstanding() == ()
    assert [f.number.sequence for f in answered.filings] == [1, 2]


def test_acknowledging_a_number_that_is_not_in_the_series():
    with pytest.raises(BoardError):
        series(filing()).acknowledge(FilingNumber("UT", 2025, 9), date(2025, 2, 1))
