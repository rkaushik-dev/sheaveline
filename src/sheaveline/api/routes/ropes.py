"""The rope file, read and written over http.

The write here is a mechanic recording a walk, which is the one thing this
surface is genuinely used for rather than consulted about. It takes a mechanic's
key, and the verdict comes back in the same reply, because standing next to the
rope is exactly when somebody wants to know whether it stays on.
"""

from __future__ import annotations

from fastapi import APIRouter, status

from sheaveline.api.deps import Mechanic, Reader, Work
from sheaveline.api.errors import NotFound
from sheaveline.api.schemas.ropework import (
    FindingOut,
    RopeFileOut,
    RopeOut,
    WearSurveyIn,
    WearSurveyOut,
)
from sheaveline.api.security import ApiKey
from sheaveline.persistence.base import StorageError
from sheaveline.persistence.uow import UnitOfWork
from sheaveline.plant.identity import RopeSerial
from sheaveline.plant.rope import HaulRope
from sheaveline.ropework.verdict import RopeFile, assess_wear

router = APIRouter(prefix="/ropes", tags=["ropes"])


def _rope(work: UnitOfWork, serial: str) -> HaulRope:
    wanted = RopeSerial.parse(serial)
    try:
        return work.ropes.get(wanted)
    except StorageError as missing:
        raise NotFound(f"there is no rope {wanted}") from missing


@router.get("", response_model=list[RopeOut])
def list_ropes(work: UnitOfWork = Work, key: ApiKey = Reader) -> list[RopeOut]:
    return [RopeOut.of(rope) for rope in work.ropes.all()]


@router.get("/spares", response_model=list[RopeOut])
def list_spares(work: UnitOfWork = Work, key: ApiKey = Reader) -> list[RopeOut]:
    return [RopeOut.of(rope) for rope in work.ropes.spares()]


@router.get("/{serial}", response_model=RopeOut)
def read_rope(serial: str, work: UnitOfWork = Work, key: ApiKey = Reader) -> RopeOut:
    return RopeOut.of(_rope(work, serial))


@router.get("/{serial}/surveys", response_model=list[WearSurveyOut])
def list_surveys(serial: str, work: UnitOfWork = Work, key: ApiKey = Reader) -> list[WearSurveyOut]:
    rope = _rope(work, serial)
    return [
        WearSurveyOut.of(survey, assess_wear(survey).value)
        for survey in work.wear_surveys.for_rope(rope)
    ]


@router.post(
    "/{serial}/surveys",
    response_model=WearSurveyOut,
    status_code=status.HTTP_201_CREATED,
)
def record_survey(
    serial: str,
    body: WearSurveyIn,
    work: UnitOfWork = Work,
    key: ApiKey = Mechanic,
) -> WearSurveyOut:
    rope = _rope(work, serial)
    survey = body.to_domain(rope)
    work.wear_surveys.add(survey)
    work.commit()
    return WearSurveyOut.of(survey, assess_wear(survey).value)


@router.get("/{serial}/file", response_model=RopeFileOut)
def read_file(serial: str, work: UnitOfWork = Work, key: ApiKey = Reader) -> RopeFileOut:
    rope = _rope(work, serial)
    surveys = work.wear_surveys.for_rope(rope)
    held = RopeFile(wear=surveys[-1] if surveys else None)
    return RopeFileOut(
        serial=rope.serial,
        verdict=held.verdict().value,
        may_run=held.may_run(),
        sources=sorted(held.sources()),
        findings=[FindingOut.of(one) for one in held.findings()],
    )
