from datetime import date

import pytest
from sqlalchemy import select
from sqlalchemy.orm import Session

from hill import john_paul
from sheaveline.measures.length import Length
from sheaveline.measures.load import Load
from sheaveline.measures.temperature import Temperature
from sheaveline.persistence.base import StorageError, build_engine, create_schema
from sheaveline.persistence.mappers.plant import lift_to_row, rope_to_row
from sheaveline.persistence.mappers.ropework import (
    breaks_from_row,
    breaks_to_row,
    magnetic_from_row,
    magnetic_to_row,
    reading_from_row,
    reading_to_row,
    splices_from_rows,
    splices_to_rows,
    tension_from_row,
    tension_to_row,
    wear_from_row,
    wear_to_row,
)
from sheaveline.persistence.models.ropework import (
    BreakSurveyRow,
    MagneticTestRow,
    SpliceRow,
    TensionReadingRow,
    TensionSystemRow,
    WearSurveyRow,
)
from sheaveline.plant.identity import RopeSerial
from sheaveline.plant.rope import HaulRope
from sheaveline.ropework.breaks import BreakObservation, BreakSurvey
from sheaveline.ropework.mrt import Indication, MagneticTest
from sheaveline.ropework.splice import Splice, SpliceRegister
from sheaveline.ropework.tension import TensionKind, TensionReading, TensionSystem
from sheaveline.ropework.verdict import assess_breaks, assess_wear
from sheaveline.ropework.wear import DiameterReading, LayReading, WearSurvey

LIFT = john_paul()
ROPE = LIFT.rope
TAKEN = date(2025, 1, 15)


@pytest.fixture
def engine():
    made = build_engine()
    create_schema(made)
    with Session(made) as session:
        session.add(rope_to_row(ROPE, LIFT.code))
        session.add(lift_to_row(LIFT))
        session.commit()
    yield made
    made.dispose()


def other_rope():
    return HaulRope(
        serial=RopeSerial.parse("BRI-2011-01887"),
        spec=ROPE.spec,
        installed=ROPE.installed,
        length=ROPE.length,
        splices=1,
    )


def wear_survey():
    return WearSurvey.of(
        ROPE,
        TAKEN,
        "R Halvorsen",
        [
            DiameterReading(
                Length.from_feet(0),
                Length.from_inches("1.376"),
                Length.from_inches("1.374"),
            ),
            DiameterReading(
                Length.from_feet(1200),
                Length.from_inches("1.352"),
                Length.from_inches("1.348"),
            ),
        ],
        [LayReading(Length.from_feet(1200), Length.from_inches("9.10"))],
    )


def test_a_wear_survey_round_trips_through_the_database(engine):
    before = wear_survey()
    with Session(engine) as session:
        session.add(wear_to_row(before))
        session.commit()
    with Session(engine) as session:
        row = session.scalars(select(WearSurveyRow)).one()
        after = wear_from_row(row, ROPE)
    assert after.taken == before.taken
    assert after.inspector == before.inspector
    assert len(after.diameters) == 2
    assert len(after.lays) == 1
    assert after.smallest().mean() == before.smallest().mean()
    assert assess_wear(after) is assess_wear(before)


def test_a_wear_survey_will_not_load_against_the_wrong_rope(engine):
    with Session(engine) as session:
        session.add(wear_to_row(wear_survey()))
        session.commit()
    with Session(engine) as session:
        row = session.scalars(select(WearSurveyRow)).one()
        with pytest.raises(StorageError):
            wear_from_row(row, other_rope())


def test_a_break_survey_round_trips(engine):
    before = BreakSurvey.of(
        ROPE,
        TAKEN,
        "R Halvorsen",
        [
            BreakObservation(Length.from_feet(1200), 4, 7, valley_breaks=1),
            BreakObservation(Length.from_feet(2400), 2, 3),
        ],
    )
    with Session(engine) as session:
        session.add(breaks_to_row(before))
        session.commit()
    with Session(engine) as session:
        row = session.scalars(select(BreakSurveyRow)).one()
        after = breaks_from_row(row, ROPE)
    assert after.total_breaks() == before.total_breaks()
    assert assess_breaks(after) is assess_breaks(before)
    assert after.observations[0].valley_breaks == 1


def test_a_magnetic_test_round_trips(engine):
    before = MagneticTest.of(
        ROPE,
        TAKEN,
        "Wire Rope Diagnostics",
        "LMA-250",
        [
            Indication.loss(Length.from_feet(1800), "7.4"),
            Indication.fault(Length.from_feet(2400), 3),
        ],
    )
    with Session(engine) as session:
        session.add(magnetic_to_row(before))
        session.commit()
    with Session(engine) as session:
        row = session.scalars(select(MagneticTestRow)).one()
        after = magnetic_from_row(row, ROPE)
    assert after.contractor == before.contractor
    assert after.instrument == before.instrument
    assert after.peak_loss() == before.peak_loss()
    assert after.largest_fault() == before.largest_fault()


def test_a_magnetic_test_will_not_load_against_the_wrong_rope(engine):
    with Session(engine) as session:
        session.add(
            magnetic_to_row(MagneticTest.of(ROPE, TAKEN, "Wire Rope Diagnostics", "LMA-250"))
        )
        session.commit()
    with Session(engine) as session:
        row = session.scalars(select(MagneticTestRow)).one()
        with pytest.raises(StorageError):
            magnetic_from_row(row, other_rope())


def test_a_splice_register_round_trips(engine):
    long_splice = ROPE.diameter.scaled(1200)
    before = SpliceRegister.of(
        ROPE,
        [
            Splice(Length.from_feet(0), date(2019, 10, 4), "B Kettner", 5, long_splice, True),
            Splice(Length.from_feet(4000), date(2022, 6, 1), "B Kettner", 5, long_splice),
        ],
    )
    with Session(engine) as session:
        session.add_all(splices_to_rows(before))
        session.commit()
    with Session(engine) as session:
        rows = list(session.scalars(select(SpliceRow)))
        after = splices_from_rows(rows, ROPE)
    assert after.count() == 2
    assert len(after.field_splices()) == 1
    assert after.is_acceptable() == before.is_acceptable()


def test_a_splice_register_will_not_load_against_the_wrong_rope(engine):
    long_splice = ROPE.diameter.scaled(1200)
    before = SpliceRegister.of(
        ROPE,
        [Splice(Length.from_feet(0), date(2019, 10, 4), "B Kettner", 5, long_splice, True)],
    )
    with Session(engine) as session:
        session.add_all(splices_to_rows(before))
        session.commit()
    with Session(engine) as session:
        rows = list(session.scalars(select(SpliceRow)))
        with pytest.raises(StorageError):
            splices_from_rows(rows, other_rope())


def test_a_tension_system_and_its_readings_round_trip(engine):
    system = TensionSystem(
        kind=TensionKind.HYDRAULIC,
        nominal=Load.from_pounds(34500),
        travel=Length.from_feet(16),
        carriage=Length.from_feet(6),
    )
    reading = TensionReading(
        taken=TAKEN,
        carriage=Length.from_feet(7),
        measured=Load.from_pounds(34800),
        temperature=Temperature.from_degrees_f(14),
    )
    with Session(engine) as session:
        session.add(tension_to_row(system, LIFT.code))
        session.add(reading_to_row(reading, LIFT.code))
        session.commit()
    with Session(engine) as session:
        system_row = session.get(TensionSystemRow, LIFT.code)
        assert system_row is not None
        assert tension_from_row(system_row) == system
        reading_row = session.scalars(select(TensionReadingRow)).one()
        assert reading_from_row(reading_row) == reading


def test_the_readings_hang_off_the_system_they_belong_to(engine):
    system = TensionSystem(
        kind=TensionKind.COUNTERWEIGHT,
        nominal=Load.from_pounds(34500),
        travel=Length.from_feet(16),
        carriage=Length.from_feet(6),
    )
    with Session(engine) as session:
        session.add(tension_to_row(system, LIFT.code))
        session.add(
            reading_to_row(
                TensionReading(
                    taken=TAKEN,
                    carriage=Length.from_feet(7),
                    measured=Load.from_pounds(34400),
                    temperature=Temperature.from_degrees_f(14),
                ),
                LIFT.code,
            )
        )
        session.commit()
    with Session(engine) as session:
        found = session.get(TensionSystemRow, LIFT.code)
        assert found is not None
        assert len(found.readings) == 1
        assert tension_from_row(found).kind is TensionKind.COUNTERWEIGHT
