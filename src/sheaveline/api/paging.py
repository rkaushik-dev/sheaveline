"""Handing back part of a list, because some of these lists do not stop growing.

Most of what this system holds is small. There are six machines and there will
be six machines next year. Three things are not: the weather readings, which
arrive every ten minutes from every station all winter, the stops, and the day
logs. A tablet at the bottom terminal on a bar of signal asking for the whole
observation table is a request that times out and then gets retried.

Offset and limit rather than a cursor. The tables that need it are ordered by a
timestamp and read far more often than they are written, and a cursor buys
correctness under concurrent insert that nobody here is going to notice. Saying
so out loud is cheaper than pretending otherwise later.

The page carries the total on purpose. The screen that shows it says how many
readings there are and lets somebody skip to the end, and working that out from
a next link is worse than asking for a count.
"""

from __future__ import annotations

from collections.abc import Sequence
from typing import Final, Generic, TypeVar

from fastapi import Depends, Query
from pydantic import BaseModel

DEFAULT_LIMIT: Final = 50
MOST_AT_ONCE: Final = 500

ItemT = TypeVar("ItemT")


class Paging:
    """How much of a list the caller wants and where to start."""

    __slots__ = ("limit", "offset")

    def __init__(self, limit: int = DEFAULT_LIMIT, offset: int = 0) -> None:
        self.limit = limit
        self.offset = offset

    def cut(self, everything: Sequence[ItemT]) -> list[ItemT]:
        return list(everything[self.offset : self.offset + self.limit])

    def next_offset(self, total: int) -> int | None:
        following = self.offset + self.limit
        return following if following < total else None

    def __repr__(self) -> str:  # pragma: no cover - a debugging convenience
        return f"Paging(limit={self.limit}, offset={self.offset})"


def paging_of(
    limit: int = Query(
        default=DEFAULT_LIMIT,
        ge=1,
        le=MOST_AT_ONCE,
        description="How many rows to hand back at once.",
    ),
    offset: int = Query(default=0, ge=0, description="How many rows to skip."),
) -> Paging:
    return Paging(limit, offset)


Page = Depends(paging_of)


class Paged(BaseModel, Generic[ItemT]):
    """One page of a list, and enough to ask for the next."""

    items: list[ItemT]
    total: int
    limit: int
    offset: int
    next_offset: int | None = None

    @classmethod
    def of(cls, everything: Sequence[ItemT], paging: Paging) -> Paged[ItemT]:
        return cls(
            items=paging.cut(everything),
            total=len(everything),
            limit=paging.limit,
            offset=paging.offset,
            next_offset=paging.next_offset(len(everything)),
        )

    def is_last(self) -> bool:
        return self.next_offset is None

    def __len__(self) -> int:
        return len(self.items)
