"""Turning the installation into rows and back again.

Both directions are written here, next to each other, because the only way this
stays honest is if the two are read together. A field added to the domain and
not to the reader is a field that silently becomes a default the next time
anything is loaded, and that shows up months later as a tower whose deviation is
nought.

The rope is mapped separately from the lift on purpose. It has its own life, it
outlives the machine it is on, and the loader takes it as an argument rather
than digging it out of the lift row.
"""

from __future__ import annotations

from collections.abc import Sequence

from sheaveline.persistence.base import StorageError
from sheaveline.persistence.models.plant import (
    BrakeRow,
    CarrierRow,
    HaulRopeRow,
    LiftRow,
    RopeSpecRow,
    SurveyShotRow,
    TerminalRow,
    TowerRow,
)
from sheaveline.plant.carrier import Carrier, CarrierSet, Grip
from sheaveline.plant.classes import GripType
from sheaveline.plant.identity import LiftCode
from sheaveline.plant.lift import Lift
from sheaveline.plant.profile import GroundPoint, LineProfile
from sheaveline.plant.rope import HaulRope, RopeSpec
from sheaveline.plant.terminal import Brake, Bullwheel, DriveTrain, Terminal
from sheaveline.plant.tower import SheaveAssembly, Tower, TowerLine


def spec_to_row(spec: RopeSpec) -> RopeSpecRow:
    return RopeSpecRow(
        construction=spec.construction,
        grade=spec.grade,
        lay=spec.lay,
        nominal_diameter=spec.nominal_diameter,
        breaking_strength=spec.breaking_strength,
        weight=spec.weight,
    )


def spec_from_row(row: RopeSpecRow) -> RopeSpec:
    return RopeSpec(
        construction=row.construction,
        grade=row.grade,
        lay=row.lay,
        nominal_diameter=row.nominal_diameter,
        breaking_strength=row.breaking_strength,
        weight=row.weight,
    )


def rope_to_row(rope: HaulRope, lift_code: LiftCode | None = None) -> HaulRopeRow:
    return HaulRopeRow(
        serial=rope.serial,
        spec=spec_to_row(rope.spec),
        lift_code=lift_code,
        installed=rope.installed,
        length=rope.length,
        splices=rope.splices,
    )


def rope_from_row(row: HaulRopeRow) -> HaulRope:
    return HaulRope(
        serial=row.serial,
        spec=spec_from_row(row.spec),
        installed=row.installed,
        length=row.length,
        splices=row.splices,
    )


def terminal_to_row(terminal: Terminal, lift_code: LiftCode) -> TerminalRow:
    drive = terminal.drive
    row = TerminalRow(
        lift_code=lift_code,
        role=terminal.role,
        label=terminal.label,
        station=terminal.station,
        rope_elevation=terminal.rope_elevation,
        bullwheel_diameter=terminal.bullwheel.diameter,
        bullwheel_liner=terminal.bullwheel.liner,
        drive_type=None if drive is None else drive.drive_type,
        rated_horsepower=None if drive is None else drive.rated_horsepower,
        gearbox_ratio=None if drive is None else drive.gearbox_ratio,
        auxiliary=None if drive is None else drive.auxiliary,
        tension_travel=terminal.tension_travel,
    )
    if drive is not None:
        for brake in drive.brakes:
            row.brakes.append(
                BrakeRow(kind=brake.kind, torque=brake.torque, hydraulic=brake.hydraulic)
            )
    return row


def terminal_from_row(row: TerminalRow) -> Terminal:
    drive = None
    if row.drive_type is not None:
        if row.rated_horsepower is None or row.gearbox_ratio is None:
            raise StorageError(f"{row.label} has a drive type and no rating")
        drive = DriveTrain(
            drive_type=row.drive_type,
            rated_horsepower=row.rated_horsepower,
            gearbox_ratio=row.gearbox_ratio,
            brakes=tuple(
                Brake(kind=brake.kind, torque=brake.torque, hydraulic=brake.hydraulic)
                for brake in sorted(row.brakes, key=lambda one: one.kind.name)
            ),
            auxiliary=row.auxiliary,
        )
    return Terminal(
        role=row.role,
        label=row.label,
        station=row.station,
        rope_elevation=row.rope_elevation,
        bullwheel=Bullwheel(row.bullwheel_diameter, row.bullwheel_liner),
        drive=drive,
        tension_travel=row.tension_travel,
    )


def tower_to_row(tower: Tower, lift_code: LiftCode) -> TowerRow:
    return TowerRow(
        lift_code=lift_code,
        number=tower.number,
        station=tower.station,
        base_elevation=tower.base_elevation,
        height=tower.height,
        uphill_kind=tower.uphill.kind,
        uphill_sheaves=tower.uphill.sheaves,
        uphill_deviation=tower.uphill.deviation,
        downhill_kind=tower.downhill.kind,
        downhill_sheaves=tower.downhill.sheaves,
        downhill_deviation=tower.downhill.deviation,
    )


def tower_from_row(row: TowerRow) -> Tower:
    return Tower(
        ref=LiftCode(row.lift_code.value).tower(row.number),
        station=row.station,
        base_elevation=row.base_elevation,
        height=row.height,
        uphill=SheaveAssembly(row.uphill_kind, row.uphill_sheaves, row.uphill_deviation),
        downhill=SheaveAssembly(row.downhill_kind, row.downhill_sheaves, row.downhill_deviation),
    )


def carrier_to_row(carrier: Carrier, lift_code: LiftCode) -> CarrierRow:
    grip = carrier.grip
    return CarrierRow(
        lift_code=lift_code,
        number=carrier.number,
        kind=carrier.kind,
        tare=carrier.tare,
        hanger=carrier.hanger,
        restraining_bar=carrier.restraining_bar,
        in_service=carrier.in_service,
        grip_kind=None if grip is None else grip.kind,
        grip_model=None if grip is None else grip.model,
        grip_jaws=None if grip is None else grip.jaws,
        grip_spring_pack=None if grip is None else grip.spring_pack,
        grip_rated_slip=None if grip is None else grip.rated_slip,
        grip_installed=None if grip is None else grip.installed,
    )


def carrier_from_row(row: CarrierRow) -> Carrier:
    grip = None
    if row.grip_kind is not None:
        if row.grip_model is None or row.grip_jaws is None or row.grip_rated_slip is None:
            raise StorageError(f"carrier {row.number} has half a grip")
        if row.grip_spring_pack is None or row.grip_installed is None:
            raise StorageError(f"carrier {row.number} has half a grip")
        grip = Grip(
            kind=GripType(row.grip_kind),
            model=row.grip_model,
            jaws=row.grip_jaws,
            spring_pack=row.grip_spring_pack,
            rated_slip=row.grip_rated_slip,
            installed=row.grip_installed,
        )
    return Carrier(
        number=row.number,
        kind=row.kind,
        tare=row.tare,
        hanger=row.hanger,
        grip=grip,
        restraining_bar=row.restraining_bar,
        in_service=row.in_service,
    )


def lift_to_row(lift: Lift) -> LiftRow:
    row = LiftRow(
        code=lift.code,
        name=lift.name,
        lift_class=lift.lift_class,
        bearing=lift.bearing,
        design_speed=lift.design_speed,
        commissioned=lift.commissioned,
    )
    row.terminals.append(terminal_to_row(lift.bottom, lift.code))
    row.terminals.append(terminal_to_row(lift.top, lift.code))
    for tower in lift.towers:
        row.towers.append(tower_to_row(tower, lift.code))
    for carrier in lift.carriers:
        row.carriers.append(carrier_to_row(carrier, lift.code))
    for point in lift.ground.points:
        row.shots.append(
            SurveyShotRow(lift_code=lift.code, station=point.station, elevation=point.elevation)
        )
    return row


def lift_from_row(row: LiftRow, rope: HaulRope) -> Lift:
    terminals = _terminals_of(row)
    return Lift(
        code=row.code,
        name=row.name,
        lift_class=row.lift_class,
        bearing=row.bearing,
        bottom=terminals[0],
        top=terminals[1],
        towers=TowerLine([tower_from_row(one) for one in row.towers]),
        carriers=CarrierSet([carrier_from_row(one) for one in row.carriers]),
        rope=rope,
        ground=LineProfile([GroundPoint(shot.station, shot.elevation) for shot in row.shots]),
        design_speed=row.design_speed,
        commissioned=row.commissioned,
    )


def _terminals_of(row: LiftRow) -> Sequence[Terminal]:
    if len(row.terminals) != 2:
        raise StorageError(f"{row.code} has {len(row.terminals)} terminals, not two")
    ordered = sorted(row.terminals, key=lambda one: one.station.mils)
    return [terminal_from_row(one) for one in ordered]
