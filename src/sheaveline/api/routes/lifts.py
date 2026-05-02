"""The machines, read over http.

Everything here is a read except the withdrawal, and that is a manager's key
because taking a lift off the register is not something a terminal tablet does
by accident.
"""

from __future__ import annotations

from fastapi import APIRouter, Response, status

from sheaveline.api.deps import Manager, Reader, Work
from sheaveline.api.lookups import Look, Lookup
from sheaveline.api.schemas.plant import (
    LiftOut,
    LiftSummaryOut,
    SpanOut,
    TerminalOut,
    TowerOut,
)
from sheaveline.api.security import ApiKey
from sheaveline.persistence.uow import UnitOfWork

router = APIRouter(prefix="/lifts", tags=["lifts"])


@router.get("", response_model=list[LiftSummaryOut])
def list_lifts(work: UnitOfWork = Work, key: ApiKey = Reader) -> list[LiftSummaryOut]:
    return [LiftOut.summary_of(lift) for lift in work.lifts.all()]


@router.get("/{code}", response_model=LiftOut)
def read_lift(code: str, look: Lookup = Look, key: ApiKey = Reader) -> LiftOut:
    return LiftOut.of(look.lift(code))


@router.get("/{code}/towers", response_model=list[TowerOut])
def read_towers(code: str, look: Lookup = Look, key: ApiKey = Reader) -> list[TowerOut]:
    return [TowerOut.of(tower) for tower in look.lift(code).towers]


@router.get("/{code}/terminals", response_model=list[TerminalOut])
def read_terminals(code: str, look: Lookup = Look, key: ApiKey = Reader) -> list[TerminalOut]:
    lift = look.lift(code)
    return [TerminalOut.of(lift.bottom), TerminalOut.of(lift.top)]


@router.get("/{code}/spans", response_model=list[SpanOut])
def read_spans(code: str, look: Lookup = Look, key: ApiKey = Reader) -> list[SpanOut]:
    return [SpanOut.of(span) for span in look.lift(code).spans()]


@router.delete("/{code}", status_code=status.HTTP_204_NO_CONTENT, response_model=None)
def withdraw_lift(
    code: str, work: UnitOfWork = Work, look: Lookup = Look, key: ApiKey = Manager
) -> Response:
    lift = look.lift(code)
    work.lifts.remove(lift.code)
    work.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)
