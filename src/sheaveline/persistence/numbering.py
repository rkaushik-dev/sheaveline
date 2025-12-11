"""Board filing numbers, handed out one at a time with no gaps.

A gap in the sequence is a question at the audit, so the number cannot come from
a count of the rows already there: two clerks filing at the same moment both
count seven and both write eight. It comes off a counter row that is read for
update and written in the same transaction, which is the only arrangement where
the second clerk waits.

On sqlite the row lock is a no thing, because the whole file is locked for the
write anyway. On the office server it is real, and the unique constraint on the
filing number is the belt to that pair of braces.
"""

from __future__ import annotations

from sqlalchemy import Integer, String, select
from sqlalchemy.orm import Mapped, Session, mapped_column

from sheaveline.persistence.base import Base, StorageError
from sheaveline.plant.identity import FilingNumber

FIRST_SEQUENCE = 1


class FilingCounterRow(Base):
    """One row a state and a year, holding the next number to hand out."""

    __tablename__ = "filing_counters"

    state: Mapped[str] = mapped_column(String(2), primary_key=True)
    year: Mapped[int] = mapped_column(Integer, primary_key=True)
    next_sequence: Mapped[int] = mapped_column(Integer, default=FIRST_SEQUENCE)


def _counter(session: Session, state: str, year: int, lock: bool) -> FilingCounterRow:
    statement = select(FilingCounterRow).where(
        FilingCounterRow.state == state.upper(),
        FilingCounterRow.year == year,
    )
    if lock:
        statement = statement.with_for_update()
    row = session.scalar(statement)
    if row is None:
        row = FilingCounterRow(state=state.upper(), year=year, next_sequence=FIRST_SEQUENCE)
        session.add(row)
        session.flush()
    return row


def take_number(session: Session, state: str, year: int) -> FilingNumber:
    """Hand out the next number and move the counter on, inside this transaction."""
    if len(state) != 2 or not state.isalpha():
        raise StorageError(f"{state} is not a state code")
    row = _counter(session, state, year, lock=True)
    number = FilingNumber(row.state, row.year, row.next_sequence)
    row.next_sequence += 1
    session.flush()
    return number


def peek_number(session: Session, state: str, year: int) -> FilingNumber:
    """What the next number would be, without taking it."""
    row = _counter(session, state, year, lock=False)
    return FilingNumber(row.state, row.year, row.next_sequence)


def issued_count(session: Session, state: str, year: int) -> int:
    row = session.get(FilingCounterRow, (state.upper(), year))
    return 0 if row is None else row.next_sequence - FIRST_SEQUENCE


def series_years(session: Session, state: str) -> list[int]:
    rows = session.scalars(
        select(FilingCounterRow.year)
        .where(FilingCounterRow.state == state.upper())
        .order_by(FilingCounterRow.year)
    )
    return list(rows)


def adopt(session: Session, number: FilingNumber) -> None:
    """Move the counter past a number that came from somewhere else.

    Used when a series is loaded from paper. It never moves the counter
    backwards, because that would hand out a number twice.
    """
    row = _counter(session, number.state, number.year, lock=True)
    if number.sequence >= row.next_sequence:
        row.next_sequence = number.sequence + 1
        session.flush()
