"""One place that says what the rope file makes of a rope.

There were three of these, one on each survey type, and each was a chain of
tests with the thresholds buried in it. That was fine while there was one of
them. With three, the department had three places to look when the board asked
where a figure came from, and the three did not agree about what monitor meant.

So the thresholds are rows now, each row says which reading it reads and what it
costs, and the surveys carry readings rather than opinions. What is left is a
rope file that reads all four sources and returns the worst of them with the
rows that fired attached.
"""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from datetime import date
from decimal import Decimal
from typing import Final

from sheaveline.ropework.breaks import BreakSurvey
from sheaveline.ropework.mrt import (
    LMA_CONDEMN,
    LMA_WATCH,
    LOCAL_FAULT_CONDEMN,
    LOCAL_FAULT_WATCH,
    MagneticTest,
    RetestSchedule,
)
from sheaveline.ropework.splice import SpliceRegister
from sheaveline.ropework.tension import TensionSystem
from sheaveline.ropework.wear import RopeworkError, WearSurvey, WearVerdict


@dataclass(frozen=True)
class Finding:
    """One row that fired, with the reading that fired it."""

    source: str
    verdict: WearVerdict
    detail: str

    def __str__(self) -> str:
        return f"{self.source}: {self.detail}"


@dataclass(frozen=True)
class WearRule:
    """One threshold on one reading, and what breaching it costs."""

    name: str
    verdict: WearVerdict
    reading: Callable[[WearSurvey], Decimal]
    breached: Callable[[Decimal], bool]
    describe: Callable[[Decimal], str]

    def check(self, survey: WearSurvey) -> Finding | None:
        value = self.reading(survey)
        if not self.breached(value):
            return None
        return Finding(self.name, self.verdict, self.describe(value))


def _worst_percent(survey: WearSurvey) -> Decimal:
    return survey.percent_of_nominal(survey.smallest())


def _ovality_fraction(survey: WearSurvey) -> Decimal:
    worst = survey.worst_ovality().ovality()
    return worst.ratio(survey.nominal)


WEAR_RULES: Final[tuple[WearRule, ...]] = (
    WearRule(
        "diameter",
        WearVerdict.CONDEMNED,
        _worst_percent,
        lambda value: value < 93,
        lambda value: f"{value.quantize(Decimal('0.1'))}% of nominal",
    ),
    WearRule(
        "lay length",
        WearVerdict.CONDEMNED,
        lambda survey: survey.worst_lay_stretch(),
        lambda value: value > Decimal("0.05"),
        lambda value: f"lay opened {(value * 100).quantize(Decimal('0.1'))}%",
    ),
    WearRule(
        "diameter",
        WearVerdict.MONITOR,
        _worst_percent,
        lambda value: value < 97,
        lambda value: f"{value.quantize(Decimal('0.1'))}% of nominal",
    ),
    WearRule(
        "ovality",
        WearVerdict.MONITOR,
        _ovality_fraction,
        lambda value: value > Decimal("0.05"),
        lambda value: f"{(value * 100).quantize(Decimal('0.1'))}% out of round",
    ),
    WearRule(
        "lay length",
        WearVerdict.MONITOR,
        lambda survey: survey.worst_lay_stretch(),
        lambda value: value > Decimal("0.02"),
        lambda value: f"lay opened {(value * 100).quantize(Decimal('0.1'))}%",
    ),
)


def wear_findings(survey: WearSurvey) -> tuple[Finding, ...]:
    return tuple(found for rule in WEAR_RULES if (found := rule.check(survey)) is not None)


def assess_wear(survey: WearSurvey) -> WearVerdict:
    return _worst_of(wear_findings(survey))


def break_findings(survey: BreakSurvey) -> tuple[Finding, ...]:
    found = []
    for seen in survey.exceedances():
        found.append(Finding("broken wires", WearVerdict.CONDEMNED, str(seen)))
    for seen in survey.near_limit():
        found.append(Finding("broken wires", WearVerdict.MONITOR, str(seen)))
    return tuple(found)


def assess_breaks(survey: BreakSurvey) -> WearVerdict:
    return _worst_of(break_findings(survey))


def test_findings(test: MagneticTest) -> tuple[Finding, ...]:
    found = []
    loss = test.peak_loss()
    fault = test.largest_fault()
    if loss > LMA_CONDEMN:
        found.append(Finding("magnetic test", WearVerdict.CONDEMNED, f"{loss}% loss of area"))
    elif loss > LMA_WATCH:
        found.append(Finding("magnetic test", WearVerdict.MONITOR, f"{loss}% loss of area"))
    if fault > LOCAL_FAULT_CONDEMN:
        found.append(Finding("magnetic test", WearVerdict.CONDEMNED, f"{fault} wire fault"))
    elif fault > LOCAL_FAULT_WATCH:
        found.append(Finding("magnetic test", WearVerdict.MONITOR, f"{fault} wire fault"))
    return tuple(found)


def assess_test(test: MagneticTest) -> WearVerdict:
    return _worst_of(test_findings(test))


def _worst_of(findings: tuple[Finding, ...]) -> WearVerdict:
    verdicts = {found.verdict for found in findings}
    if WearVerdict.CONDEMNED in verdicts:
        return WearVerdict.CONDEMNED
    if WearVerdict.MONITOR in verdicts:
        return WearVerdict.MONITOR
    return WearVerdict.SERVICEABLE


@dataclass(frozen=True)
class RopeFile:
    """Everything known about one rope, read together."""

    wear: WearSurvey | None = None
    breaks: BreakSurvey | None = None
    magnetic: MagneticTest | None = None
    splices: SpliceRegister | None = None
    tension: TensionSystem | None = None
    retest: RetestSchedule | None = None

    def findings(self, on: date | None = None) -> tuple[Finding, ...]:
        found: list[Finding] = []
        if self.wear is not None:
            found.extend(wear_findings(self.wear))
        if self.breaks is not None:
            found.extend(break_findings(self.breaks))
        if self.magnetic is not None:
            found.extend(test_findings(self.magnetic))
        if self.splices is not None and not self.splices.is_acceptable():
            found.append(Finding("splices", WearVerdict.CONDEMNED, "splice register fails"))
        if self.tension is not None and not self.tension.has_reserve():
            found.append(
                Finding("tension", WearVerdict.MONITOR, "carriage has no travel in reserve")
            )
        if on is not None and self.retest is not None and self.retest.is_overdue(on):
            found.append(Finding("magnetic test", WearVerdict.CONDEMNED, "magnetic test overdue"))
        return tuple(found)

    def verdict(self, on: date | None = None) -> WearVerdict:
        return _worst_of(self.findings(on))

    def sources(self, on: date | None = None) -> frozenset[str]:
        return frozenset(found.source for found in self.findings(on))

    def worst(self, on: date | None = None) -> Finding:
        found = self.findings(on)
        if not found:
            raise RopeworkError("nothing in this rope file has anything against it")
        return next(one for one in found if one.verdict is self.verdict(on))

    def may_run(self, on: date | None = None) -> bool:
        return self.verdict(on).may_run
