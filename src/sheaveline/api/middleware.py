"""One journal line a request, with an id the caller can quote back.

The id goes out on the response as well as into the journal, because the useful
version of a bug report is somebody reading twelve characters off a tablet
screen rather than describing what they were doing at the time.

The line is written after the response, not before, so it can carry the status
and the milliseconds. A request that raises still gets its line, because the one
that fell over is the one somebody is going to come looking for.
"""

from __future__ import annotations

import time
from collections.abc import Awaitable, Callable
from typing import Final

from fastapi import FastAPI, Request, Response

from sheaveline.journal import Journal, new_request_id

REQUEST_ID_HEADER: Final = "x-request-id"
REQUEST_ID_STATE: Final = "request_id"

Handler = Callable[[Request], Awaitable[Response]]


def journal_of(request: Request) -> Journal:
    held = getattr(request.app.state, "journal", None)
    return held if isinstance(held, Journal) else Journal.silent()


def request_id_of(request: Request) -> str:
    held = getattr(request.state, REQUEST_ID_STATE, None)
    return held if isinstance(held, str) else ""


def install_journal(app: FastAPI, journal: Journal) -> None:
    """Give the application a journal and a line a request."""
    app.state.journal = journal

    @app.middleware("http")
    async def _one_line_a_request(request: Request, call_next: Handler) -> Response:
        given = request.headers.get(REQUEST_ID_HEADER)
        wanted = given if given else new_request_id()
        setattr(request.state, REQUEST_ID_STATE, wanted)
        book = journal_of(request).about(request=wanted)
        started = time.monotonic()
        try:
            reply = await call_next(request)
        except Exception as went_wrong:
            book.error(
                "request",
                method=request.method,
                path=request.url.path,
                ms=round((time.monotonic() - started) * 1000),
                outcome="raised",
                error=type(went_wrong).__name__,
            )
            raise
        book.info(
            "request",
            method=request.method,
            path=request.url.path,
            status=reply.status_code,
            ms=round((time.monotonic() - started) * 1000),
        )
        reply.headers[REQUEST_ID_HEADER] = wanted
        return reply
