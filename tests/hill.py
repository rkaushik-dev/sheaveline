"""The hill the tests are written against.

One resort, built once here so that every test that needs a whole machine does
not build one of its own. John Paul is the detachable quad on the north face,
Strawberry is the fixed grip triple that serves the beginner terrain, and Wildcat
is the surface tow at the base.
"""

from datetime import date
from decimal import Decimal

from sheaveline.measures.angle import Angle, Bearing
from sheaveline.measures.length import Length
from sheaveline.measures.load import Load, LoadPerFoot
from sheaveline.measures.speed import Speed
from sheaveline.plant.carrier import Carrier, CarrierSet, Grip
from sheaveline.plant.classes import (
    CarrierType,
    DriveType,
    GripType,
    LiftClass,
    TerminalRole,
)
from sheaveline.plant.identity import LiftCode, RopeSerial
from sheaveline.plant.lift import Lift
from sheaveline.plant.profile import LineProfile
from sheaveline.plant.rope import (
    HaulRope,
    LayDirection,
    RopeConstruction,
    RopeGrade,
    RopeSpec,
)
from sheaveline.plant.terminal import Brake, BrakeKind, Bullwheel, DriveTrain, Terminal
from sheaveline.plant.tower import AssemblyKind, SheaveAssembly, Tower, TowerLine

JOHN_PAUL = LiftCode("JP")
STRAWBERRY = LiftCode("SB")
WILDCAT = LiftCode("WC")

JP_SURVEY = [
    (0, 6400),
    (300, 6448),
    (600, 6496),
    (900, 6580),
    (1200, 6700),
    (1600, 6830),
    (2000, 6940),
    (2400, 7040),
    (2800, 7190),
    (3200, 7360),
    (3600, 7520),
    (4000, 7660),
    (4400, 7800),
    (4800, 7920),
]


def support_assembly(sheaves: int = 4, degrees: str = "1.4") -> SheaveAssembly:
    return SheaveAssembly(AssemblyKind.SUPPORT, sheaves, Angle.from_degrees(degrees))


def hold_down_assembly(sheaves: int = 4, degrees: str = "-1.1") -> SheaveAssembly:
    return SheaveAssembly(AssemblyKind.HOLD_DOWN, sheaves, Angle.from_degrees(degrees))


def drive_train(**overrides: object) -> DriveTrain:
    fields: dict[str, object] = {
        "drive_type": DriveType.AC_DRIVE,
        "rated_horsepower": 700,
        "gearbox_ratio": Decimal("18.5"),
        "brakes": (Brake(BrakeKind.SERVICE, 900), Brake(BrakeKind.EMERGENCY, 2400)),
        "auxiliary": DriveType.DIESEL_HYDRAULIC,
    }
    fields.update(overrides)
    return DriveTrain(**fields)  # type: ignore[arg-type]


def bullwheel(feet: int = 11, inches: int = 6) -> Bullwheel:
    return Bullwheel(Length.from_feet_inches(feet, inches), "polyurethane")


def jp_rope_spec() -> RopeSpec:
    return RopeSpec(
        construction=RopeConstruction.SIX_BY_TWENTY_FIVE,
        grade=RopeGrade.EIP,
        lay=LayDirection.RIGHT_REGULAR,
        nominal_diameter=Length.from_inches("1.378"),
        breaking_strength=Load.from_pounds(196000),
        weight=LoadPerFoot.from_pounds_per_foot("3.12"),
    )


def jp_towers() -> TowerLine:
    layout = [
        (1, 600, 6496, 34),
        (2, 1200, 6700, 38),
        (3, 1800, 6880, 30),
        (4, 2400, 7040, 42),
        (5, 3000, 7280, 36),
        (6, 3600, 7520, 32),
        (7, 4200, 7730, 28),
    ]
    towers = []
    for number, station, base, height in layout:
        down = hold_down_assembly() if number == 3 else support_assembly()
        towers.append(
            Tower(
                ref=JOHN_PAUL.tower(number),
                station=Length.from_feet(station),
                base_elevation=Length.from_feet(base),
                height=Length.from_feet(height),
                uphill=support_assembly(6 if number in (4, 5) else 4),
                downhill=down,
            )
        )
    return TowerLine(towers)


def jp_carriers(count: int = 84) -> CarrierSet:
    grip = Grip(
        kind=GripType.DETACHABLE,
        model="AK6",
        jaws=1,
        spring_pack="blue",
        rated_slip=Load.from_pounds(2600),
        installed=date(2019, 10, 4),
    )
    return CarrierSet(
        [
            Carrier(
                number=number,
                kind=CarrierType.CHAIR_4,
                tare=Load.from_pounds(410),
                hanger=Length.from_feet_inches(6, 4),
                grip=grip,
                restraining_bar=True,
            )
            for number in range(1, count + 1)
        ]
    )


def john_paul(**overrides: object) -> Lift:
    """The detachable quad on the north face."""
    fields: dict[str, object] = {
        "code": JOHN_PAUL,
        "name": "John Paul",
        "lift_class": LiftClass.DETACHABLE_CHAIR,
        "bearing": Bearing.from_degrees(72),
        "bottom": Terminal(
            role=TerminalRole.DRIVE,
            label="John Paul base",
            station=Length.from_feet(0),
            rope_elevation=Length.from_feet(6412),
            bullwheel=bullwheel(),
            drive=drive_train(),
        ),
        "top": Terminal(
            role=TerminalRole.RETURN_TENSION,
            label="John Paul summit",
            station=Length.from_feet(4800),
            rope_elevation=Length.from_feet(7938),
            bullwheel=bullwheel(),
            tension_travel=Length.from_feet(16),
        ),
        "towers": jp_towers(),
        "carriers": jp_carriers(),
        "rope": HaulRope(
            serial=RopeSerial.parse("FAT-2019-04412"),
            spec=jp_rope_spec(),
            installed=date(2019, 10, 4),
            length=Length.from_feet(10152),
            splices=1,
        ),
        "ground": LineProfile.from_survey(JP_SURVEY),
        "design_speed": Speed.from_feet_per_minute(1000),
        "commissioned": date(2004, 12, 3),
    }
    fields.update(overrides)
    return Lift(**fields)  # type: ignore[arg-type]
