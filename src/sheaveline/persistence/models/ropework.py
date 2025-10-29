"""The tables the rope file lives in.

Every one of these hangs off a rope serial rather than off a lift, and that is
the point. A rope moves. It comes off the quad in the spring, gets shortened,
sits in the shop for a season and goes on the triple, and the whole history has
to follow it rather than staying with the machine it happened to be on when the
caliper came out.

The tension system is the exception and belongs to the lift, because the
counterweight does not travel with the rope.
"""

from __future__ import annotations

from datetime import date, datetime
from decimal import Decimal

from sqlalchemy import (
    Boolean,
    CheckConstraint,
    Date,
    DateTime,
    ForeignKey,
    Integer,
    String,
    UniqueConstraint,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from sheaveline.measures.length import Length
from sheaveline.measures.load import Load
from sheaveline.measures.temperature import Temperature
from sheaveline.persistence.base import Base
from sheaveline.persistence.models.plant import HaulRopeRow, LiftRow, named_enum
from sheaveline.persistence.types import (
    DecimalColumn,
    LengthColumn,
    LiftCodeColumn,
    LoadColumn,
    RopeSerialColumn,
    TemperatureColumn,
)
from sheaveline.plant.identity import LiftCode, RopeSerial
from sheaveline.ropework.mrt import IndicationKind
from sheaveline.ropework.tension import TensionKind


class WearSurveyRow(Base):
    __tablename__ = "wear_surveys"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    rope_serial: Mapped[RopeSerial] = mapped_column(
        RopeSerialColumn, ForeignKey("haul_ropes.serial")
    )
    taken: Mapped[date] = mapped_column(Date)
    inspector: Mapped[str] = mapped_column(String(64))

    rope: Mapped[HaulRopeRow] = relationship()
    diameters: Mapped[list[DiameterReadingRow]] = relationship(
        back_populates="survey", cascade="all, delete-orphan"
    )
    lays: Mapped[list[LayReadingRow]] = relationship(
        back_populates="survey", cascade="all, delete-orphan"
    )

    __table_args__ = (UniqueConstraint("rope_serial", "taken", name="one_wear_survey_a_day"),)


class DiameterReadingRow(Base):
    __tablename__ = "diameter_readings"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    survey_id: Mapped[int] = mapped_column(Integer, ForeignKey("wear_surveys.id"))
    station: Mapped[Length] = mapped_column(LengthColumn)
    across_crowns: Mapped[Length] = mapped_column(LengthColumn)
    across_valleys: Mapped[Length] = mapped_column(LengthColumn)

    survey: Mapped[WearSurveyRow] = relationship(back_populates="diameters")


class LayReadingRow(Base):
    __tablename__ = "lay_readings"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    survey_id: Mapped[int] = mapped_column(Integer, ForeignKey("wear_surveys.id"))
    station: Mapped[Length] = mapped_column(LengthColumn)
    measured: Mapped[Length] = mapped_column(LengthColumn)

    survey: Mapped[WearSurveyRow] = relationship(back_populates="lays")


class BreakSurveyRow(Base):
    __tablename__ = "break_surveys"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    rope_serial: Mapped[RopeSerial] = mapped_column(
        RopeSerialColumn, ForeignKey("haul_ropes.serial")
    )
    taken: Mapped[date] = mapped_column(Date)
    inspector: Mapped[str] = mapped_column(String(64))

    rope: Mapped[HaulRopeRow] = relationship()
    observations: Mapped[list[BreakObservationRow]] = relationship(
        back_populates="survey", cascade="all, delete-orphan"
    )

    __table_args__ = (UniqueConstraint("rope_serial", "taken", name="one_break_survey_a_day"),)


class BreakObservationRow(Base):
    __tablename__ = "break_observations"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    survey_id: Mapped[int] = mapped_column(Integer, ForeignKey("break_surveys.id"))
    station: Mapped[Length] = mapped_column(LengthColumn)
    in_one_lay: Mapped[int] = mapped_column(Integer)
    in_six_lays: Mapped[int] = mapped_column(Integer)
    valley_breaks: Mapped[int] = mapped_column(Integer, default=0)
    at_splice: Mapped[bool] = mapped_column(Boolean, default=False)

    survey: Mapped[BreakSurveyRow] = relationship(back_populates="observations")

    __table_args__ = (
        CheckConstraint("in_six_lays >= in_one_lay", name="six_holds_one"),
        CheckConstraint("valley_breaks <= in_one_lay", name="valley_inside_one"),
    )


class MagneticTestRow(Base):
    __tablename__ = "magnetic_tests"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    rope_serial: Mapped[RopeSerial] = mapped_column(
        RopeSerialColumn, ForeignKey("haul_ropes.serial")
    )
    taken: Mapped[date] = mapped_column(Date)
    contractor: Mapped[str] = mapped_column(String(64))
    instrument: Mapped[str] = mapped_column(String(32))

    rope: Mapped[HaulRopeRow] = relationship()
    indications: Mapped[list[IndicationRow]] = relationship(
        back_populates="test", cascade="all, delete-orphan"
    )

    __table_args__ = (UniqueConstraint("rope_serial", "taken", name="one_test_a_day"),)


class IndicationRow(Base):
    __tablename__ = "indications"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    test_id: Mapped[int] = mapped_column(Integer, ForeignKey("magnetic_tests.id"))
    station: Mapped[Length] = mapped_column(LengthColumn)
    kind: Mapped[IndicationKind] = mapped_column(named_enum(IndicationKind, "indication_kind"))
    magnitude: Mapped[Decimal] = mapped_column(DecimalColumn)

    test: Mapped[MagneticTestRow] = relationship(back_populates="indications")


class SpliceRow(Base):
    __tablename__ = "splices"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    rope_serial: Mapped[RopeSerial] = mapped_column(
        RopeSerialColumn, ForeignKey("haul_ropes.serial")
    )
    station: Mapped[Length] = mapped_column(LengthColumn)
    made: Mapped[date] = mapped_column(Date)
    made_by: Mapped[str] = mapped_column(String(64))
    tucks: Mapped[int] = mapped_column(Integer)
    length: Mapped[Length] = mapped_column(LengthColumn)
    mill: Mapped[bool] = mapped_column(Boolean, default=False)

    rope: Mapped[HaulRopeRow] = relationship()

    __table_args__ = (
        UniqueConstraint("rope_serial", "station", name="one_splice_a_station"),
        CheckConstraint("tucks >= 4", name="four_tucks_at_least"),
    )


class TensionSystemRow(Base):
    __tablename__ = "tension_systems"

    lift_code: Mapped[LiftCode] = mapped_column(
        LiftCodeColumn, ForeignKey("lifts.code"), primary_key=True
    )
    kind: Mapped[TensionKind] = mapped_column(named_enum(TensionKind, "tension_kind"))
    nominal: Mapped[Load] = mapped_column(LoadColumn)
    travel: Mapped[Length] = mapped_column(LengthColumn)
    carriage: Mapped[Length] = mapped_column(LengthColumn)

    lift: Mapped[LiftRow] = relationship()
    readings: Mapped[list[TensionReadingRow]] = relationship(
        back_populates="system", cascade="all, delete-orphan"
    )

    __table_args__ = (CheckConstraint("travel > 0", name="travel_positive"),)


class TensionReadingRow(Base):
    __tablename__ = "tension_readings"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    lift_code: Mapped[LiftCode] = mapped_column(
        LiftCodeColumn, ForeignKey("tension_systems.lift_code")
    )
    taken: Mapped[datetime] = mapped_column(DateTime)
    carriage: Mapped[Length] = mapped_column(LengthColumn)
    measured: Mapped[Load] = mapped_column(LoadColumn)
    temperature: Mapped[Temperature] = mapped_column(TemperatureColumn)

    system: Mapped[TensionSystemRow] = relationship(back_populates="readings")
