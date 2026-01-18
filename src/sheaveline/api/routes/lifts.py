"""The machines, read over http.

Everything here is a read except the withdrawal, and that is a manager's key
because taking a lift off the register is not something a terminal tablet does
by accident.
"""

from __future__ import annotations

from fastapi import APIRouter, Response, status

from sheaveline.api.deps import Manager, Reader, Work
from sheaveline.api.errors import NotFound
from sheaveline.api.schemas.plant import (
    LiftOut,
    LiftSummaryOut,
    SpanOut,
    TerminalOut,
    TowerOut,
)
from sheaveline.api.security import ApiKey
from sheaveline.persistence.base import StorageError
from sheaveline.persistence.uow import UnitOfWork
from sheaveline.plant.identity import LiftCode
from sheaveline.plant.lift import Lift

router = APIRouter(prefix="/lifts", tags=["lifts"])


def _lift(work: UnitOfWork, code: str) -> Lift:
    wanted = LiftCode.parse(code)
    try:
        return work.lifts.get(wanted)
    except StorageError as missing:
        raise NotFound(f"there is no lift {wanted}") from missing


@router.get("", response_model=list[LiftSummaryOut])
def list_lifts(work: UnitOfWork = Work, key: ApiKey = Reader) -> list[LiftSummaryOut]:
    return [LiftOut.summary_of(lift) for lift in work.lifts.all()]


@router.get("/{code}", response_model=LiftOut)
def read_lift(code: str, work: UnitOfWork = Work, key: ApiKey = Reader) -> LiftOut:
    return LiftOut.of(_lift(work, code))


@router.get("/{code}/towers", response_model=list[TowerOut])
def read_towers(code: str, work: UnitOfWork = Work, key: ApiKey = Reader) -> list[TowerOut]:
    return [TowerOut.of(tower) for tower in _lift(work, code).towers]


@router.get("/{code}/terminals", response_model=list[TerminalOut])
def read_terminals(code: str, work: UnitOfWork = Work, key: ApiKey = Reader) -> list[TerminalOut]:
    lift = _lift(work, code)
    return [TerminalOut.of(lift.bottom), TerminalOut.of(lift.top)]


@router.get("/{code}/spans", response_model=list[SpanOut])
def read_spans(code: str, work: UnitOfWork = Work, key: ApiKey = Reader) -> list[SpanOut]:
    return [SpanOut.of(span) for span in _lift(work, code).spans()]


@router.delete("/{code}", status_code=status.HTTP_204_NO_CONTENT, response_model=None)
def withdraw_lift(code: str, work: UnitOfWork = Work, key: ApiKey = Manager) -> Response:
    lift = _lift(work, code)
    work.lifts.remove(lift.code)
    work.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)
