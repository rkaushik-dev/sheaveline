from datetime import datetime

import pytest

from hill import JOHN_PAUL, STRAWBERRY
from sheaveline.board.filing import Filing, FilingKind, FilingSeries
from sheaveline.board.incident import BoardError, Incident, IncidentKind, InjuryLevel
from sheaveline.board.register import BoardRegister, ReportedIncident
from sheaveline.measures.duration import Duration
from sheaveline.plant.identity import FilingNumber


def incident(
    lift=JOHN_PAUL,
    kind=IncidentKind.DEROPEMENT,
    injury=InjuryLevel.NONE,
    people=0,
    at=datetime(2025, 1, 15, 11, 30),
    narrative="chair 34 off at T4",
):
    return Incident(lift, at, kind, injury, people, narrative, "K Ostler")


def register(*incidents):
    held = BoardRegister.of(FilingSeries.of("UT", 2025))
    for one in incidents:
        held = held.record(one)
    return held


def test_an_incident_starts_unfiled():
    held = register(incident())
    assert len(held) == 1
    assert len(held.unfiled()) == 1
    assert not held.reported[0].is_filed()
    assert str(held.reported[0]).endswith("not filed")


def test_filing_takes_the_next_number_from_the_series():
    one = incident()
    held = register(one).file(one, datetime(2025, 1, 15, 12, 30), "H Okafor")
    assert held.unfiled() == ()
    filed = held.reported[0]
    assert filed.is_filed()
    assert filed.filing is not None
    assert filed.filing.number == FilingNumber("UT", 2025, 1)
    assert str(filed).endswith("filed as UT-2025-0001")


def test_filing_something_that_is_not_in_the_register():
    with pytest.raises(BoardError):
        register().file(incident(), datetime(2025, 1, 15, 12, 30), "H Okafor")


def test_an_incident_is_filed_once():
    one = incident()
    held = register(one).file(one, datetime(2025, 1, 15, 12, 30), "H Okafor")
    with pytest.raises(BoardError):
        held.file(one, datetime(2025, 1, 16, 9, 0), "H Okafor")


def test_a_filing_against_another_machine_is_refused():
    wrong = Filing(
        FilingNumber("UT", 2025, 1),
        FilingKind.INCIDENT,
        STRAWBERRY,
        datetime(2025, 1, 15, 12, 30),
        "a thing",
        "H Okafor",
    )
    with pytest.raises(BoardError):
        ReportedIncident(incident(), wrong)


def test_an_incident_is_answered_by_an_incident_filing():
    wrong_kind = Filing(
        FilingNumber("UT", 2025, 1),
        FilingKind.CAPACITY,
        JOHN_PAUL,
        datetime(2025, 1, 15, 12, 30),
        "a thing",
        "H Okafor",
    )
    with pytest.raises(BoardError):
        ReportedIncident(incident(), wrong_kind)


def test_an_unfiled_incident_goes_overdue_when_its_deadline_passes():
    held = register(incident())
    assert not held.overdue(datetime(2025, 1, 15, 12, 30))
    assert held.overdue(datetime(2025, 1, 15, 14, 30))


def test_a_filing_inside_the_deadline_clears_the_overdue():
    one = incident()
    held = register(one).file(one, datetime(2025, 1, 15, 12, 30), "H Okafor")
    assert held.overdue(datetime(2025, 2, 1)) == ()
    assert held.late_filings() == ()


def test_a_filing_after_the_deadline_stays_overdue_forever():
    one = incident()
    held = register(one).file(one, datetime(2025, 1, 17, 9, 0), "H Okafor")
    assert held.overdue(datetime(2025, 2, 1))
    assert len(held.late_filings()) == 1
    assert held.late_filings()[0].lateness() > Duration.from_hours(40)


def test_lateness_on_something_unfiled_is_an_error_rather_than_a_zero():
    held = register(incident())
    with pytest.raises(BoardError):
        held.reported[0].lateness()


def test_an_unfiled_incident_reports_how_long_it_has_been_sitting():
    held = register(incident())
    entry = held.reported[0]
    assert entry.outstanding_for(datetime(2025, 1, 15, 13, 30)) == Duration.from_hours(2)
    with pytest.raises(BoardError):
        entry.outstanding_for(datetime(2025, 1, 15, 10, 0))


def test_a_filed_incident_has_nothing_outstanding():
    one = incident()
    held = register(one).file(one, datetime(2025, 1, 15, 12, 30), "H Okafor")
    assert held.reported[0].outstanding_for(datetime(2025, 2, 1)).is_zero()


def test_the_incident_due_next_is_the_one_with_least_time_on_it():
    urgent = incident(at=datetime(2025, 1, 15, 11, 30))
    slack = incident(
        kind=IncidentKind.LOADING,
        injury=InjuryLevel.FIRST_AID,
        people=1,
        at=datetime(2025, 1, 15, 10, 0),
        narrative="pole caught on the ramp",
    )
    held = register(slack, urgent)
    due = held.due_next(datetime(2025, 1, 15, 12, 0))
    assert due is not None
    assert due.incident == urgent


def test_nothing_due_means_nothing_rather_than_an_error():
    assert register().due_next(datetime(2025, 1, 15, 12, 0)) is None


def test_the_monthly_return_only_holds_what_belongs_in_it():
    quiet = incident(
        kind=IncidentKind.LOADING,
        injury=InjuryLevel.FIRST_AID,
        people=1,
        narrative="pole caught on the ramp",
    )
    held = register(incident(), quiet)
    monthly = held.monthly_return(2025, 1)
    assert [entry.incident for entry in monthly] == [quiet]
    assert held.monthly_return(2025, 2) == ()


def test_incidents_are_kept_apart_by_machine():
    held = register(incident(lift=JOHN_PAUL), incident(lift=STRAWBERRY))
    assert len(held.for_lift(JOHN_PAUL)) == 1
    assert len(held.for_lift(STRAWBERRY)) == 1


def test_an_unfiled_holding_incident_names_the_machine_it_is_holding():
    held = register(incident(kind=IncidentKind.DEROPEMENT))
    assert JOHN_PAUL in held.holding_machines()
    one = held.reported[0].incident
    after = held.file(one, datetime(2025, 1, 15, 12, 30), "H Okafor")
    assert after.holding_machines() == frozenset()


def test_a_gentle_incident_never_holds_a_machine():
    held = register(
        incident(
            kind=IncidentKind.LOADING,
            injury=InjuryLevel.FIRST_AID,
            people=1,
            narrative="pole caught",
        )
    )
    assert held.holding_machines() == frozenset()
