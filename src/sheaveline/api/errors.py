"""One error shape for the whole surface.

Every failure that reaches the http layer comes out of the same handler and
looks the same on the wire: a kind, a sentence and, where there is one, the
field that caused it. The client on the other end is a tablet in a terminal
being read by somebody with gloves on, and a body that changes shape depending
on which layer raised is a body nobody writes a screen against.

The mapping from exception to status code is a table rather than a chain,
because it is read far more often than it is changed and the question it answers
is always which one of these is a four hundred and which is a five hundred.
"""

from __future__ import annotations

from typing import Final

from fastapi import FastAPI, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel

from sheaveline.board.incident import BoardError
from sheaveline.capacity.spacing import CapacityError
from sheaveline.errors import MeasureError, ParseError, RangeError, SheavelineError
from sheaveline.evac.profile import EvacuationError
from sheaveline.line.catenary import LineError
from sheaveline.ops.preop import OperationsError
from sheaveline.people.certification import PeopleError
from sheaveline.persistence.base import StorageError
from sheaveline.plant.classes import PlantError
from sheaveline.ropework.wear import RopeworkError
from sheaveline.schedule.season import SeasonError
from sheaveline.upkeep.interval import UpkeepError
from sheaveline.weather.observation import WeatherError


class ErrorBody(BaseModel):
    """What every failure looks like on the wire."""

    kind: str
    detail: str
    field: str | None = None


STATUS_FOR: Final[dict[type[Exception], int]] = {
    ParseError: status.HTTP_400_BAD_REQUEST,
    RangeError: status.HTTP_400_BAD_REQUEST,
    MeasureError: status.HTTP_400_BAD_REQUEST,
    PlantError: status.HTTP_409_CONFLICT,
    SeasonError: status.HTTP_409_CONFLICT,
    RopeworkError: status.HTTP_409_CONFLICT,
    LineError: status.HTTP_409_CONFLICT,
    CapacityError: status.HTTP_409_CONFLICT,
    WeatherError: status.HTTP_409_CONFLICT,
    OperationsError: status.HTTP_409_CONFLICT,
    UpkeepError: status.HTTP_409_CONFLICT,
    EvacuationError: status.HTTP_409_CONFLICT,
    PeopleError: status.HTTP_409_CONFLICT,
    BoardError: status.HTTP_409_CONFLICT,
    StorageError: status.HTTP_404_NOT_FOUND,
}


class NotFound(SheavelineError):
    """Asked for something that is not on file."""


def status_for(error: Exception) -> int:
    """The most specific entry in the table that this error is an instance of."""
    if isinstance(error, NotFound):
        return status.HTTP_404_NOT_FOUND
    best: int | None = None
    depth = -1
    for kind, code in STATUS_FOR.items():
        if isinstance(error, kind):
            here = len(kind.__mro__)
            if here > depth:
                depth = here
                best = code
    return best if best is not None else status.HTTP_500_INTERNAL_SERVER_ERROR


def body_for(error: Exception) -> ErrorBody:
    field = None
    if isinstance(error, RangeError):
        field = error.what
    elif isinstance(error, ParseError):
        field = error.wanted
    return ErrorBody(kind=type(error).__name__, detail=str(error), field=field)


def install_handlers(app: FastAPI) -> None:
    """Every domain failure leaves through here and nowhere else."""

    @app.exception_handler(SheavelineError)
    async def _domain(request: Request, error: Exception) -> JSONResponse:
        return JSONResponse(
            status_code=status_for(error),
            content=body_for(error).model_dump(),
        )

    @app.exception_handler(RequestValidationError)
    async def _validation(request: Request, error: Exception) -> JSONResponse:
        first = ""
        if isinstance(error, RequestValidationError) and error.errors():
            problem = error.errors()[0]
            first = ".".join(str(part) for part in problem["loc"][1:])
        return JSONResponse(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            content=ErrorBody(
                kind="RequestValidationError",
                detail="the request does not read as what the route wants",
                field=first or None,
            ).model_dump(),
        )
