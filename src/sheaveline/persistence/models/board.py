"""The tables the board's paperwork lives in.

An incident and its filing are one row each rather than one row with a nullable
half, because the two are written by different people at different times and the
gap between them is the thing the board asks about. The link is the filing's own
column, nullable until somebody writes.

Certificates and drills are here too, for the same reason the filings are: they
are the department's answer when the board asks whether the people on the hill
were allowed to be there.
"""

from __future__ import annotations

from datetime import date, datetime

from sqlalchemy import (
    CheckConstraint,
    Date,
    DateTime,
    ForeignKey,
    Integer,
    String,
    UniqueConstraint,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from sheaveline.board.filing import FilingKind
from sheaveline.board.incident import IncidentKind, InjuryLevel
from sheaveline.evac.drill import DrillKind
from sheaveline.people.certification import Endorsement
from sheaveline.persistence.base import Base
from sheaveline.persistence.models.plant import LiftRow, named_enum
from sheaveline.persistence.types import FilingNumberColumn, LiftCodeColumn
from sheaveline.plant.identity import FilingNumber, LiftCode


class FilingRow(Base):
    __tablename__ = "filings"

    number: Mapped[FilingNumber] = mapped_column(FilingNumberColumn, primary_key=True)
    kind: Mapped[FilingKind] = mapped_column(named_enum(FilingKind, "filing_kind"))
    lift_code: Mapped[LiftCode] = mapped_column(LiftCodeColumn, ForeignKey("lifts.code"))
    filed: Mapped[datetime] = mapped_column(DateTime)
    about: Mapped[str] = mapped_column(String(1000))
    filed_by: Mapped[str] = mapped_column(String(64))
    acknowledged: Mapped[date | None] = mapped_column(Date, nullable=True)

    lift: Mapped[LiftRow] = relationship()
    incidents: Mapped[list[IncidentRow]] = relationship(back_populates="filing")


class IncidentRow(Base):
    __tablename__ = "incidents"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    lift_code: Mapped[LiftCode] = mapped_column(LiftCodeColumn, ForeignKey("lifts.code"))
    at: Mapped[datetime] = mapped_column(DateTime)
    kind: Mapped[IncidentKind] = mapped_column(named_enum(IncidentKind, "incident_kind"))
    injury: Mapped[InjuryLevel] = mapped_column(named_enum(InjuryLevel, "injury_level"))
    people: Mapped[int] = mapped_column(Integer)
    narrative: Mapped[str] = mapped_column(String(2000))
    reported_by: Mapped[str] = mapped_column(String(64))
    filing_number: Mapped[FilingNumber | None] = mapped_column(
        FilingNumberColumn, ForeignKey("filings.number"), nullable=True
    )

    lift: Mapped[LiftRow] = relationship()
    filing: Mapped[FilingRow | None] = relationship(back_populates="incidents")

    __table_args__ = (
        UniqueConstraint("lift_code", "at", name="one_incident_a_moment"),
        CheckConstraint("people >= 0", name="people_not_negative"),
    )


class DrillRow(Base):
    __tablename__ = "drills"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    lift_code: Mapped[LiftCode] = mapped_column(LiftCodeColumn, ForeignKey("lifts.code"))
    on: Mapped[date] = mapped_column(Date)
    kind: Mapped[DrillKind] = mapped_column(named_enum(DrillKind, "drill_kind"))
    carriers_worked: Mapped[int] = mapped_column(Integer)
    elapsed_seconds: Mapped[int] = mapped_column(Integer)
    observer: Mapped[str] = mapped_column(String(64))

    lift: Mapped[LiftRow] = relationship()
    teams: Mapped[list[DrillTeamRow]] = relationship(
        back_populates="drill", cascade="all, delete-orphan"
    )

    __table_args__ = (
        UniqueConstraint("lift_code", "on", name="one_drill_a_day"),
        CheckConstraint("carriers_worked >= 0", name="carriers_not_negative"),
    )


class DrillTeamRow(Base):
    __tablename__ = "drill_teams"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    drill_id: Mapped[int] = mapped_column(Integer, ForeignKey("drills.id"))
    team: Mapped[str] = mapped_column(String(32))

    drill: Mapped[DrillRow] = relationship(back_populates="teams")

    __table_args__ = (UniqueConstraint("drill_id", "team", name="one_team_a_drill"),)


class CertificateRow(Base):
    __tablename__ = "certificates"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    person: Mapped[str] = mapped_column(String(64))
    endorsement: Mapped[Endorsement] = mapped_column(named_enum(Endorsement, "endorsement"))
    issued: Mapped[date] = mapped_column(Date)
    expires: Mapped[date] = mapped_column(Date)
    issued_by: Mapped[str] = mapped_column(String(64))

    classes: Mapped[list[CertificateClassRow]] = relationship(
        back_populates="certificate", cascade="all, delete-orphan"
    )

    __table_args__ = (
        UniqueConstraint("person", "endorsement", "issued", name="one_card_an_issue"),
        CheckConstraint("expires > issued", name="card_runs_forwards"),
    )


class CertificateClassRow(Base):
    __tablename__ = "certificate_classes"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    certificate_id: Mapped[int] = mapped_column(Integer, ForeignKey("certificates.id"))
    lift_class: Mapped[str] = mapped_column(String(32))

    certificate: Mapped[CertificateRow] = relationship(back_populates="classes")

    __table_args__ = (UniqueConstraint("certificate_id", "lift_class", name="one_class_a_card"),)
