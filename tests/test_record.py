from datetime import date
from decimal import Decimal

import pytest

from hill import JOHN_PAUL, STRAWBERRY
from sheaveline.upkeep.interval import UpkeepError
from sheaveline.upkeep.record import (
    Defect,
    DefectLevel,
    InspectionRecord,
    RecordBook,
)

DAY = date(2025, 1, 15)


def defect(
    where="JP-T4",
    level=DefectLevel.DEFECT,
    detail="sheave liner cracked",
    raised=DAY,
    cleared=None,
):
    return Defect(
        where=where,
        level=level,
        detail=detail,
        raised=raised,
        cleared=cleared,
        cleared_by=None if cleared is None else "M Vetter",
    )


def record(code="W1", done=DAY, defects=(), hours=None, cycles=None, lift=JOHN_PAUL):
    return InspectionRecord.of(lift, code, done, "M Vetter", hours, cycles, defects)


def test_a_defect_says_where_it_is_and_what_is_wrong():
    with pytest.raises(UpkeepError):
        defect(where="  ")
    with pytest.raises(UpkeepError):
        defect(detail="")


def test_a_defect_cannot_be_cleared_before_it_was_raised():
    with pytest.raises(UpkeepError):
        defect(raised=DAY, cleared=date(2025, 1, 1))


def test_a_defect_is_cleared_by_somebody_on_some_day_or_neither():
    with pytest.raises(UpkeepError):
        Defect("JP-T4", DefectLevel.DEFECT, "cracked", DAY, cleared=date(2025, 2, 1))


def test_an_open_defect_ages_until_it_is_cleared():
    open_one = defect()
    assert open_one.is_open()
    assert open_one.age_in_days(date(2025, 2, 14)) == 30
    with pytest.raises(UpkeepError):
        open_one.age_in_days(date(2025, 1, 1))


def test_a_cleared_defect_stops_ageing():
    closed = defect(cleared=date(2025, 1, 25))
    assert not closed.is_open()
    assert closed.age_in_days(date(2025, 3, 1)) == 10


def test_clearing_a_defect_makes_a_new_record():
    open_one = defect()
    closed = open_one.clear(date(2025, 1, 25), "M Vetter")
    assert open_one.is_open()
    assert not closed.is_open()
    with pytest.raises(UpkeepError):
        closed.clear(date(2025, 2, 1), "M Vetter")
    with pytest.raises(UpkeepError):
        open_one.clear(date(2025, 1, 25), "  ")


def test_only_a_serious_defect_stops_the_machine():
    assert DefectLevel.SERIOUS.stops_the_machine
    assert not DefectLevel.DEFECT.stops_the_machine
    assert DefectLevel.DEFECT.needs_a_work_order
    assert not DefectLevel.OBSERVATION.needs_a_work_order


def test_a_defect_prints_its_state():
    assert str(defect()) == "JP-T4 defect: sheave liner cracked (open)"
    closed = defect(cleared=date(2025, 1, 25))
    assert str(closed).endswith("(cleared 2025-01-25)")


def test_a_record_names_the_job_it_was():
    walked = record(code="w1")
    assert walked.code == "W1"
    assert walked.job().description.startswith("walk the towers")


def test_a_record_is_signed():
    with pytest.raises(UpkeepError):
        InspectionRecord.of(JOHN_PAUL, "W1", DAY, "  ")


def test_a_job_that_runs_on_the_meter_needs_a_meter_reading():
    with pytest.raises(UpkeepError):
        record(code="H1")
    assert record(code="H1", hours=Decimal("42000.0")).hours == Decimal("42000.0")


def test_a_job_that_runs_on_the_counter_needs_a_counter_reading():
    with pytest.raises(UpkeepError):
        record(code="C1")
    assert record(code="C1", cycles=1_000_000).cycles == 1_000_000


def test_a_defect_cannot_predate_the_inspection_that_found_it():
    with pytest.raises(UpkeepError):
        record(defects=(defect(raised=date(2025, 1, 1)),))


def test_a_clean_record_says_so():
    assert record().was_clean()
    assert record(defects=(defect(level=DefectLevel.OBSERVATION),)).was_clean()
    assert not record(defects=(defect(),)).was_clean()


def test_the_serious_defects_are_picked_out():
    walked = record(
        defects=(
            defect(where="JP-T4"),
            defect(where="JP-T7", level=DefectLevel.SERIOUS, detail="crossarm bolt sheared"),
        )
    )
    assert len(walked.serious()) == 1
    assert len(walked.open_serious()) == 1
    assert len(walked.open_defects()) == 2


def test_a_serious_defect_already_cleared_no_longer_counts():
    walked = record(
        defects=(
            defect(
                where="JP-T7",
                level=DefectLevel.SERIOUS,
                detail="crossarm bolt sheared",
                cleared=date(2025, 1, 16),
            ),
        )
    )
    assert len(walked.serious()) == 1
    assert walked.open_serious() == ()


def test_a_record_prints_a_one_liner():
    assert str(record()) == "JP W1 on 2025-01-15, clean"
    assert str(record(defects=(defect(),))) == "JP W1 on 2025-01-15, 1 written up"


def test_a_book_belongs_to_one_machine():
    with pytest.raises(UpkeepError):
        RecordBook.of(JOHN_PAUL, [record(lift=STRAWBERRY)])


def test_a_book_sorts_by_day_and_finds_the_last_of_a_job():
    book = RecordBook.of(
        JOHN_PAUL,
        [
            record(code="W1", done=date(2025, 1, 8)),
            record(code="W1", done=date(2025, 1, 15)),
            record(code="M1", done=date(2025, 1, 10)),
        ],
    )
    assert len(book) == 3
    assert len(book.for_code("W1")) == 2
    last = book.last_done("W1")
    assert last is not None
    assert last.done == date(2025, 1, 15)
    assert book.last_done("A1") is None


def test_the_open_defects_across_the_whole_book():
    book = RecordBook.of(
        JOHN_PAUL,
        [
            record(code="W1", done=date(2025, 1, 8), defects=(defect(raised=date(2025, 1, 8)),)),
            record(
                code="W1",
                done=date(2025, 1, 15),
                defects=(
                    defect(
                        where="JP-T7",
                        level=DefectLevel.SERIOUS,
                        detail="bolt sheared",
                    ),
                ),
            ),
        ],
    )
    assert len(book.open_defects()) == 2
    assert len(book.open_serious()) == 1
    assert len(book.defects_at("JP-T4")) == 1
    assert book.defects_at("JP-T9") == ()


def test_adding_a_record_makes_a_new_book():
    book = RecordBook.of(JOHN_PAUL)
    longer = book.with_record(record())
    assert len(book) == 0
    assert len(longer) == 1
