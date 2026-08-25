"""The tables the installation lives in.

Two things here are deliberate and both come from the same lesson. Every child
table carries a relationship back to its parent even where nothing reads it,
because a bare foreign key column does not tell the unit of work which row to
insert first, and a tower written before its lift is a foreign key violation
that only shows up under a real database.

And every enum is stored by its name rather than its value. The values are
sentences a person reads, they get reworded, and a reworded value silently
orphans every row written before the change.
"""

from __future__ import annotations

from datetime import date
from decimal import Decimal

from sqlalchemy import (
    Boolean,
    CheckConstraint,
    Date,
    Enum,
    ForeignKey,
    Index,
    Integer,
    String,
    UniqueConstraint,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from sheaveline.measures.angle import Angle, Bearing
from sheaveline.measures.length import Length
from sheaveline.measures.load import Load, LoadPerFoot
from sheaveline.measures.speed import Speed
from sheaveline.measures.torque import Torque
from sheaveline.persistence.base import Base
from sheaveline.persistence.types import (
    AngleColumn,
    BearingColumn,
    DecimalColumn,
    LengthColumn,
    LiftCodeColumn,
    LoadColumn,
    LoadPerFootColumn,
    RopeSerialColumn,
    SpeedColumn,
    TorqueColumn,
    TowerRefColumn,
)
from sheaveline.plant.classes import (
    CarrierType,
    DriveType,
    GripType,
    LiftClass,
    TerminalRole,
)
from sheaveline.plant.identity import LiftCode, RopeSerial, TowerRef
from sheaveline.plant.rope import LayDirection, RopeConstruction, RopeGrade
from sheaveline.plant.terminal import BrakeKind
from sheaveline.plant.tower import AssemblyKind


def named_enum(kind: type, name: str) -> Enum:
    """Store the member name, not the sentence a person reads."""
    return Enum(
        kind,
        name=name,
        native_enum=False,
        length=32,
        values_callable=lambda members: [member.name for member in members],
    )


class LiftRow(Base):
    __tablename__ = "lifts"

    code: Mapped[LiftCode] = mapped_column(LiftCodeColumn, primary_key=True)
    name: Mapped[str] = mapped_column(String(64))
    lift_class: Mapped[LiftClass] = mapped_column(named_enum(LiftClass, "lift_class"))
    bearing: Mapped[Bearing] = mapped_column(BearingColumn)
    design_speed: Mapped[Speed] = mapped_column(SpeedColumn)
    commissioned: Mapped[date] = mapped_column(Date)

    terminals: Mapped[list[TerminalRow]] = relationship(
        back_populates="lift", cascade="all, delete-orphan"
    )
    towers: Mapped[list[TowerRow]] = relationship(
        back_populates="lift", cascade="all, delete-orphan"
    )
    carriers: Mapped[list[CarrierRow]] = relationship(
        back_populates="lift", cascade="all, delete-orphan"
    )
    shots: Mapped[list[SurveyShotRow]] = relationship(
        back_populates="lift", cascade="all, delete-orphan"
    )
    ropes: Mapped[list[HaulRopeRow]] = relationship(back_populates="lift")

    __table_args__ = (CheckConstraint("design_speed > 0", name="speed_positive"),)


class TerminalRow(Base):
    __tablename__ = "terminals"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    lift_code: Mapped[LiftCode] = mapped_column(LiftCodeColumn, ForeignKey("lifts.code"))
    role: Mapped[TerminalRole] = mapped_column(named_enum(TerminalRole, "terminal_role"))
    label: Mapped[str] = mapped_column(String(64))
    station: Mapped[Length] = mapped_column(LengthColumn)
    rope_elevation: Mapped[Length] = mapped_column(LengthColumn)
    bullwheel_diameter: Mapped[Length] = mapped_column(LengthColumn)
    bullwheel_liner: Mapped[str] = mapped_column(String(32))
    drive_type: Mapped[DriveType | None] = mapped_column(
        named_enum(DriveType, "drive_type"), nullable=True
    )
    rated_horsepower: Mapped[int | None] = mapped_column(Integer, nullable=True)
    gearbox_ratio: Mapped[Decimal | None] = mapped_column(DecimalColumn, nullable=True)
    auxiliary: Mapped[DriveType | None] = mapped_column(
        named_enum(DriveType, "auxiliary_type"), nullable=True
    )
    tension_travel: Mapped[Length | None] = mapped_column(LengthColumn, nullable=True)

    lift: Mapped[LiftRow] = relationship(back_populates="terminals")
    brakes: Mapped[list[BrakeRow]] = relationship(
        back_populates="terminal", cascade="all, delete-orphan"
    )

    __table_args__ = (UniqueConstraint("lift_code", "label", name="one_label_a_lift"),)


class BrakeRow(Base):
    __tablename__ = "brakes"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    terminal_id: Mapped[int] = mapped_column(Integer, ForeignKey("terminals.id"))
    kind: Mapped[BrakeKind] = mapped_column(named_enum(BrakeKind, "brake_kind"))
    torque: Mapped[Torque] = mapped_column(TorqueColumn)
    hydraulic: Mapped[bool] = mapped_column(Boolean, default=True)

    terminal: Mapped[TerminalRow] = relationship(back_populates="brakes")

    __table_args__ = (UniqueConstraint("terminal_id", "kind", name="one_brake_a_kind"),)


class TowerRow(Base):
    __tablename__ = "towers"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    lift_code: Mapped[LiftCode] = mapped_column(LiftCodeColumn, ForeignKey("lifts.code"))
    number: Mapped[int] = mapped_column(Integer)
    station: Mapped[Length] = mapped_column(LengthColumn)
    base_elevation: Mapped[Length] = mapped_column(LengthColumn)
    height: Mapped[Length] = mapped_column(LengthColumn)
    uphill_kind: Mapped[AssemblyKind] = mapped_column(named_enum(AssemblyKind, "uphill_kind"))
    uphill_sheaves: Mapped[int] = mapped_column(Integer)
    uphill_deviation: Mapped[Angle] = mapped_column(AngleColumn)
    downhill_kind: Mapped[AssemblyKind] = mapped_column(named_enum(AssemblyKind, "downhill_kind"))
    downhill_sheaves: Mapped[int] = mapped_column(Integer)
    downhill_deviation: Mapped[Angle] = mapped_column(AngleColumn)

    lift: Mapped[LiftRow] = relationship(back_populates="towers")

    __table_args__ = (
        UniqueConstraint("lift_code", "number", name="one_number_a_line"),
        CheckConstraint("number >= 1", name="towers_from_one"),
    )


class CarrierRow(Base):
    __tablename__ = "carriers"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    lift_code: Mapped[LiftCode] = mapped_column(LiftCodeColumn, ForeignKey("lifts.code"))
    number: Mapped[int] = mapped_column(Integer)
    kind: Mapped[CarrierType] = mapped_column(named_enum(CarrierType, "carrier_type"))
    tare: Mapped[Load] = mapped_column(LoadColumn)
    hanger: Mapped[Length] = mapped_column(LengthColumn)
    restraining_bar: Mapped[bool] = mapped_column(Boolean, default=False)
    in_service: Mapped[bool] = mapped_column(Boolean, default=True)
    grip_kind: Mapped[GripType | None] = mapped_column(
        named_enum(GripType, "grip_kind"), nullable=True
    )
    grip_model: Mapped[str | None] = mapped_column(String(32), nullable=True)
    grip_jaws: Mapped[int | None] = mapped_column(Integer, nullable=True)
    grip_spring_pack: Mapped[str | None] = mapped_column(String(32), nullable=True)
    grip_rated_slip: Mapped[Load | None] = mapped_column(LoadColumn, nullable=True)
    grip_installed: Mapped[date | None] = mapped_column(Date, nullable=True)

    lift: Mapped[LiftRow] = relationship(back_populates="carriers")

    __table_args__ = (
        UniqueConstraint("lift_code", "number", name="one_number_a_fleet"),
        CheckConstraint("number >= 1", name="carriers_from_one"),
    )


class SurveyShotRow(Base):
    __tablename__ = "survey_shots"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    lift_code: Mapped[LiftCode] = mapped_column(LiftCodeColumn, ForeignKey("lifts.code"))
    station: Mapped[Length] = mapped_column(LengthColumn)
    elevation: Mapped[Length] = mapped_column(LengthColumn)

    lift: Mapped[LiftRow] = relationship(back_populates="shots")

    __table_args__ = (UniqueConstraint("lift_code", "station", name="one_shot_a_station"),)


class RopeSpecRow(Base):
    __tablename__ = "rope_specs"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    construction: Mapped[RopeConstruction] = mapped_column(
        named_enum(RopeConstruction, "rope_construction")
    )
    grade: Mapped[RopeGrade] = mapped_column(named_enum(RopeGrade, "rope_grade"))
    lay: Mapped[LayDirection] = mapped_column(named_enum(LayDirection, "lay_direction"))
    nominal_diameter: Mapped[Length] = mapped_column(LengthColumn)
    breaking_strength: Mapped[Load] = mapped_column(LoadColumn)
    weight: Mapped[LoadPerFoot] = mapped_column(LoadPerFootColumn)

    ropes: Mapped[list[HaulRopeRow]] = relationship(back_populates="spec")


class HaulRopeRow(Base):
    __tablename__ = "haul_ropes"

    serial: Mapped[RopeSerial] = mapped_column(RopeSerialColumn, primary_key=True)
    spec_id: Mapped[int] = mapped_column(Integer, ForeignKey("rope_specs.id"))
    lift_code: Mapped[LiftCode | None] = mapped_column(
        LiftCodeColumn, ForeignKey("lifts.code"), nullable=True
    )
    installed: Mapped[date] = mapped_column(Date)
    length: Mapped[Length] = mapped_column(LengthColumn)
    splices: Mapped[int] = mapped_column(Integer, default=1)

    spec: Mapped[RopeSpecRow] = relationship(back_populates="ropes")
    lift: Mapped[LiftRow | None] = relationship(back_populates="ropes")

    __table_args__ = (CheckConstraint("splices >= 0", name="splices_not_negative"),)


class AlignmentSurveyRow(Base):
    """One autumn's total station work on one machine."""

    __tablename__ = "alignment_surveys"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    lift_code: Mapped[LiftCode] = mapped_column(LiftCodeColumn, ForeignKey("lifts.code"))
    taken: Mapped[date] = mapped_column(Date)
    surveyor: Mapped[str] = mapped_column(String(64))

    lift: Mapped[LiftRow] = relationship()
    shots: Mapped[list[TowerShotRow]] = relationship(
        back_populates="survey", cascade="all, delete-orphan"
    )

    __table_args__ = (
        UniqueConstraint("lift_code", "taken", name="one_alignment_survey_a_day"),
        Index("ix_alignment_by_machine", "lift_code", "taken"),
    )


class TowerShotRow(Base):
    """One tower in one survey, as the instrument read it."""

    __tablename__ = "tower_shots"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    survey_id: Mapped[int] = mapped_column(Integer, ForeignKey("alignment_surveys.id"))
    tower_ref: Mapped[TowerRef] = mapped_column(TowerRefColumn)
    station: Mapped[Length] = mapped_column(LengthColumn)
    crossarm_elevation: Mapped[Length] = mapped_column(LengthColumn)
    offset_at_top: Mapped[Length] = mapped_column(LengthColumn)

    survey: Mapped[AlignmentSurveyRow] = relationship(back_populates="shots")

    __table_args__ = (
        UniqueConstraint("survey_id", "tower_ref", name="one_shot_a_tower"),
        CheckConstraint("station >= 0", name="shot_station_not_negative"),
    )
