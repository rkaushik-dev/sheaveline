"""The option every command in this program grew its own copy of.

Sixteen commands take a day and default it to today, and each of them had the
same four line click option written out above it and its own little helper below
it to turn what click handed back into a date. Three of those helpers were the
same function under two names, and a fourth was a different function under one of
the same names, which is the point at which this stops being tidiness.

The formats live here too. A date is written the way a date is written on a
tramway board form, and a moment is that plus a wall clock time, and neither of
them is negotiable per command.
"""

from __future__ import annotations

from collections.abc import Callable
from datetime import date, datetime
from typing import Any, Final, Protocol, TypeVar

import click

from sheaveline.persistence.uow import UnitOfWork
from sheaveline.plant.identity import LiftCode, RopeSerial
from sheaveline.plant.lift import Lift
from sheaveline.plant.rope import HaulRope

DAY_FORMAT: Final = "%Y-%m-%d"
MOMENT_FORMAT: Final = "%Y-%m-%d %H:%M"

Command = TypeVar("Command", bound=Callable[..., Any])


def day_option(
    help: str = "The day it happened. Today if you leave it off.",
) -> Callable[[Command], Command]:
    """The --on option, written once."""
    return click.option(
        "--on",
        "on",
        type=click.DateTime([DAY_FORMAT]),
        default=None,
        metavar="YYYY-MM-DD",
        help=help,
    )


def moment_option(
    help: str = "When it happened, to the minute. Now if you leave it off.",
) -> Callable[[Command], Command]:
    """The --at option, which takes a whole day as well as a moment on purpose.

    Somebody writing up yesterday's incident at eight the next morning has the
    date in front of them and not the minute, and refusing the date would only
    teach them to invent a time.
    """
    return click.option(
        "--at",
        "at",
        type=click.DateTime([MOMENT_FORMAT, DAY_FORMAT]),
        default=None,
        metavar="'YYYY-MM-DD HH:MM'",
        help=help,
    )


class HasClock(Protocol):
    """What these two need, which is less than the whole console.

    A protocol rather than a base class, because the console lives in main and
    main imports this module. Asking for the shape instead of the type keeps the
    arrow pointing one way.
    """

    def today(self) -> date: ...

    def now(self) -> datetime: ...


def day_of(console: HasClock, given: datetime | None) -> date:
    """What click handed back, or the day the console thinks it is."""
    return given.date() if given is not None else console.today()


def moment_of(console: HasClock, given: datetime | None) -> datetime:
    return given if given is not None else console.now()


def printed(wanted: date | None, missing: str = "") -> str:
    """A date on a page, or a word saying there is not one."""
    return missing if wanted is None else wanted.isoformat()


def machine(work: UnitOfWork, code: str) -> Lift:
    """A machine off the register, named the way it is named on the radio."""
    return work.lifts.get(LiftCode.parse(code))


def haul_rope(work: UnitOfWork, serial: str) -> HaulRope:
    """A rope off the file, named the way the mill stamped it."""
    return work.ropes.get(RopeSerial.parse(serial))
