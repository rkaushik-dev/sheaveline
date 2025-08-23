from datetime import date
from decimal import Decimal

import pytest

from hill import JOHN_PAUL, STRAWBERRY
from sheaveline.line.geometry import GeometryVerdict
from sheaveline.plant.classes import LiftClass
from sheaveline.ropework.wear import WearVerdict
from sheaveline.upkeep.record import (
    Defect,
    DefectLevel,
    InspectionRecord,
    RecordBook,
)
from sheaveline.upkeep.schedule import UpkeepSchedule
from sheaveline.upkeep.status import ServiceAssessment, ServiceStatus, StatusReason

BUILT = date(2024, 11, 1)
ON = date(2025, 1, 15)


def record(code, done, defects=(), hours=None, cycles=None):
    return InspectionRecord.of(JOHN_PAUL, code, done, "M Vetter", hours, cycles, defects)


def current_ladder(*extra):
    jobs = UpkeepSchedule(
        JOHN_PAUL, LiftClass.DETACHABLE_CHAIR, BUILT, RecordBook.of(JOHN_PAUL)
    ).jobs()
    records = [
        record(job.code, date(2025, 1, 14), hours=Decimal("42000.0"), cycles=1_000_000)
        for job in jobs
    ]
    return UpkeepSchedule(
        JOHN_PAUL,
        LiftClass.DETACHABLE_CHAIR,
        BUILT,
        RecordBook.of(JOHN_PAUL, [*records, *extra]),
    )


def assessment(schedule=None, rope=None, geometry=None):
    return ServiceAssessment(JOHN_PAUL, ON, schedule or current_ladder(), rope, geometry)


def test_a_machine_with_nothing_against_it_is_in_service():
    read = assessment()
    assert read.status() is ServiceStatus.IN_SERVICE
    assert read.reasons() == ()
    assert read.may_open()
    assert str(read) == "JP in service"


def test_a_ladder_from_another_machine_is_refused():
    other = UpkeepSchedule(STRAWBERRY, LiftClass.FIXED_CHAIR, BUILT, RecordBook.of(STRAWBERRY))
    with pytest.raises(ValueError):
        ServiceAssessment(JOHN_PAUL, ON, other)


def test_an_overdue_critical_job_takes_the_machine_out():
    thin = UpkeepSchedule(JOHN_PAUL, LiftClass.DETACHABLE_CHAIR, BUILT, RecordBook.of(JOHN_PAUL))
    read = assessment(schedule=thin)
    assert read.status() is ServiceStatus.OUT_OF_SERVICE
    assert not read.may_open()
    assert "ladder" in read.sources_reporting()


def test_an_overdue_job_that_is_not_critical_only_restricts():
    ladder = current_ladder()
    without_paint = RecordBook.of(
        JOHN_PAUL,
        [rec for rec in ladder.book.records if rec.code != "A5"],
    )
    read = assessment(
        schedule=UpkeepSchedule(
            JOHN_PAUL, LiftClass.DETACHABLE_CHAIR, date(2022, 1, 1), without_paint
        )
    )
    codes = {reason.detail.split()[0] for reason in read.reasons()}
    assert "A5" in codes


def test_an_open_serious_defect_takes_the_machine_out():
    serious = Defect("JP-T7", DefectLevel.SERIOUS, "crossarm bolt sheared", date(2025, 1, 14))
    read = assessment(schedule=current_ladder(record("W1", date(2025, 1, 14), defects=(serious,))))
    assert read.status() is ServiceStatus.OUT_OF_SERVICE
    assert "record book" in read.sources_reporting()


def test_a_serious_defect_already_cleared_does_not_count():
    cleared = Defect(
        "JP-T7",
        DefectLevel.SERIOUS,
        "crossarm bolt sheared",
        date(2025, 1, 14),
        cleared=date(2025, 1, 15),
        cleared_by="M Vetter",
    )
    read = assessment(schedule=current_ladder(record("W1", date(2025, 1, 14), defects=(cleared,))))
    assert read.status() is ServiceStatus.IN_SERVICE


def test_an_ordinary_defect_does_not_take_the_machine_out():
    ordinary = Defect("JP-T4", DefectLevel.DEFECT, "liner cracked", date(2025, 1, 14))
    read = assessment(schedule=current_ladder(record("W1", date(2025, 1, 14), defects=(ordinary,))))
    assert read.status() is ServiceStatus.IN_SERVICE


def test_a_condemned_rope_takes_the_machine_out():
    read = assessment(rope=WearVerdict.CONDEMNED)
    assert read.status() is ServiceStatus.OUT_OF_SERVICE
    assert "rope file" in read.sources_reporting()


def test_a_rope_on_watch_only_restricts():
    read = assessment(rope=WearVerdict.MONITOR)
    assert read.status() is ServiceStatus.RESTRICTED
    assert read.may_open()
    assert read.status().needs_a_note_on_the_sheet


def test_a_serviceable_rope_says_nothing():
    assert assessment(rope=WearVerdict.SERVICEABLE).reasons() == ()


def test_an_unfit_line_takes_the_machine_out():
    read = assessment(geometry=GeometryVerdict.UNFIT)
    assert read.status() is ServiceStatus.OUT_OF_SERVICE
    assert "line walk" in read.sources_reporting()


def test_a_restricted_line_only_restricts():
    read = assessment(geometry=GeometryVerdict.RESTRICTED)
    assert read.status() is ServiceStatus.RESTRICTED


def test_a_fit_line_says_nothing():
    assert assessment(geometry=GeometryVerdict.FIT).reasons() == ()


def test_the_worst_of_the_four_wins():
    read = assessment(rope=WearVerdict.MONITOR, geometry=GeometryVerdict.UNFIT)
    assert read.status() is ServiceStatus.OUT_OF_SERVICE
    assert len(read.reasons()) == 2
    assert len(read.blocking()) == 1


def test_two_sources_can_both_restrict_without_taking_it_out():
    read = assessment(rope=WearVerdict.MONITOR, geometry=GeometryVerdict.RESTRICTED)
    assert read.status() is ServiceStatus.RESTRICTED
    assert read.sources_reporting() == frozenset({"rope file", "line walk"})


def test_the_reasons_carry_the_source_that_raised_them():
    read = assessment(rope=WearVerdict.CONDEMNED)
    reason = read.reasons()[0]
    assert isinstance(reason, StatusReason)
    assert str(reason) == "rope file: rope condemned"


def test_the_status_prints_with_a_count_of_reasons():
    read = assessment(rope=WearVerdict.MONITOR, geometry=GeometryVerdict.RESTRICTED)
    assert str(read) == "JP restricted, 2 reasons"


def test_only_the_out_of_service_status_stops_the_public():
    assert ServiceStatus.IN_SERVICE.may_carry_public
    assert ServiceStatus.RESTRICTED.may_carry_public
    assert not ServiceStatus.OUT_OF_SERVICE.may_carry_public
    assert not ServiceStatus.IN_SERVICE.needs_a_note_on_the_sheet
