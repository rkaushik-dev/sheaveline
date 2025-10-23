from decimal import Decimal

import pytest
from sqlalchemy import Column, Integer, MetaData, String, Table, select
from sqlalchemy.orm import Session

from sheaveline.measures.angle import Angle, Bearing
from sheaveline.measures.duration import Duration
from sheaveline.measures.length import Length
from sheaveline.measures.load import Load, LoadPerFoot
from sheaveline.measures.speed import Speed
from sheaveline.measures.temperature import Temperature
from sheaveline.measures.torque import Torque
from sheaveline.persistence.base import (
    NAMING_CONVENTION,
    Base,
    build_engine,
    build_sessions,
    create_schema,
    drop_schema,
    session_scope,
    table_names,
)
from sheaveline.persistence.types import (
    AngleColumn,
    BearingColumn,
    DecimalColumn,
    DurationColumn,
    FilingNumberColumn,
    LengthColumn,
    LiftCodeColumn,
    LoadColumn,
    LoadPerFootColumn,
    RopeSerialColumn,
    SpeedColumn,
    TemperatureColumn,
    TimeOfDayColumn,
    TorqueColumn,
    TowerRefColumn,
)
from sheaveline.plant.identity import FilingNumber, LiftCode, RopeSerial, TowerRef
from sheaveline.schedule.hours import TimeOfDay

SCRATCH = MetaData(naming_convention=NAMING_CONVENTION)

MEASURES = Table(
    "scratch_measures",
    SCRATCH,
    Column("id", Integer, primary_key=True),
    Column("length", LengthColumn),
    Column("load", LoadColumn),
    Column("per_foot", LoadPerFootColumn),
    Column("speed", SpeedColumn),
    Column("elapsed", DurationColumn),
    Column("angle", AngleColumn),
    Column("bearing", BearingColumn),
    Column("temperature", TemperatureColumn),
    Column("torque", TorqueColumn),
    Column("clock", TimeOfDayColumn),
    Column("ratio", DecimalColumn),
    Column("lift", LiftCodeColumn),
    Column("tower", TowerRefColumn),
    Column("rope", RopeSerialColumn),
    Column("filing", FilingNumberColumn),
    Column("note", String(20)),
)


@pytest.fixture
def engine():
    made = build_engine()
    SCRATCH.create_all(made)
    yield made
    SCRATCH.drop_all(made)
    made.dispose()


def round_trip(engine, **values):
    with Session(engine) as session:
        session.execute(MEASURES.insert().values(id=1, **values))
        session.commit()
        return session.execute(select(MEASURES).where(MEASURES.c.id == 1)).mappings().one()


def test_every_measure_comes_back_as_the_type_it_went_in_as(engine):
    row = round_trip(
        engine,
        length=Length.from_feet(4800),
        load=Load.from_pounds(34500),
        per_foot=LoadPerFoot.from_pounds_per_foot("3.12"),
        speed=Speed.from_feet_per_minute(1000),
        elapsed=Duration.from_minutes(8),
        angle=Angle.from_degrees("1.75"),
        bearing=Bearing.from_degrees(72),
        temperature=Temperature.from_degrees_f(-12),
        torque=Torque.from_pound_feet(900),
        clock=TimeOfDay.at(15, 45),
        ratio=Decimal("1.125"),
        lift=LiftCode("JP"),
        tower=TowerRef.parse("JP-T4"),
        rope=RopeSerial.parse("FAT-2019-04412"),
        filing=FilingNumber("UT", 2025, 147),
    )
    assert row["length"] == Length.from_feet(4800)
    assert row["load"] == Load.from_pounds(34500)
    assert row["per_foot"] == LoadPerFoot.from_pounds_per_foot("3.12")
    assert row["speed"] == Speed.from_feet_per_minute(1000)
    assert row["elapsed"] == Duration.from_minutes(8)
    assert row["angle"] == Angle.from_degrees("1.75")
    assert row["bearing"] == Bearing.from_degrees(72)
    assert row["temperature"] == Temperature.from_degrees_f(-12)
    assert row["torque"] == Torque.from_pound_feet(900)
    assert row["clock"] == TimeOfDay.at(15, 45)
    assert row["ratio"] == Decimal("1.125")
    assert row["lift"] == LiftCode("JP")
    assert row["tower"] == TowerRef.parse("JP-T4")
    assert row["rope"] == RopeSerial.parse("FAT-2019-04412")
    assert row["filing"] == FilingNumber("UT", 2025, 147)


def test_nothing_stored_comes_back_as_nothing(engine):
    row = round_trip(engine, note="empty")
    for column in MEASURES.columns:
        if column.name in {"id", "note"}:
            continue
        assert row[column.name] is None


def test_a_length_is_stored_as_the_integer_it_already_is(engine):
    with Session(engine) as session:
        session.execute(MEASURES.insert().values(id=2, length=Length.from_feet(1)))
        session.commit()
        raw = session.execute(select(MEASURES.c.length).where(MEASURES.c.id == 2)).scalar_one()
    assert raw == Length.from_mils(12000)


def test_lengths_compare_in_the_database_the_way_they_compare_in_python(engine):
    with Session(engine) as session:
        for index, feet in enumerate((100, 4800, 250), start=10):
            session.execute(MEASURES.insert().values(id=index, length=Length.from_feet(feet)))
        session.commit()
        found = (
            session.execute(
                select(MEASURES.c.length)
                .where(MEASURES.c.length > Length.from_feet(200))
                .order_by(MEASURES.c.length)
            )
            .scalars()
            .all()
        )
    assert found == [Length.from_feet(250), Length.from_feet(4800)]


def test_a_negative_measure_survives_the_trip(engine):
    row = round_trip(
        engine, temperature=Temperature.from_degrees_f(-24), angle=Angle.from_degrees("-1.2")
    )
    assert row["temperature"] == Temperature.from_degrees_f(-24)
    assert row["angle"] == Angle.from_degrees("-1.2")


def test_a_bearing_wraps_on_the_way_back_out(engine):
    row = round_trip(engine, bearing=Bearing.from_degrees(370))
    assert row["bearing"] == Bearing.from_degrees(10)


def test_the_naming_convention_is_on_the_base_metadata():
    assert Base.metadata.naming_convention == NAMING_CONVENTION
    assert Base.metadata.naming_convention["pk"] == "pk_%(table_name)s"


def test_the_schema_can_be_built_and_dropped():
    made = build_engine()
    create_schema(made)
    drop_schema(made)
    made.dispose()
    assert isinstance(table_names(), tuple)


def test_a_scope_commits_on_the_way_out(engine):
    sessions = build_sessions(engine)
    with session_scope(sessions) as session:
        session.execute(MEASURES.insert().values(id=3, note="kept"))
    with Session(engine) as session:
        assert (
            session.execute(select(MEASURES.c.note).where(MEASURES.c.id == 3)).scalar_one()
            == "kept"
        )


def test_a_scope_rolls_back_on_anything_raised(engine):
    sessions = build_sessions(engine)
    with pytest.raises(RuntimeError), session_scope(sessions) as session:
        session.execute(MEASURES.insert().values(id=4, note="dropped"))
        raise RuntimeError("something went wrong halfway")
    with Session(engine) as session:
        assert (
            session.execute(select(MEASURES.c.note).where(MEASURES.c.id == 4)).one_or_none() is None
        )


def test_sqlite_is_told_to_enforce_its_foreign_keys():
    made = build_engine()
    with made.connect() as connection:
        pragma = connection.exec_driver_sql("PRAGMA foreign_keys").scalar()
    made.dispose()
    assert pragma == 1


def test_a_decimal_keeps_three_places_and_rounds_the_rest(engine):
    row = round_trip(engine, ratio=Decimal("0.8567"))
    assert row["ratio"] == Decimal("0.857")
