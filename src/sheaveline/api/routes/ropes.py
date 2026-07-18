"""The rope file, read and written over http.

The write here is a mechanic recording a walk, which is the one thing this
surface is genuinely used for rather than consulted about. It takes a mechanic's
key, and the verdict comes back in the same reply, because standing next to the
rope is exactly when somebody wants to know whether it stays on.
"""

from __future__ import annotations

from fastapi import APIRouter, status

from sheaveline.api.deps import Mechanic, Reader, Work
from sheaveline.api.lookups import Look, Lookup
from sheaveline.api.paging import Page, Paged, Paging
from sheaveline.api.schemas.ropework import (
    FindingOut,
    RopeFileOut,
    RopeOut,
    WearSurveyIn,
    WearSurveyOut,
)
from sheaveline.api.security import ApiKey
from sheaveline.persistence.uow import UnitOfWork
from sheaveline.ropework.verdict import RopeFile, assess_wear

router = APIRouter(prefix="/ropes", tags=["ropes"])


@router.get("", response_model=list[RopeOut])
def list_ropes(work: UnitOfWork = Work, key: ApiKey = Reader) -> list[RopeOut]:
    return [RopeOut.of(rope) for rope in work.ropes.all()]


@router.get("/spares", response_model=list[RopeOut])
def list_spares(work: UnitOfWork = Work, key: ApiKey = Reader) -> list[RopeOut]:
    return [RopeOut.of(rope) for rope in work.ropes.spares()]


@router.get("/{serial}", response_model=RopeOut)
def read_rope(serial: str, look: Lookup = Look, key: ApiKey = Reader) -> RopeOut:
    return RopeOut.of(look.rope(serial))


@router.get("/{serial}/surveys", response_model=Paged[WearSurveyOut])
def list_surveys(
    serial: str,
    work: UnitOfWork = Work,
    look: Lookup = Look,
    paging: Paging = Page,
    key: ApiKey = Reader,
) -> Paged[WearSurveyOut]:
    """Paged, because a rope is walked monthly and lives eight years."""
    rope = look.rope(serial)
    return Paged.of(
        [
            WearSurveyOut.of(survey, assess_wear(survey).value)
            for survey in work.wear_surveys.for_rope(rope)
        ],
        paging,
    )


@router.post(
    "/{serial}/surveys",
    response_model=WearSurveyOut,
    status_code=status.HTTP_201_CREATED,
)
def record_survey(
    serial: str,
    body: WearSurveyIn,
    work: UnitOfWork = Work,
    look: Lookup = Look,
    key: ApiKey = Mechanic,
) -> WearSurveyOut:
    rope = look.rope(serial)
    survey = body.to_domain(rope)
    work.wear_surveys.add(survey)
    work.commit()
    return WearSurveyOut.of(survey, assess_wear(survey).value)


@router.get("/{serial}/file", response_model=RopeFileOut)
def read_file(
    serial: str, work: UnitOfWork = Work, look: Lookup = Look, key: ApiKey = Reader
) -> RopeFileOut:
    rope = look.rope(serial)
    surveys = work.wear_surveys.for_rope(rope)
    held = RopeFile(wear=surveys[-1] if surveys else None)
    return RopeFileOut(
        serial=rope.serial,
        verdict=held.verdict().value,
        may_run=held.may_run(),
        sources=sorted(held.sources()),
        findings=[FindingOut.of(one) for one in held.findings()],
    )
