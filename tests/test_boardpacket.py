from datetime import date, datetime
from decimal import Decimal

from hill import JOHN_PAUL, STRAWBERRY
from sheaveline.board.incident import Incident, IncidentKind, InjuryLevel
from sheaveline.measures.duration import Duration
from sheaveline.plant.identity import FilingNumber
from sheaveline.reports.boardpacket import BoardPacket, FiledIncident, MachineYear
from sheaveline.reports.forms import PAGE_WIDTH
from sheaveline.upkeep.status import ServiceStatus

PREPARED = date(2025, 5, 12)


def machine(
    lift=JOHN_PAUL,
    name="John Paul",
    days=142,
    status=ServiceStatus.IN_SERVICE,
    overdue=0,
    defects=0,
    verdict="serviceable",
    clears=None,
):
    return MachineYear(
        lift=lift,
        name=name,
        days_open=days,
        scheduled=Duration.from_hours(1065),
        chargeable=Duration.from_hours(11),
        availability=Decimal("0.99"),
        status=status,
        ladder_overdue=overdue,
        open_defects=defects,
        rope_serial="FAT-2019-04412",
        rope_verdict=verdict,
        evacuation_time=clears,
    )


def incident(hour=11, kind=IncidentKind.LOADING, filing="UT-2025-0001", filed=None):
    return FiledIncident(
        incident=Incident(
            lift=JOHN_PAUL,
            at=datetime(2025, 1, 15, hour, 30),
            kind=kind,
            injury=InjuryLevel.NONE,
            people=1,
            narrative="ski under the bail on loading",
            reported_by="D Chin",
        ),
        filing=None if filing is None else FilingNumber.parse(filing),
        filed_on=filed,
    )


def packet(*machines, incidents=()):
    return BoardPacket.of(
        season="2024-25",
        resort="Ogden Peak",
        prepared=PREPARED,
        prepared_by="R Skaggs",
        machines=machines or [machine()],
        incidents=incidents,
    )


def test_a_machine_with_nothing_outstanding_is_clean():
    assert machine().is_clean()
    assert machine().concerns() == ()


def test_a_machine_out_of_service_is_named():
    one = machine(status=ServiceStatus.OUT_OF_SERVICE)
    assert not one.is_clean()
    assert "JP is out of service" in one.concerns()


def test_overdue_inspections_and_open_defects_are_both_counted():
    one = machine(overdue=2, defects=3)
    assert "JP has 2 inspections overdue" in one.concerns()
    assert "JP has 3 defects open" in one.concerns()


def test_a_rope_that_is_not_serviceable_reaches_the_board():
    one = machine(verdict="on watch")
    assert any("rope FAT-2019-04412 is on watch" in line for line in one.concerns())


def test_the_machines_come_back_in_code_order():
    made = packet(machine(lift=STRAWBERRY, name="Strawberry"), machine())
    assert [one.lift for one in made.machines] == [JOHN_PAUL, STRAWBERRY]


def test_the_days_open_is_the_busiest_machine():
    made = packet(machine(days=142), machine(lift=STRAWBERRY, name="Strawberry", days=96))
    assert made.days_open() == 142


def test_a_packet_with_no_machines_says_nothing_rather_than_failing():
    made = BoardPacket.of("2024-25", "Ogden Peak", PREPARED, "R Skaggs")
    assert made.days_open() == 0
    assert made.is_clean()
    assert "nothing was reportable this season" in made.text()


def test_a_clean_season_says_so():
    made = packet()
    assert made.is_clean()
    assert "nothing outstanding" in made.text()


def test_an_unfiled_incident_is_outstanding():
    made = packet(incidents=[incident(filing=None)])
    assert made.unfiled()
    assert not made.is_clean()
    assert "NOT FILED" in made.text()
    assert "has not been filed" in made.text()


def test_a_filing_that_went_in_late_is_outstanding():
    late = incident(kind=IncidentKind.DEROPEMENT, filed=date(2025, 2, 1))
    made = packet(incidents=[late])
    assert made.late_filings()
    assert "after the deadline" in made.text()


def test_a_filing_that_went_in_on_time_is_not():
    prompt = incident(kind=IncidentKind.DEROPEMENT, filed=date(2025, 1, 15))
    assert not packet(incidents=[prompt]).late_filings()


def test_an_incident_nobody_has_filed_is_not_counted_as_late():
    assert not packet(incidents=[incident(filing=None)]).late_filings()


def test_the_incidents_come_back_oldest_first():
    made = packet(incidents=[incident(hour=15), incident(hour=9)])
    assert [one.incident.at.hour for one in made.incidents] == [9, 15]


def test_the_page_is_eighty_columns():
    made = packet(machine(), machine(lift=STRAWBERRY, name="Strawberry"), incidents=[incident()])
    assert max(len(one) for one in made.render().lines()) <= PAGE_WIDTH


def test_the_page_answers_the_four_questions_in_order():
    wanted = ["machines", "ropes", "evacuation", "incidents", "outstanding"]
    lines = packet(incidents=[incident()]).text().splitlines()
    assert [one for one in lines if one in wanted] == wanted


def test_a_machine_that_has_not_been_drilled_says_so():
    assert "not drilled" in packet(machine(clears=None)).text()
    assert "44:00" in packet(machine(clears=Duration.from_minutes(44))).text()


def test_the_clean_machines_are_the_ones_with_nothing_against_them():
    made = packet(machine(), machine(lift=STRAWBERRY, name="Strawberry", defects=1))
    assert [one.lift for one in made.clean_machines()] == [JOHN_PAUL]
