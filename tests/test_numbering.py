import pytest
from sqlalchemy import inspect
from sqlalchemy.orm import Session

from sheaveline.persistence.base import StorageError, build_engine, create_schema
from sheaveline.persistence.numbering import (
    FilingCounterRow,
    adopt,
    issued_count,
    peek_number,
    series_years,
    take_number,
)
from sheaveline.plant.identity import FilingNumber


@pytest.fixture
def session():
    engine = build_engine()
    create_schema(engine)
    with Session(engine) as opened:
        yield opened
    engine.dispose()


def test_the_counter_table_is_on_the_schema():
    engine = build_engine()
    create_schema(engine)
    assert "filing_counters" in inspect(engine).get_table_names()
    engine.dispose()


def test_the_first_number_of_a_year_is_one(session):
    assert take_number(session, "UT", 2025) == FilingNumber("UT", 2025, 1)


def test_numbers_come_out_in_sequence_with_no_gaps(session):
    taken = [take_number(session, "UT", 2025) for _ in range(5)]
    assert [one.sequence for one in taken] == [1, 2, 3, 4, 5]


def test_two_states_keep_separate_sequences(session):
    assert take_number(session, "UT", 2025).sequence == 1
    assert take_number(session, "CO", 2025).sequence == 1
    assert take_number(session, "UT", 2025).sequence == 2


def test_two_years_keep_separate_sequences(session):
    assert take_number(session, "UT", 2025).sequence == 1
    assert take_number(session, "UT", 2026).sequence == 1
    assert take_number(session, "UT", 2025).sequence == 2


def test_the_state_code_is_normalised(session):
    assert take_number(session, "ut", 2025).state == "UT"
    assert take_number(session, "UT", 2025).sequence == 2


def test_something_that_is_not_a_state_code_is_refused(session):
    with pytest.raises(StorageError):
        take_number(session, "UTAH", 2025)
    with pytest.raises(StorageError):
        take_number(session, "U1", 2025)


def test_peeking_does_not_take_the_number(session):
    assert peek_number(session, "UT", 2025) == FilingNumber("UT", 2025, 1)
    assert peek_number(session, "UT", 2025) == FilingNumber("UT", 2025, 1)
    assert take_number(session, "UT", 2025) == FilingNumber("UT", 2025, 1)
    assert peek_number(session, "UT", 2025) == FilingNumber("UT", 2025, 2)


def test_the_issued_count_follows_the_counter(session):
    assert issued_count(session, "UT", 2025) == 0
    take_number(session, "UT", 2025)
    take_number(session, "UT", 2025)
    assert issued_count(session, "UT", 2025) == 2
    assert issued_count(session, "CO", 2025) == 0


def test_the_years_a_state_has_filed_in_come_back_in_order(session):
    take_number(session, "UT", 2026)
    take_number(session, "UT", 2024)
    take_number(session, "CO", 2025)
    assert series_years(session, "UT") == [2024, 2026]
    assert series_years(session, "CO") == [2025]


def test_a_number_loaded_from_paper_moves_the_counter_past_it(session):
    adopt(session, FilingNumber("UT", 2025, 40))
    assert take_number(session, "UT", 2025) == FilingNumber("UT", 2025, 41)


def test_adopting_an_older_number_never_moves_the_counter_backwards(session):
    take_number(session, "UT", 2025)
    take_number(session, "UT", 2025)
    adopt(session, FilingNumber("UT", 2025, 1))
    assert take_number(session, "UT", 2025) == FilingNumber("UT", 2025, 3)


def test_the_counter_survives_a_commit(session):
    take_number(session, "UT", 2025)
    session.commit()
    assert take_number(session, "UT", 2025).sequence == 2
    session.commit()
    row = session.get(FilingCounterRow, ("UT", 2025))
    assert row is not None
    assert row.next_sequence == 3


def test_a_rolled_back_number_is_handed_out_again(session):
    assert take_number(session, "UT", 2025).sequence == 1
    session.rollback()
    assert take_number(session, "UT", 2025).sequence == 1


def test_the_counter_does_not_count_the_rows_already_written(session):
    """Counting rows is the mistake this table exists to avoid."""
    take_number(session, "UT", 2025)
    take_number(session, "UT", 2025)
    session.commit()
    row = session.get(FilingCounterRow, ("UT", 2025))
    assert row is not None
    assert row.next_sequence == 3
    assert issued_count(session, "UT", 2025) == 2
