"""What the installation looks like on the wire.

Read shapes only. The department does not create a chairlift over http, and
pretending otherwise would mean a body with four hundred fields in it that
nobody could fill in correctly. Machines are loaded from the survey through the
command line; this surface answers questions about them.

Every derived figure is computed here rather than stored, so the tablet at the
terminal cannot be looking at a ride time that was right in 2019.
"""

from __future__ import annotations

from datetime import date

from pydantic import BaseModel, Field

from sheaveline.api.schemas.measures import (
    AngleText,
    BearingText,
    DurationText,
    LengthFeet,
    LengthInches,
    LiftCodeText,
    RopeSerialText,
    SpeedText,
)
from sheaveline.plant.lift import Lift
from sheaveline.plant.span import Span
from sheaveline.plant.terminal import Terminal
from sheaveline.plant.tower import Tower


class AssemblyOut(BaseModel):
    kind: str
    sheaves: int
    deviation: AngleText


class TowerOut(BaseModel):
    number: int
    reference: str
    station: LengthFeet
    base_elevation: LengthFeet
    height: LengthFeet
    crossarm_elevation: LengthFeet
    uphill: AssemblyOut
    downhill: AssemblyOut

    @classmethod
    def of(cls, tower: Tower) -> TowerOut:
        return cls(
            number=tower.number,
            reference=str(tower.ref),
            station=tower.station,
            base_elevation=tower.base_elevation,
            height=tower.height,
            crossarm_elevation=tower.crossarm_elevation(),
            uphill=AssemblyOut(
                kind=tower.uphill.kind.value,
                sheaves=tower.uphill.sheaves,
                deviation=tower.uphill.deviation,
            ),
            downhill=AssemblyOut(
                kind=tower.downhill.kind.value,
                sheaves=tower.downhill.sheaves,
                deviation=tower.downhill.deviation,
            ),
        )


class DriveOut(BaseModel):
    drive_type: str
    rated_horsepower: int
    auxiliary: str | None
    brakes: list[str]
    has_standby_power: bool


class TerminalOut(BaseModel):
    label: str
    role: str
    station: LengthFeet
    rope_elevation: LengthFeet
    bullwheel_diameter: LengthFeet
    bullwheel_liner: str
    tension_travel: LengthFeet | None
    drive: DriveOut | None

    @classmethod
    def of(cls, terminal: Terminal) -> TerminalOut:
        drive = None
        if terminal.drive is not None:
            drive = DriveOut(
                drive_type=terminal.drive.drive_type.value,
                rated_horsepower=terminal.drive.rated_horsepower,
                auxiliary=(
                    None if terminal.drive.auxiliary is None else terminal.drive.auxiliary.value
                ),
                brakes=[brake.kind.value for brake in terminal.drive.brakes],
                has_standby_power=terminal.drive.has_standby_power(),
            )
        return cls(
            label=terminal.label,
            role=terminal.role.value,
            station=terminal.station,
            rope_elevation=terminal.rope_elevation,
            bullwheel_diameter=terminal.bullwheel.diameter,
            bullwheel_liner=terminal.bullwheel.liner,
            tension_travel=terminal.tension_travel,
            drive=drive,
        )


class FleetOut(BaseModel):
    kind: str
    seats_each: int
    on_line: int
    withdrawn: int
    seats_on_line: int
    missing_numbers: list[int]


class RopeOut(BaseModel):
    serial: RopeSerialText
    construction: str
    grade: str
    lay: str
    diameter: LengthInches
    length: LengthFeet
    installed: date
    splices: int


class SpanOut(BaseModel):
    index: int
    label: str
    horizontal: LengthFeet
    rise: LengthFeet
    chord: LengthFeet
    slope: AngleText

    @classmethod
    def of(cls, span: Span) -> SpanOut:
        return cls(
            index=span.index,
            label=span.label,
            horizontal=span.horizontal(),
            rise=span.rise(),
            chord=span.chord(),
            slope=span.slope(),
        )


class LiftSummaryOut(BaseModel):
    code: LiftCodeText
    name: str
    lift_class: str
    design_speed: SpeedText
    carriers_on_line: int


class LiftOut(BaseModel):
    code: LiftCodeText
    name: str
    lift_class: str
    bearing: BearingText
    design_speed: SpeedText
    commissioned: date
    horizontal_length: LengthFeet
    vertical_rise: LengthFeet
    slope_length: LengthFeet
    ride_time: DurationText
    rope_length_agrees: bool
    terminals: list[TerminalOut]
    towers: list[TowerOut]
    fleet: FleetOut
    rope: RopeOut
    spans: list[SpanOut] = Field(default_factory=list)

    @classmethod
    def of(cls, lift: Lift) -> LiftOut:
        return cls(
            code=lift.code,
            name=lift.name,
            lift_class=lift.lift_class.value,
            bearing=lift.bearing,
            design_speed=lift.design_speed,
            commissioned=lift.commissioned,
            horizontal_length=lift.horizontal_length(),
            vertical_rise=lift.vertical_rise(),
            slope_length=lift.slope_length(),
            ride_time=lift.ride_time(),
            rope_length_agrees=lift.rope_length_agrees(),
            terminals=[TerminalOut.of(lift.bottom), TerminalOut.of(lift.top)],
            towers=[TowerOut.of(tower) for tower in lift.towers],
            fleet=FleetOut(
                kind=lift.carriers.kind.value,
                seats_each=lift.carriers.kind.seats,
                on_line=len(lift.carriers.on_line()),
                withdrawn=len(lift.carriers.withdrawn()),
                seats_on_line=lift.carriers.seats_on_line(),
                missing_numbers=list(lift.carriers.missing_numbers()),
            ),
            rope=RopeOut(
                serial=lift.rope.serial,
                construction=lift.rope.spec.construction.value,
                grade=lift.rope.spec.grade.value,
                lay=lift.rope.spec.lay.value,
                diameter=lift.rope.diameter,
                length=lift.rope.length,
                installed=lift.rope.installed,
                splices=lift.rope.splices,
            ),
            spans=[SpanOut.of(span) for span in lift.spans()],
        )

    @classmethod
    def summary_of(cls, lift: Lift) -> LiftSummaryOut:
        return LiftSummaryOut(
            code=lift.code,
            name=lift.name,
            lift_class=lift.lift_class.value,
            design_speed=lift.design_speed,
            carriers_on_line=lift.carriers_on_line(),
        )
