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
from sheaveline.measures.torque import Torque
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
from sheaveline.plant.registry import Resort
from sheaveline.plant.rope import (
    HaulRope,
    LayDirection,
    RopeConstruction,
    RopeGrade,
    RopeSpec,
)
from sheaveline.plant.terminal import Brake, BrakeKind, Bullwheel, DriveTrain, Terminal
from sheaveline.plant.tower import AssemblyKind, SheaveAssembly, Tower, TowerLine
from sheaveline.schedule.daylight import Site
from sheaveline.schedule.season import HolidayPeriod, Season

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
        "brakes": (
            Brake(BrakeKind.SERVICE, Torque.from_pound_feet(900)),
            Brake(BrakeKind.EMERGENCY, Torque.from_pound_feet(2400)),
        ),
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


SB_SURVEY = [
    (0, 6400),
    (400, 6460),
    (800, 6530),
    (1200, 6610),
    (1600, 6700),
    (2000, 6790),
]

WC_SURVEY = [
    (0, 6390),
    (200, 6420),
    (400, 6455),
    (600, 6492),
]


def sb_towers() -> TowerLine:
    layout = [(1, 400, 6460, 26), (2, 900, 6545, 30), (3, 1500, 6675, 24)]
    return TowerLine(
        [
            Tower(
                ref=STRAWBERRY.tower(number),
                station=Length.from_feet(station),
                base_elevation=Length.from_feet(base),
                height=Length.from_feet(height),
                uphill=support_assembly(),
                downhill=support_assembly(),
            )
            for number, station, base, height in layout
        ]
    )


def sb_carriers(count: int = 46) -> CarrierSet:
    grip = Grip(
        kind=GripType.FIXED,
        model="TB41",
        jaws=2,
        spring_pack="green",
        rated_slip=Load.from_pounds(2100),
        installed=date(2011, 9, 20),
    )
    return CarrierSet(
        [
            Carrier(
                number=number,
                kind=CarrierType.CHAIR_3,
                tare=Load.from_pounds(295),
                hanger=Length.from_feet_inches(5, 10),
                grip=grip,
                restraining_bar=True,
            )
            for number in range(1, count + 1)
        ]
    )


def strawberry(**overrides: object) -> Lift:
    """The fixed grip triple over the beginner terrain."""
    fields: dict[str, object] = {
        "code": STRAWBERRY,
        "name": "Strawberry",
        "lift_class": LiftClass.FIXED_CHAIR,
        "bearing": Bearing.from_degrees(310),
        "bottom": Terminal(
            role=TerminalRole.DRIVE_TENSION,
            label="Strawberry base",
            station=Length.from_feet(0),
            rope_elevation=Length.from_feet(6418),
            bullwheel=bullwheel(feet=9, inches=0),
            drive=drive_train(rated_horsepower=310, auxiliary=None),
            tension_travel=Length.from_feet(9),
        ),
        "top": Terminal(
            role=TerminalRole.RETURN,
            label="Strawberry top",
            station=Length.from_feet(2000),
            rope_elevation=Length.from_feet(6812),
            bullwheel=bullwheel(feet=9, inches=0),
        ),
        "towers": sb_towers(),
        "carriers": sb_carriers(),
        "rope": HaulRope(
            serial=RopeSerial.parse("BRI-2011-01887"),
            spec=RopeSpec(
                construction=RopeConstruction.SIX_BY_TWENTY_SIX,
                grade=RopeGrade.EIP,
                lay=LayDirection.RIGHT_REGULAR,
                nominal_diameter=Length.from_inches("1.102"),
                breaking_strength=Load.from_pounds(124000),
                weight=LoadPerFoot.from_pounds_per_foot("1.98"),
            ),
            installed=date(2011, 9, 20),
            length=Length.from_feet(4113),
            splices=1,
        ),
        "ground": LineProfile.from_survey(SB_SURVEY),
        "design_speed": Speed.from_feet_per_minute(450),
        "commissioned": date(1988, 11, 18),
    }
    fields.update(overrides)
    return Lift(**fields)  # type: ignore[arg-type]


def wc_towers() -> TowerLine:
    layout = [(1, 200, 6420, 18), (2, 450, 6467, 18)]
    return TowerLine(
        [
            Tower(
                ref=WILDCAT.tower(number),
                station=Length.from_feet(station),
                base_elevation=Length.from_feet(base),
                height=Length.from_feet(height),
                uphill=support_assembly(2, "0.9"),
                downhill=support_assembly(2, "0.9"),
            )
            for number, station, base, height in layout
        ]
    )


def wc_carriers(count: int = 22) -> CarrierSet:
    return CarrierSet(
        [
            Carrier(
                number=number,
                kind=CarrierType.TBAR,
                tare=Load.from_pounds(38),
                hanger=Length.from_feet_inches(11, 0),
                grip=None,
                restraining_bar=False,
            )
            for number in range(1, count + 1)
        ]
    )


def wildcat(**overrides: object) -> Lift:
    """The surface tow at the base area."""
    fields: dict[str, object] = {
        "code": WILDCAT,
        "name": "Wildcat",
        "lift_class": LiftClass.SURFACE_TOW,
        "bearing": Bearing.from_degrees(145),
        "bottom": Terminal(
            role=TerminalRole.DRIVE,
            label="Wildcat base",
            station=Length.from_feet(0),
            rope_elevation=Length.from_feet(6404),
            bullwheel=bullwheel(feet=6, inches=6),
            drive=drive_train(
                drive_type=DriveType.DIESEL_HYDRAULIC,
                rated_horsepower=75,
                brakes=(Brake(BrakeKind.SERVICE, Torque.from_pound_feet(300)),),
                auxiliary=None,
            ),
        ),
        "top": Terminal(
            role=TerminalRole.RETURN_TENSION,
            label="Wildcat top",
            station=Length.from_feet(600),
            rope_elevation=Length.from_feet(6506),
            bullwheel=bullwheel(feet=6, inches=6),
            tension_travel=Length.from_feet(6),
        ),
        "towers": wc_towers(),
        "carriers": wc_carriers(),
        "rope": HaulRope(
            serial=RopeSerial.parse("BRI-2016-00294"),
            spec=RopeSpec(
                construction=RopeConstruction.EIGHT_BY_NINETEEN,
                grade=RopeGrade.IPS,
                lay=LayDirection.RIGHT_REGULAR,
                nominal_diameter=Length.from_inches("0.625"),
                breaking_strength=Load.from_pounds(35000),
                weight=LoadPerFoot.from_pounds_per_foot("0.63"),
            ),
            installed=date(2016, 10, 11),
            length=Length.from_feet(1238),
            splices=1,
        ),
        "ground": LineProfile.from_survey(WC_SURVEY),
        "design_speed": Speed.from_feet_per_minute(560),
        "commissioned": date(1996, 12, 6),
    }
    fields.update(overrides)
    return Lift(**fields)  # type: ignore[arg-type]


def the_site() -> Site:
    return Site("Ogden Peak", 41.216, -111.857, -420)


def the_season() -> Season:
    return Season(
        label="2024/25",
        opens=date(2024, 11, 29),
        closes=date(2025, 4, 20),
        preview_from=date(2024, 11, 23),
        spring_from=date(2025, 3, 24),
        holidays=(
            HolidayPeriod("christmas", date(2024, 12, 21), date(2025, 1, 1)),
            HolidayPeriod("presidents", date(2025, 2, 15), date(2025, 2, 23)),
        ),
        closures=frozenset({date(2025, 1, 14)}),
    )


def the_resort() -> Resort:
    return Resort.of(
        "Ogden Peak",
        the_site(),
        the_season(),
        [john_paul(), strawberry(), wildcat()],
    )
