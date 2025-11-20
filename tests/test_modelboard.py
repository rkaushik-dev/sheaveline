from datetime import date, datetime

import pytest
from sqlalchemy import inspect, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from sheaveline.board.filing import FilingKind
from sheaveline.board.incident import IncidentKind, InjuryLevel
from sheaveline.evac.drill import DrillKind
from sheaveline.measures.angle import Bearing
from sheaveline.measures.speed import Speed
from sheaveline.people.certification import Endorsement
from sheaveline.persistence.base import build_engine, create_schema
from sheaveline.persistence.models.board import (
    CertificateClassRow,
    CertificateRow,
    DrillRow,
    DrillTeamRow,
    FilingRow,
    IncidentRow,
)
from sheaveline.persistence.models.plant import LiftRow
from sheaveline.plant.classes import LiftClass
from sheaveline.plant.identity import FilingNumber, LiftCode

JP = LiftCode("JP")
AT = datetime(2025, 1, 15, 11, 30)


@pytest.fixture
def engine():
    made = build_engine()
    create_schema(made)
    with Session(made) as session:
        session.add(
            LiftRow(
                code=JP,
                name="John Paul",
                lift_class=LiftClass.DETACHABLE_CHAIR,
                bearing=Bearing.from_degrees(72),
                design_speed=Speed.from_feet_per_minute(1000),
                commissioned=date(2004, 12, 3),
            )
        )
        session.commit()
    yield made
    made.dispose()


def incident_row(at=AT, kind=IncidentKind.DEROPEMENT, injury=InjuryLevel.NONE, people=0):
    return IncidentRow(
        lift_code=JP,
        at=at,
        kind=kind,
        injury=injury,
        people=people,
        narrative="chair 34 off at T4",
        reported_by="K Ostler",
    )


def filing_row(sequence=1, kind=FilingKind.INCIDENT):
    return FilingRow(
        number=FilingNumber("UT", 2025, sequence),
        kind=kind,
        lift_code=JP,
        filed=datetime(2025, 1, 15, 12, 30),
        about="chair 34 off at T4",
        filed_by="H Okafor",
    )


def test_the_schema_carries_every_board_table(engine):
    tables = set(inspect(engine).get_table_names())
    assert {
        "filings",
        "incidents",
        "drills",
        "drill_teams",
        "certificates",
        "certificate_classes",
    } <= tables


def test_an_incident_starts_with_no_filing_against_it(engine):
    with Session(engine) as session:
        session.add(incident_row())
        session.commit()
    with Session(engine) as session:
        found = session.scalars(select(IncidentRow)).one()
        assert found.filing is None
        assert found.filing_number is None


def test_a_filing_can_be_hung_on_an_incident_later(engine):
    with Session(engine) as session:
        session.add(incident_row())
        session.add(filing_row())
        session.commit()
        found = session.scalars(select(IncidentRow)).one()
        found.filing_number = FilingNumber("UT", 2025, 1)
        session.commit()
    with Session(engine) as session:
        found = session.scalars(select(IncidentRow)).one()
        assert found.filing is not None
        assert found.filing.filed_by == "H Okafor"


def test_a_filing_number_that_does_not_exist_is_refused(engine):
    with Session(engine) as session:
        one = incident_row()
        one.filing_number = FilingNumber("UT", 2025, 9)
        session.add(one)
        with pytest.raises(IntegrityError):
            session.commit()


def test_two_incidents_cannot_share_a_moment_on_one_machine(engine):
    with Session(engine) as session:
        session.add(incident_row())
        session.add(incident_row())
        with pytest.raises(IntegrityError):
            session.commit()


def test_an_incident_cannot_involve_fewer_than_nobody(engine):
    with Session(engine) as session:
        session.add(incident_row(people=-1))
        with pytest.raises(IntegrityError):
            session.commit()


def test_one_filing_can_answer_for_more_than_one_incident(engine):
    with Session(engine) as session:
        session.add(filing_row())
        first = incident_row(at=datetime(2025, 1, 15, 11, 30))
        second = incident_row(at=datetime(2025, 1, 15, 14, 0))
        first.filing_number = FilingNumber("UT", 2025, 1)
        second.filing_number = FilingNumber("UT", 2025, 1)
        session.add_all([first, second])
        session.commit()
    with Session(engine) as session:
        found = session.get(FilingRow, FilingNumber("UT", 2025, 1))
        assert found is not None
        assert len(found.incidents) == 2


def test_a_drill_takes_its_teams_with_it(engine):
    with Session(engine) as session:
        drill = DrillRow(
            lift_code=JP,
            on=date(2024, 12, 5),
            kind=DrillKind.FULL_LINE,
            carriers_worked=8,
            elapsed_seconds=5700,
            observer="H Okafor",
        )
        drill.teams.append(DrillTeamRow(team="alpha"))
        drill.teams.append(DrillTeamRow(team="bravo"))
        session.add(drill)
        session.commit()
    with Session(engine) as session:
        found = session.scalars(select(DrillRow)).one()
        assert {team.team for team in found.teams} == {"alpha", "bravo"}


def test_a_team_is_counted_once_in_a_drill(engine):
    with Session(engine) as session:
        drill = DrillRow(
            lift_code=JP,
            on=date(2024, 12, 5),
            kind=DrillKind.FULL_LINE,
            carriers_worked=8,
            elapsed_seconds=5700,
            observer="H Okafor",
        )
        drill.teams.append(DrillTeamRow(team="alpha"))
        drill.teams.append(DrillTeamRow(team="alpha"))
        session.add(drill)
        with pytest.raises(IntegrityError):
            session.commit()


def test_one_drill_a_machine_a_day(engine):
    with Session(engine) as session:
        for _ in range(2):
            session.add(
                DrillRow(
                    lift_code=JP,
                    on=date(2024, 12, 5),
                    kind=DrillKind.PARTIAL,
                    carriers_worked=4,
                    elapsed_seconds=1800,
                    observer="H Okafor",
                )
            )
        with pytest.raises(IntegrityError):
            session.commit()


def test_a_certificate_carries_the_classes_it_covers(engine):
    with Session(engine) as session:
        card = CertificateRow(
            person="R Diaz",
            endorsement=Endorsement.OPERATOR,
            issued=date(2024, 11, 1),
            expires=date(2025, 11, 1),
            issued_by="Utah tramway board",
        )
        card.classes.append(CertificateClassRow(lift_class="FIXED_CHAIR"))
        card.classes.append(CertificateClassRow(lift_class="DETACHABLE_CHAIR"))
        session.add(card)
        session.commit()
    with Session(engine) as session:
        found = session.scalars(select(CertificateRow)).one()
        assert {entry.lift_class for entry in found.classes} == {
            "FIXED_CHAIR",
            "DETACHABLE_CHAIR",
        }


def test_a_card_runs_forwards(engine):
    with Session(engine) as session:
        session.add(
            CertificateRow(
                person="R Diaz",
                endorsement=Endorsement.OPERATOR,
                issued=date(2025, 11, 1),
                expires=date(2024, 11, 1),
                issued_by="Utah tramway board",
            )
        )
        with pytest.raises(IntegrityError):
            session.commit()


def test_a_class_is_listed_once_on_a_card(engine):
    with Session(engine) as session:
        card = CertificateRow(
            person="R Diaz",
            endorsement=Endorsement.OPERATOR,
            issued=date(2024, 11, 1),
            expires=date(2025, 11, 1),
            issued_by="Utah tramway board",
        )
        card.classes.append(CertificateClassRow(lift_class="FIXED_CHAIR"))
        card.classes.append(CertificateClassRow(lift_class="FIXED_CHAIR"))
        session.add(card)
        with pytest.raises(IntegrityError):
            session.commit()


def test_deleting_a_card_takes_its_classes_with_it(engine):
    with Session(engine) as session:
        card = CertificateRow(
            person="R Diaz",
            endorsement=Endorsement.OPERATOR,
            issued=date(2024, 11, 1),
            expires=date(2025, 11, 1),
            issued_by="Utah tramway board",
        )
        card.classes.append(CertificateClassRow(lift_class="FIXED_CHAIR"))
        session.add(card)
        session.commit()
        session.delete(card)
        session.commit()
    with Session(engine) as session:
        assert session.scalars(select(CertificateClassRow)).all() == []
