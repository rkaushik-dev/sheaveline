"""The rope file, printed.

This is the document that goes in the folder with the rope's name on it and
follows the rope to whatever machine it ends up on. The board asks for it when
a rope is retired and an insurer asks for it when something has gone wrong, and
in both cases the question is the same: show me every time somebody looked at
this rope and what they found.

So it is a history rather than a status. The current verdict is on it, once, at
the top, and the rest of the page is the walks in the order they were walked.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass, field
from datetime import date

from sheaveline.plant.rope import HaulRope
from sheaveline.reports.forms import Align, Column, Report, Table
from sheaveline.ropework.breaks import BreakSurvey
from sheaveline.ropework.mrt import MagneticTest
from sheaveline.ropework.splice import SpliceRegister
from sheaveline.ropework.verdict import (
    RopeFile,
    assess_breaks,
    assess_test,
    assess_wear,
)
from sheaveline.ropework.wear import WearSurvey


@dataclass(frozen=True)
class RopeFilePage:
    """One rope and everything written about it."""

    rope: HaulRope
    on: date
    machine: str
    wear: tuple[WearSurvey, ...] = field(default_factory=tuple)
    breaks: tuple[BreakSurvey, ...] = field(default_factory=tuple)
    tests: tuple[MagneticTest, ...] = field(default_factory=tuple)
    splices: SpliceRegister | None = None

    @classmethod
    def of(
        cls,
        rope: HaulRope,
        on: date,
        machine: str,
        wear: Sequence[WearSurvey] = (),
        breaks: Sequence[BreakSurvey] = (),
        tests: Sequence[MagneticTest] = (),
        splices: SpliceRegister | None = None,
    ) -> RopeFilePage:
        return cls(
            rope,
            on,
            machine,
            tuple(sorted(wear, key=lambda one: one.taken)),
            tuple(sorted(breaks, key=lambda one: one.taken)),
            tuple(sorted(tests, key=lambda one: one.taken)),
            splices,
        )

    def file(self) -> RopeFile:
        """The verdict, read off the most recent of each kind."""
        return RopeFile(
            wear=self.wear[-1] if self.wear else None,
            breaks=self.breaks[-1] if self.breaks else None,
            magnetic=self.tests[-1] if self.tests else None,
            splices=self.splices,
        )

    def times_looked_at(self) -> int:
        return len(self.wear) + len(self.breaks) + len(self.tests)

    def render(self) -> Report:
        page = Report()
        page.heading(f"rope file {self.rope.serial}")
        page.field("printed", self.on.isoformat())
        page.field("on machine", self.machine)
        page.field("construction", self.rope.spec.construction.value)
        page.field("grade", self.rope.spec.grade.value)
        page.field("lay", self.rope.spec.lay.value)
        page.field("nominal diameter", self.rope.diameter.format_inches(3))
        page.field("condemning diameter", self.rope.spec.condemning_diameter().format_inches(3))
        page.field("breaking strength", self.rope.spec.breaking_strength.format_pounds())
        page.field("length", f"{self.rope.length.feet:.0f} ft")
        page.field("installed", self.rope.installed.isoformat())
        page.field("splices", self.rope.splices)
        page.field("seasons in service", self.rope.seasons_in_service(self.on))

        held = self.file()
        page.section("verdict")
        page.field("standing", held.verdict(self.on).value)
        page.field("may run", "yes" if held.may_run(self.on) else "no")
        for one in held.findings(self.on):
            page.line(f"    {one}")
        if not held.findings(self.on):
            page.line("    nothing recorded against it")

        if self.wear:
            page.section("caliper walks")
            page.table(
                Table.of(
                    [
                        Column("taken", 11),
                        Column("inspector", 18),
                        Column("smallest", 10, Align.RIGHT),
                        Column("station", 10, Align.RIGHT),
                        Column("verdict", 12),
                    ],
                    [
                        (
                            one.taken.isoformat(),
                            one.inspector,
                            one.smallest().mean().format_inches(3),
                            f"{one.smallest().station.feet:.0f} ft",
                            assess_wear(one).value,
                        )
                        for one in self.wear
                    ],
                )
            )

        if self.breaks:
            page.section("broken wire counts")
            page.table(
                Table.of(
                    [
                        Column("taken", 11),
                        Column("inspector", 18),
                        Column("counted", 8, Align.RIGHT),
                        Column("verdict", 12),
                    ],
                    [
                        (
                            one.taken.isoformat(),
                            one.inspector,
                            one.total_breaks(),
                            assess_breaks(one).value,
                        )
                        for one in self.breaks
                    ],
                )
            )

        if self.tests:
            page.section("magnetic tests")
            page.table(
                Table.of(
                    [
                        Column("taken", 11),
                        Column("contractor", 24),
                        Column("peak loss", 10, Align.RIGHT),
                        Column("fault", 6, Align.RIGHT),
                        Column("verdict", 12),
                    ],
                    [
                        (
                            one.taken.isoformat(),
                            one.contractor,
                            f"{one.peak_loss()}%",
                            one.largest_fault(),
                            assess_test(one).value,
                        )
                        for one in self.tests
                    ],
                )
            )

        if self.splices is not None:
            page.section("splices")
            page.table(
                Table.of(
                    [
                        Column("station", 10, Align.RIGHT),
                        Column("made", 11),
                        Column("by", 18),
                        Column("tucks", 6, Align.RIGHT),
                        Column("kind", 12),
                    ],
                    [
                        (
                            f"{one.station.feet:.0f} ft",
                            one.made.isoformat(),
                            one.made_by,
                            one.tucks,
                            "mill" if one.mill else "field",
                        )
                        for one in self.splices.splices
                    ],
                )
            )

        page.blank()
        page.line(f"looked at {self.times_looked_at()} times")
        return page

    def text(self) -> str:
        return self.render().text()
