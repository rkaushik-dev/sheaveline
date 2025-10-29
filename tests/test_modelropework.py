from datetime import date, datetime
from decimal import Decimal

import pytest
from sqlalchemy import inspect, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from sheaveline.measures.angle import Bearing
from sheaveline.measures.length import Length
from sheaveline.measures.load import Load, LoadPerFoot
from sheaveline.measures.speed import Speed
from sheaveline.measures.temperature import Temperature
from sheaveline.persistence.base import build_engine, create_schema
from sheaveline.persistence.models.plant import HaulRopeRow, LiftRow, RopeSpecRow
from sheaveline.persistence.models.ropework import (
    BreakObservationRow,
    BreakSurveyRow,
    DiameterReadingRow,
    IndicationRow,
    LayReadingRow,
    MagneticTestRow,
    SpliceRow,
    TensionReadingRow,
    TensionSystemRow,
    WearSurveyRow,
)
from sheaveline.plant.classes import LiftClass
from sheaveline.plant.identity import LiftCode, RopeSerial
from sheaveline.plant.rope import LayDirection, RopeConstruction, RopeGrade
from sheaveline.ropework.mrt import IndicationKind
from sheaveline.ropework.tension import TensionKind

JP = LiftCode("JP")
SERIAL = RopeSerial.parse("FAT-2019-04412")


@pytest.fixture
def engine():
    made = build_engine()
    create_schema(made)
    with Session(made) as session:
        session.add(
            LiftRow(
                code=JP,
                name="John Paul",
                lift_class=LiftClass.DETACHABLE_CHAIR,
                bearing=Bearing.from_degrees(72),
                design_speed=Speed.from_feet_per_minute(1000),
                commissioned=date(2004, 12, 3),
            )
        )
        session.add(
            HaulRopeRow(
                serial=SERIAL,
                spec=RopeSpecRow(
                    construction=RopeConstruction.SIX_BY_TWENTY_FIVE,
                    grade=RopeGrade.EIP,
                    lay=LayDirection.RIGHT_REGULAR,
                    nominal_diameter=Length.from_inches("1.378"),
                    breaking_strength=Load.from_pounds(196000),
                    weight=LoadPerFoot.from_pounds_per_foot("3.12"),
                ),
                lift_code=JP,
                installed=date(2019, 10, 4),
                length=Length.from_feet(10132),
            )
        )
        session.commit()
    yield made
    made.dispose()


def test_the_schema_carries_every_rope_table(engine):
    tables = set(inspect(engine).get_table_names())
    assert {
        "wear_surveys",
        "diameter_readings",
        "lay_readings",
        "break_surveys",
        "break_observations",
        "magnetic_tests",
        "indications",
        "splices",
        "tension_systems",
        "tension_readings",
    } <= tables


def test_a_wear_survey_takes_its_readings_with_it(engine):
    with Session(engine) as session:
        survey = WearSurveyRow(rope_serial=SERIAL, taken=date(2025, 1, 15), inspector="R Halvorsen")
        survey.diameters.append(
            DiameterReadingRow(
                station=Length.from_feet(1200),
                across_crowns=Length.from_inches("1.352"),
                across_valleys=Length.from_inches("1.348"),
            )
        )
        survey.lays.append(
            LayReadingRow(station=Length.from_feet(1200), measured=Length.from_inches("9.20"))
        )
        session.add(survey)
        session.commit()
    with Session(engine) as session:
        found = session.scalars(select(WearSurveyRow)).one()
        assert len(found.diameters) == 1
        assert len(found.lays) == 1
        assert found.diameters[0].across_crowns == Length.from_inches("1.352")
        assert found.rope.serial == SERIAL


def test_one_wear_survey_a_rope_a_day(engine):
    with Session(engine) as session:
        for _ in range(2):
            session.add(
                WearSurveyRow(rope_serial=SERIAL, taken=date(2025, 1, 15), inspector="R Halvorsen")
            )
        with pytest.raises(IntegrityError):
            session.commit()


def test_a_survey_of_a_rope_that_is_not_there_is_refused(engine):
    with Session(engine) as session:
        session.add(
            WearSurveyRow(
                rope_serial=RopeSerial.parse("BRI-2011-01887"),
                taken=date(2025, 1, 15),
                inspector="R Halvorsen",
            )
        )
        with pytest.raises(IntegrityError):
            session.commit()


def test_a_break_count_cannot_hold_more_in_one_lay_than_in_six(engine):
    with Session(engine) as session:
        survey = BreakSurveyRow(
            rope_serial=SERIAL, taken=date(2025, 1, 15), inspector="R Halvorsen"
        )
        survey.observations.append(
            BreakObservationRow(station=Length.from_feet(0), in_one_lay=7, in_six_lays=3)
        )
        session.add(survey)
        with pytest.raises(IntegrityError):
            session.commit()


def test_valley_breaks_are_counted_inside_the_one_lay_total(engine):
    with Session(engine) as session:
        survey = BreakSurveyRow(
            rope_serial=SERIAL, taken=date(2025, 1, 15), inspector="R Halvorsen"
        )
        survey.observations.append(
            BreakObservationRow(
                station=Length.from_feet(0), in_one_lay=2, in_six_lays=4, valley_breaks=3
            )
        )
        session.add(survey)
        with pytest.raises(IntegrityError):
            session.commit()


def test_a_magnetic_test_takes_its_indications_with_it(engine):
    with Session(engine) as session:
        test = MagneticTestRow(
            rope_serial=SERIAL,
            taken=date(2025, 1, 15),
            contractor="Wire Rope Diagnostics",
            instrument="LMA-250",
        )
        test.indications.append(
            IndicationRow(
                station=Length.from_feet(1800),
                kind=IndicationKind.LOSS_OF_AREA,
                magnitude=Decimal("7.4"),
            )
        )
        session.add(test)
        session.commit()
    with Session(engine) as session:
        found = session.scalars(select(MagneticTestRow)).one()
        assert found.indications[0].magnitude == Decimal("7.4")
        assert found.indications[0].kind is IndicationKind.LOSS_OF_AREA


def test_two_splices_cannot_share_a_station_on_one_rope(engine):
    with Session(engine) as session:
        for _ in range(2):
            session.add(
                SpliceRow(
                    rope_serial=SERIAL,
                    station=Length.from_feet(0),
                    made=date(2019, 10, 4),
                    made_by="B Kettner",
                    tucks=5,
                    length=Length.from_feet(138),
                    mill=True,
                )
            )
        with pytest.raises(IntegrityError):
            session.commit()


def test_a_splice_with_too_few_tucks_is_refused(engine):
    with Session(engine) as session:
        session.add(
            SpliceRow(
                rope_serial=SERIAL,
                station=Length.from_feet(0),
                made=date(2019, 10, 4),
                made_by="B Kettner",
                tucks=2,
                length=Length.from_feet(138),
            )
        )
        with pytest.raises(IntegrityError):
            session.commit()


def test_a_tension_system_belongs_to_the_lift_and_keeps_its_readings(engine):
    with Session(engine) as session:
        system = TensionSystemRow(
            lift_code=JP,
            kind=TensionKind.HYDRAULIC,
            nominal=Load.from_pounds(34500),
            travel=Length.from_feet(16),
            carriage=Length.from_feet(6),
        )
        system.readings.append(
            TensionReadingRow(
                taken=datetime(2025, 1, 15, 7, 30),
                carriage=Length.from_feet(6),
                measured=Load.from_pounds(34800),
                temperature=Temperature.from_degrees_f(14),
            )
        )
        session.add(system)
        session.commit()
    with Session(engine) as session:
        found = session.get(TensionSystemRow, JP)
        assert found is not None
        assert found.kind is TensionKind.HYDRAULIC
        assert len(found.readings) == 1
        assert found.readings[0].measured == Load.from_pounds(34800)
        assert found.lift.name == "John Paul"


def test_a_tension_system_with_no_travel_is_refused(engine):
    with Session(engine) as session:
        session.add(
            TensionSystemRow(
                lift_code=JP,
                kind=TensionKind.HYDRAULIC,
                nominal=Load.from_pounds(34500),
                travel=Length.from_feet(0),
                carriage=Length.from_feet(0),
            )
        )
        with pytest.raises(IntegrityError):
            session.commit()


def test_deleting_a_survey_takes_its_readings_with_it(engine):
    with Session(engine) as session:
        survey = WearSurveyRow(rope_serial=SERIAL, taken=date(2025, 1, 15), inspector="R Halvorsen")
        survey.diameters.append(
            DiameterReadingRow(
                station=Length.from_feet(0),
                across_crowns=Length.from_inches("1.376"),
                across_valleys=Length.from_inches("1.376"),
            )
        )
        session.add(survey)
        session.commit()
        session.delete(survey)
        session.commit()
    with Session(engine) as session:
        assert session.scalars(select(DiameterReadingRow)).all() == []


def test_the_rope_file_follows_the_rope_and_not_the_machine(engine):
    with Session(engine) as session:
        session.add(
            WearSurveyRow(rope_serial=SERIAL, taken=date(2025, 1, 15), inspector="R Halvorsen")
        )
        session.commit()
        rope = session.get(HaulRopeRow, SERIAL)
        assert rope is not None
        rope.lift_code = None
        session.commit()
    with Session(engine) as session:
        found = session.scalars(select(WearSurveyRow)).one()
        assert found.rope.lift is None
        assert found.rope_serial == SERIAL
