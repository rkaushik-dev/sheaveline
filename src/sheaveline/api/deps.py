"""What every route asks for: a unit of work and a key that may do the job.

Both reach the running application through the request rather than through a
module level global, because the application is built by a factory and there can
be two of them in one process. That happens in the tests every day and it
happens on the office machine when the season rolls over and last season's
database is still open in another tab.
"""

from __future__ import annotations

from collections.abc import Callable, Iterator
from datetime import date
from typing import TYPE_CHECKING

from fastapi import Depends, Header, Request

from sheaveline.api.security import ApiKey, AuthError, Role
from sheaveline.persistence.uow import UnitOfWork

if TYPE_CHECKING:  # pragma: no cover
    from sheaveline.api.app import Settings

API_KEY_HEADER = "x-api-key"


def settings_of_request(request: Request) -> Settings:
    from sheaveline.api.app import settings_of

    return settings_of(request.app)


def work_of(request: Request) -> Iterator[UnitOfWork]:
    """One unit of work a request, rolled back unless the route commits."""
    settings = settings_of_request(request)
    work = UnitOfWork.on(settings.engine)
    with work:
        yield work


Work = Depends(work_of)


def key_of(request: Request, role: Role) -> ApiKey:
    settings = settings_of_request(request)
    presented = request.headers.get(API_KEY_HEADER)
    if not presented:
        raise AuthError("no key was presented")
    return settings.keys.require(presented, settings.today(), role)


def requires(role: Role) -> Callable[..., ApiKey]:
    """A dependency that refuses anything below the role it names."""

    def check(
        request: Request,
        x_api_key: str = Header(default="", alias=API_KEY_HEADER),
    ) -> ApiKey:
        return key_of(request, role)

    return check


Reader = Depends(requires(Role.READER))
Operator = Depends(requires(Role.OPERATOR))
Mechanic = Depends(requires(Role.MECHANIC))
Manager = Depends(requires(Role.MANAGER))


def today_of(request: Request) -> date:
    return settings_of_request(request).today()


Today = Depends(today_of)
