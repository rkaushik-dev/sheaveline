from datetime import date
from decimal import Decimal

import pytest
from sqlalchemy import inspect, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from sheaveline.measures.angle import Angle, Bearing
from sheaveline.measures.length import Length
from sheaveline.measures.load import Load, LoadPerFoot
from sheaveline.measures.speed import Speed
from sheaveline.measures.torque import Torque
from sheaveline.persistence.base import build_engine, create_schema
from sheaveline.persistence.models.plant import (
    BrakeRow,
    CarrierRow,
    HaulRopeRow,
    LiftRow,
    RopeSpecRow,
    SurveyShotRow,
    TerminalRow,
    TowerRow,
    named_enum,
)
from sheaveline.plant.classes import (
    CarrierType,
    DriveType,
    GripType,
    LiftClass,
    TerminalRole,
)
from sheaveline.plant.identity import LiftCode, RopeSerial
from sheaveline.plant.rope import LayDirection, RopeConstruction, RopeGrade
from sheaveline.plant.terminal import BrakeKind
from sheaveline.plant.tower import AssemblyKind

JP = LiftCode("JP")


@pytest.fixture
def engine():
    made = build_engine()
    create_schema(made)
    yield made
    made.dispose()


def lift_row(code=JP):
    return LiftRow(
        code=code,
        name="John Paul",
        lift_class=LiftClass.DETACHABLE_CHAIR,
        bearing=Bearing.from_degrees(72),
        design_speed=Speed.from_feet_per_minute(1000),
        commissioned=date(2004, 12, 3),
    )


def terminal_row(lift=None, role=TerminalRole.DRIVE, label="base"):
    return TerminalRow(
        lift=lift,
        role=role,
        label=label,
        station=Length.from_feet(0),
        rope_elevation=Length.from_feet(6425),
        bullwheel_diameter=Length.from_feet_inches(11, 6),
        bullwheel_liner="polyurethane",
        drive_type=DriveType.AC_DRIVE,
        rated_horsepower=700,
        gearbox_ratio=Decimal("18.5"),
        auxiliary=DriveType.DIESEL_HYDRAULIC,
    )


def tower_row(lift=None, number=1):
    return TowerRow(
        lift=lift,
        number=number,
        station=Length.from_feet(600 * number),
        base_elevation=Length.from_feet(6496),
        height=Length.from_feet(55),
        uphill_kind=AssemblyKind.SUPPORT,
        uphill_sheaves=4,
        uphill_deviation=Angle.from_degrees("0.7"),
        downhill_kind=AssemblyKind.SUPPORT,
        downhill_sheaves=4,
        downhill_deviation=Angle.from_degrees("0.7"),
    )


def carrier_row(lift=None, number=1):
    return CarrierRow(
        lift=lift,
        number=number,
        kind=CarrierType.CHAIR_4,
        tare=Load.from_pounds(410),
        hanger=Length.from_feet_inches(6, 4),
        restraining_bar=True,
        in_service=True,
        grip_kind=GripType.DETACHABLE,
        grip_model="AK6",
        grip_jaws=1,
        grip_spring_pack="blue",
        grip_rated_slip=Load.from_pounds(2600),
        grip_installed=date(2019, 10, 4),
    )


def spec_row():
    return RopeSpecRow(
        construction=RopeConstruction.SIX_BY_TWENTY_FIVE,
        grade=RopeGrade.EIP,
        lay=LayDirection.RIGHT_REGULAR,
        nominal_diameter=Length.from_inches("1.378"),
        breaking_strength=Load.from_pounds(196000),
        weight=LoadPerFoot.from_pounds_per_foot("3.12"),
    )


def test_the_schema_carries_every_plant_table(engine):
    tables = set(inspect(engine).get_table_names())
    assert {
        "lifts",
        "terminals",
        "brakes",
        "towers",
        "carriers",
        "survey_shots",
        "rope_specs",
        "haul_ropes",
    } <= tables


def test_a_lift_and_its_children_go_in_together(engine):
    with Session(engine) as session:
        lift = lift_row()
        lift.terminals.append(terminal_row())
        lift.towers.append(tower_row())
        lift.carriers.append(carrier_row())
        lift.shots.append(
            SurveyShotRow(station=Length.from_feet(0), elevation=Length.from_feet(6400))
        )
        session.add(lift)
        session.commit()
    with Session(engine) as session:
        found = session.get(LiftRow, JP)
        assert found is not None
        assert len(found.terminals) == 1
        assert len(found.towers) == 1
        assert len(found.carriers) == 1
        assert len(found.shots) == 1


def test_the_measures_come_back_as_measures(engine):
    with Session(engine) as session:
        session.add(lift_row())
        session.commit()
    with Session(engine) as session:
        found = session.get(LiftRow, JP)
        assert found is not None
        assert found.design_speed == Speed.from_feet_per_minute(1000)
        assert found.bearing == Bearing.from_degrees(72)
        assert found.commissioned == date(2004, 12, 3)


def test_a_tower_without_a_lift_is_refused_by_the_database(engine):
    with Session(engine) as session:
        session.add(tower_row())
        with pytest.raises(IntegrityError):
            session.commit()


def test_a_lift_and_a_tower_added_together_insert_in_the_right_order(engine):
    with Session(engine) as session:
        lift = lift_row()
        tower = tower_row(lift=lift)
        session.add_all([tower, lift])
        session.commit()
    with Session(engine) as session:
        assert session.scalar(select(TowerRow).where(TowerRow.number == 1)) is not None


def test_two_towers_cannot_share_a_number_on_one_line(engine):
    with Session(engine) as session:
        lift = lift_row()
        lift.towers.append(tower_row(number=3))
        lift.towers.append(tower_row(number=3))
        session.add(lift)
        with pytest.raises(IntegrityError):
            session.commit()


def test_two_carriers_cannot_share_a_number_on_one_fleet(engine):
    with Session(engine) as session:
        lift = lift_row()
        lift.carriers.append(carrier_row(number=7))
        lift.carriers.append(carrier_row(number=7))
        session.add(lift)
        with pytest.raises(IntegrityError):
            session.commit()


def test_a_terminal_carries_one_brake_of_each_kind(engine):
    with Session(engine) as session:
        lift = lift_row()
        terminal = terminal_row(lift=lift)
        terminal.brakes.append(BrakeRow(kind=BrakeKind.SERVICE, torque=Torque.from_pound_feet(900)))
        terminal.brakes.append(BrakeRow(kind=BrakeKind.SERVICE, torque=Torque.from_pound_feet(700)))
        session.add(lift)
        with pytest.raises(IntegrityError):
            session.commit()


def test_a_rope_hangs_off_a_spec_and_may_hang_off_a_lift(engine):
    with Session(engine) as session:
        lift = lift_row()
        spec = spec_row()
        session.add(
            HaulRopeRow(
                serial=RopeSerial.parse("FAT-2019-04412"),
                spec=spec,
                lift=lift,
                installed=date(2019, 10, 4),
                length=Length.from_feet(10132),
                splices=1,
            )
        )
        session.commit()
    with Session(engine) as session:
        found = session.get(HaulRopeRow, RopeSerial.parse("FAT-2019-04412"))
        assert found is not None
        assert found.spec.construction is RopeConstruction.SIX_BY_TWENTY_FIVE
        assert found.lift is not None
        assert found.lift.code == JP


def test_a_spare_rope_sits_on_no_lift(engine):
    with Session(engine) as session:
        session.add(
            HaulRopeRow(
                serial=RopeSerial.parse("BRI-2021-00500"),
                spec=spec_row(),
                installed=date(2021, 9, 1),
                length=Length.from_feet(4113),
            )
        )
        session.commit()
    with Session(engine) as session:
        found = session.get(HaulRopeRow, RopeSerial.parse("BRI-2021-00500"))
        assert found is not None
        assert found.lift is None


def test_deleting_a_lift_takes_its_towers_with_it(engine):
    with Session(engine) as session:
        lift = lift_row()
        lift.towers.append(tower_row())
        session.add(lift)
        session.commit()
    with Session(engine) as session:
        lift = session.get(LiftRow, JP)
        assert lift is not None
        session.delete(lift)
        session.commit()
    with Session(engine) as session:
        assert session.scalars(select(TowerRow)).all() == []


def test_two_shots_cannot_share_a_station(engine):
    with Session(engine) as session:
        lift = lift_row()
        lift.shots.append(
            SurveyShotRow(station=Length.from_feet(600), elevation=Length.from_feet(6496))
        )
        lift.shots.append(
            SurveyShotRow(station=Length.from_feet(600), elevation=Length.from_feet(6500))
        )
        session.add(lift)
        with pytest.raises(IntegrityError):
            session.commit()


def test_enums_are_stored_by_name_rather_than_by_sentence(engine):
    with Session(engine) as session:
        session.add(lift_row())
        session.commit()
        stored = session.connection().exec_driver_sql("select lift_class from lifts").scalar_one()
    assert stored == "DETACHABLE_CHAIR"


def test_the_enum_helper_names_the_type_it_makes():
    made = named_enum(LiftClass, "lift_class")
    assert made.name == "lift_class"
    assert not made.native_enum
