"""Fixed width printing, because the board's forms are eighty columns wide.

Everything the department sends out is printed on a dot matrix in the workshop
or pasted into a form the board has not changed since the nineteen eighties, and
both of those want eighty columns and no escape codes. So a report is a list of
strings, each one no wider than the page, and nothing here knows what a terminal
is.

A column that will not fit is truncated rather than wrapped inside a table,
because a table row that becomes two rows stops lining up with the row above it
and the whole point of the form is that the eye can run down a column.
"""

from __future__ import annotations

from collections.abc import Iterable, Sequence
from dataclasses import dataclass
from enum import Enum
from typing import Final

from sheaveline.errors import SheavelineError

PAGE_WIDTH: Final = 80
GUTTER: Final = 1


class ReportError(SheavelineError):
    """A report was laid out in a way that will not print."""


class Align(Enum):
    LEFT = "left"
    RIGHT = "right"
    CENTRE = "centre"

    def place(self, text: str, width: int) -> str:
        if self is Align.RIGHT:
            return text.rjust(width)
        if self is Align.CENTRE:
            return text.center(width)
        return text.ljust(width)


@dataclass(frozen=True)
class Column:
    """One column of a table, with the width it is printed at."""

    heading: str
    width: int
    align: Align = Align.LEFT

    def __post_init__(self) -> None:
        if self.width < 1:
            raise ReportError(f"column {self.heading} has no width")
        if len(self.heading) > self.width:
            raise ReportError(f"the heading {self.heading} does not fit in {self.width}")

    def cell(self, value: object) -> str:
        text = "" if value is None else str(value)
        if len(text) > self.width:
            text = text[: self.width - 1] + "…" if self.width > 1 else text[:1]
        return self.align.place(text, self.width)


@dataclass(frozen=True)
class Table:
    """A run of rows under a run of headings."""

    columns: tuple[Column, ...]
    rows: tuple[tuple[object, ...], ...] = ()

    def __post_init__(self) -> None:
        if not self.columns:
            raise ReportError("a table has columns")
        if self.width() > PAGE_WIDTH:
            raise ReportError(f"a table {self.width()} wide does not fit the page")
        for row in self.rows:
            if len(row) != len(self.columns):
                raise ReportError(f"a row of {len(row)} does not fit {len(self.columns)} columns")

    @classmethod
    def of(
        cls,
        columns: Sequence[Column],
        rows: Iterable[Sequence[object]] = (),
    ) -> Table:
        return cls(tuple(columns), tuple(tuple(row) for row in rows))

    def width(self) -> int:
        return sum(column.width for column in self.columns) + GUTTER * (len(self.columns) - 1)

    def heading_line(self) -> str:
        gap = " " * GUTTER
        return gap.join(column.align.place(column.heading, column.width) for column in self.columns)

    def rule(self, character: str = "-") -> str:
        gap = " " * GUTTER
        return gap.join(character * column.width for column in self.columns)

    def lines(self) -> list[str]:
        gap = " " * GUTTER
        out = [self.heading_line(), self.rule()]
        out.extend(
            gap.join(column.cell(value) for column, value in zip(self.columns, row, strict=True))
            for row in self.rows
        )
        return out

    def with_row(self, row: Sequence[object]) -> Table:
        return Table.of(self.columns, [*self.rows, row])


def wrap(text: str, width: int = PAGE_WIDTH) -> list[str]:
    """Break prose at spaces, which is the only place a form may break it."""
    if width < 1:
        raise ReportError("nothing wraps to nothing")
    words = text.split()
    if not words:
        return [""]
    lines: list[str] = []
    current = words[0][:width]
    for word in words[1:]:
        piece = word[:width]
        if len(current) + 1 + len(piece) <= width:
            current = f"{current} {piece}"
        else:
            lines.append(current)
            current = piece
    lines.append(current)
    return lines


def rule(character: str = "=", width: int = PAGE_WIDTH) -> str:
    return character * width


def centre(text: str, width: int = PAGE_WIDTH) -> str:
    return text.center(width).rstrip()


def field(label: str, value: object, width: int = 24) -> str:
    """One labelled value, with the label padded so a column of them lines up."""
    return f"{label:<{width}} {value}"


class Report:
    """A page being built, one line at a time."""

    __slots__ = ("_lines", "_width")

    def __init__(self, width: int = PAGE_WIDTH) -> None:
        if width < 20:
            raise ReportError("a page narrower than twenty columns is not a page")
        self._width = width
        self._lines: list[str] = []

    @property
    def width(self) -> int:
        return self._width

    def line(self, text: str = "") -> Report:
        if len(text) > self._width:
            raise ReportError(f"a line of {len(text)} does not fit {self._width} columns")
        self._lines.append(text)
        return self

    def blank(self) -> Report:
        return self.line("")

    def heading(self, text: str) -> Report:
        self.line(centre(text.upper(), self._width))
        return self.line(rule("=", self._width))

    def section(self, text: str) -> Report:
        self.blank()
        self.line(text)
        return self.line(rule("-", len(text)))

    def field(self, label: str, value: object, width: int = 24) -> Report:
        return self.line(field(label, value, width))

    def prose(self, text: str) -> Report:
        for one in wrap(text, self._width):
            self.line(one)
        return self

    def table(self, table: Table) -> Report:
        for one in table.lines():
            self.line(one)
        return self

    def lines(self) -> list[str]:
        return list(self._lines)

    def text(self) -> str:
        return "\n".join(self._lines) + "\n"

    def __len__(self) -> int:
        return len(self._lines)
