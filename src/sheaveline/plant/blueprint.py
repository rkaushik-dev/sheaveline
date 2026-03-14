"""Building a machine from the drawing, and writing one back out to it.

A lift arrives as a document. The surveyor sends elevations, the manufacturer
sends a tower schedule and a rope spec, and somebody types the rest. So there is
one shape that all of it can be written in, and one function that turns it into
a Lift with every check in the domain applied.

Both directions again, because the office also has to hand the drawing back:
when a machine is modified the board wants the description it was accepted
under, and reconstructing that from the database by hand is how the wrong figure
gets filed.

Every measure in the document is text. A tower height of 55 is ambiguous and a
tower height of "55 ft" is not, and the one place that matters is the one place
somebody is typing.
"""

from __future__ import annotations

from collections.abc import Mapping, Sequence
from datetime import date
from decimal import Decimal
from typing import Any

from sheaveline.measures.angle import Angle, Bearing
from sheaveline.measures.length import Length
from sheaveline.measures.load import Load, LoadPerFoot
from sheaveline.measures.speed import Speed
from sheaveline.measures.torque import Torque
from sheaveline.plant.carrier import Carrier, CarrierSet, Grip
from sheaveline.plant.classes import (
    CarrierType,
    DriveType,
    GripType,
    LiftClass,
    PlantError,
    TerminalRole,
)
from sheaveline.plant.identity import LiftCode, RopeSerial
from sheaveline.plant.lift import Lift
from sheaveline.plant.profile import GroundPoint, LineProfile
from sheaveline.plant.rope import (
    HaulRope,
    LayDirection,
    RopeConstruction,
    RopeGrade,
    RopeSpec,
)
from sheaveline.plant.terminal import Brake, BrakeKind, Bullwheel, DriveTrain, Terminal
from sheaveline.plant.tower import AssemblyKind, SheaveAssembly, Tower, TowerLine

Document = Mapping[str, Any]


def _need(document: Document, key: str, where: str) -> Any:
    if key not in document:
        raise PlantError(f"{where} has no {key}")
    return document[key]


def _named(kind: type, value: str, where: str) -> Any:
    for member in kind:  # type: ignore[attr-defined]
        if member.name == value or member.value == value:
            return member
    raise PlantError(f"{where} names {value!r}, which is not a {kind.__name__}")


def _day(value: str, where: str) -> date:
    try:
        return date.fromisoformat(value)
    except ValueError as bad:
        raise PlantError(f"{where} has {value!r} where a date belongs") from bad


def read_assembly(document: Document, where: str) -> SheaveAssembly:
    return SheaveAssembly(
        kind=_named(AssemblyKind, _need(document, "kind", where), where),
        sheaves=int(_need(document, "sheaves", where)),
        deviation=Angle.parse(_need(document, "deviation", where)),
    )


def write_assembly(assembly: SheaveAssembly) -> dict[str, Any]:
    return {
        "kind": assembly.kind.name,
        "sheaves": assembly.sheaves,
        "deviation": assembly.deviation.format_degrees(3),
    }


def read_tower(document: Document, code: LiftCode) -> Tower:
    number = int(_need(document, "number", "a tower"))
    where = f"tower {number}"
    return Tower(
        ref=code.tower(number),
        station=Length.parse(_need(document, "station", where)),
        base_elevation=Length.parse(_need(document, "base_elevation", where)),
        height=Length.parse(_need(document, "height", where)),
        uphill=read_assembly(_need(document, "uphill", where), f"{where} uphill"),
        downhill=read_assembly(_need(document, "downhill", where), f"{where} downhill"),
    )


def write_tower(tower: Tower) -> dict[str, Any]:
    return {
        "number": tower.number,
        "station": f"{tower.station.feet:.2f} ft",
        "base_elevation": f"{tower.base_elevation.feet:.2f} ft",
        "height": f"{tower.height.feet:.2f} ft",
        "uphill": write_assembly(tower.uphill),
        "downhill": write_assembly(tower.downhill),
    }


def read_terminal(document: Document) -> Terminal:
    label = str(_need(document, "label", "a terminal"))
    role = _named(TerminalRole, _need(document, "role", label), label)
    drive = None
    if "drive" in document and document["drive"] is not None:
        drive = read_drive(document["drive"], label)
    travel = document.get("tension_travel")
    return Terminal(
        role=role,
        label=label,
        station=Length.parse(_need(document, "station", label)),
        rope_elevation=Length.parse(_need(document, "rope_elevation", label)),
        bullwheel=Bullwheel(
            Length.parse(_need(document, "bullwheel_diameter", label)),
            str(_need(document, "bullwheel_liner", label)),
        ),
        drive=drive,
        tension_travel=None if travel is None else Length.parse(travel),
    )


def read_drive(document: Document, where: str) -> DriveTrain:
    auxiliary = document.get("auxiliary")
    return DriveTrain(
        drive_type=_named(DriveType, _need(document, "drive_type", where), where),
        rated_horsepower=int(_need(document, "rated_horsepower", where)),
        gearbox_ratio=Decimal(str(_need(document, "gearbox_ratio", where))),
        brakes=tuple(
            Brake(
                kind=_named(BrakeKind, _need(one, "kind", where), where),
                torque=Torque.parse(_need(one, "torque", where)),
                hydraulic=bool(one.get("hydraulic", True)),
            )
            for one in _need(document, "brakes", where)
        ),
        auxiliary=None if auxiliary is None else _named(DriveType, auxiliary, where),
    )


def write_terminal(terminal: Terminal) -> dict[str, Any]:
    out: dict[str, Any] = {
        "label": terminal.label,
        "role": terminal.role.name,
        "station": f"{terminal.station.feet:.2f} ft",
        "rope_elevation": f"{terminal.rope_elevation.feet:.2f} ft",
        "bullwheel_diameter": terminal.bullwheel.diameter.format_inches(2),
        "bullwheel_liner": terminal.bullwheel.liner,
    }
    if terminal.tension_travel is not None:
        out["tension_travel"] = f"{terminal.tension_travel.feet:.2f} ft"
    if terminal.drive is not None:
        out["drive"] = {
            "drive_type": terminal.drive.drive_type.name,
            "rated_horsepower": terminal.drive.rated_horsepower,
            "gearbox_ratio": str(terminal.drive.gearbox_ratio),
            "auxiliary": (
                None if terminal.drive.auxiliary is None else terminal.drive.auxiliary.name
            ),
            "brakes": [
                {
                    "kind": brake.kind.name,
                    "torque": brake.torque.format_inch_pounds(),
                    "hydraulic": brake.hydraulic,
                }
                for brake in terminal.drive.brakes
            ],
        }
    return out


def read_carriers(document: Document) -> CarrierSet:
    where = "the fleet"
    kind = _named(CarrierType, _need(document, "kind", where), where)
    count = int(_need(document, "count", where))
    if count < 1:
        raise PlantError("a fleet has carriers in it")
    grip = None
    if "grip" in document and document["grip"] is not None:
        one = document["grip"]
        grip = Grip(
            kind=_named(GripType, _need(one, "kind", "the grip"), "the grip"),
            model=str(_need(one, "model", "the grip")),
            jaws=int(_need(one, "jaws", "the grip")),
            spring_pack=str(_need(one, "spring_pack", "the grip")),
            rated_slip=Load.parse(_need(one, "rated_slip", "the grip")),
            installed=_day(_need(one, "installed", "the grip"), "the grip"),
        )
    return CarrierSet(
        [
            Carrier(
                number=number,
                kind=kind,
                tare=Load.parse(_need(document, "tare", where)),
                hanger=Length.parse(_need(document, "hanger", where)),
                grip=grip,
                restraining_bar=bool(document.get("restraining_bar", False)),
            )
            for number in range(1, count + 1)
        ]
    )


def write_carriers(fleet: CarrierSet) -> dict[str, Any]:
    first = next(iter(fleet))
    out: dict[str, Any] = {
        "kind": fleet.kind.name,
        "count": len(fleet),
        "tare": first.tare.format_pounds(),
        "hanger": first.hanger.format_feet_inches(),
        "restraining_bar": first.restraining_bar,
    }
    if first.grip is not None:
        out["grip"] = {
            "kind": first.grip.kind.name,
            "model": first.grip.model,
            "jaws": first.grip.jaws,
            "spring_pack": first.grip.spring_pack,
            "rated_slip": first.grip.rated_slip.format_pounds(),
            "installed": first.grip.installed.isoformat(),
        }
    return out


def read_rope(document: Document) -> HaulRope:
    where = "the rope"
    spec = _need(document, "spec", where)
    return HaulRope(
        serial=RopeSerial.parse(_need(document, "serial", where)),
        spec=RopeSpec(
            construction=_named(RopeConstruction, _need(spec, "construction", where), where),
            grade=_named(RopeGrade, _need(spec, "grade", where), where),
            lay=_named(LayDirection, _need(spec, "lay", where), where),
            nominal_diameter=Length.parse(_need(spec, "nominal_diameter", where)),
            breaking_strength=Load.parse(_need(spec, "breaking_strength", where)),
            weight=LoadPerFoot.from_pounds_per_foot(str(_need(spec, "weight_per_foot", where))),
        ),
        installed=_day(_need(document, "installed", where), where),
        length=Length.parse(_need(document, "length", where)),
        splices=int(document.get("splices", 1)),
    )


def write_rope(rope: HaulRope) -> dict[str, Any]:
    return {
        "serial": str(rope.serial),
        "installed": rope.installed.isoformat(),
        "length": f"{rope.length.feet:.2f} ft",
        "splices": rope.splices,
        "spec": {
            "construction": rope.spec.construction.name,
            "grade": rope.spec.grade.name,
            "lay": rope.spec.lay.name,
            "nominal_diameter": rope.spec.nominal_diameter.format_inches(3),
            "breaking_strength": rope.spec.breaking_strength.format_pounds(),
            "weight_per_foot": str(rope.spec.weight.pounds_per_foot),
        },
    }


def read_survey(shots: Sequence[Mapping[str, str]]) -> LineProfile:
    if not shots:
        raise PlantError("the survey has no shots in it")
    return LineProfile(
        [
            GroundPoint(
                Length.parse(_need(one, "station", "a survey shot")),
                Length.parse(_need(one, "elevation", "a survey shot")),
            )
            for one in shots
        ]
    )


def write_survey(ground: LineProfile) -> list[dict[str, str]]:
    return [
        {
            "station": f"{one.station.feet:.2f} ft",
            "elevation": f"{one.elevation.feet:.2f} ft",
        }
        for one in ground.points
    ]


def read_lift(document: Document) -> Lift:
    """Build a machine from the drawing, with every domain check applied."""
    code = LiftCode.parse(_need(document, "code", "the drawing"))
    where = str(code)
    terminals = _need(document, "terminals", where)
    if len(terminals) != 2:
        raise PlantError(f"{where} has {len(terminals)} terminals, not two")
    bottom, top = (read_terminal(one) for one in terminals)
    if bottom.station > top.station:
        bottom, top = top, bottom
    return Lift(
        code=code,
        name=str(_need(document, "name", where)),
        lift_class=_named(LiftClass, _need(document, "lift_class", where), where),
        bearing=Bearing.parse(_need(document, "bearing", where)),
        bottom=bottom,
        top=top,
        towers=TowerLine([read_tower(one, code) for one in _need(document, "towers", where)]),
        carriers=read_carriers(_need(document, "fleet", where)),
        rope=read_rope(_need(document, "rope", where)),
        ground=read_survey(_need(document, "survey", where)),
        design_speed=Speed.parse(_need(document, "design_speed", where)),
        commissioned=_day(_need(document, "commissioned", where), where),
    )


def write_lift(lift: Lift) -> dict[str, Any]:
    """Hand the drawing back, in the shape it would be read from."""
    return {
        "code": str(lift.code),
        "name": lift.name,
        "lift_class": lift.lift_class.name,
        "bearing": lift.bearing.format_degrees(3),
        "design_speed": lift.design_speed.format_fpm(2),
        "commissioned": lift.commissioned.isoformat(),
        "terminals": [write_terminal(lift.bottom), write_terminal(lift.top)],
        "towers": [write_tower(one) for one in lift.towers],
        "fleet": write_carriers(lift.carriers),
        "rope": write_rope(lift.rope),
        "survey": write_survey(lift.ground),
    }
