"""The standing list of jobs, which is data rather than code on purpose.

This is the department's maintenance manual turned into rows. It gets edited
when the manufacturer issues a bulletin or the board changes a rule, and that
should be an edit to one line here and nothing else. Anything that reads the
list reads it through the two functions at the bottom, so nothing downstream
holds its own copy of what a machine is due.

The codes follow the interval they run on, which is how the crew refers to them
on the radio. D is daily, W weekly, M monthly, A annual, F five yearly, H hour
based and C cycle based.
"""

from __future__ import annotations

from typing import Final

from sheaveline.plant.classes import LiftClass
from sheaveline.upkeep.interval import AERIAL, DETACHABLES, Interval, Requirement, UpkeepError

ALL_CLASSES: Final = frozenset(LiftClass)
COUNTERWEIGHTED: Final = frozenset(cls for cls in LiftClass if cls.is_aerial)

STANDARD_LIBRARY: Final[tuple[Requirement, ...]] = (
    Requirement(
        "D1",
        "ride the line and look at every sheave train",
        Interval.daily(),
        classes=ALL_CLASSES,
    ),
    Requirement("D2", "set and release the service brake", Interval.daily(), classes=ALL_CLASSES),
    Requirement("D3", "test both terminal stop switches", Interval.daily(), classes=ALL_CLASSES),
    Requirement(
        "W1",
        "walk the towers and check sheave liners and bolts",
        Interval.weekly(),
        classes=ALL_CLASSES,
    ),
    Requirement(
        "W2",
        "inspect the grips through the terminal",
        Interval.weekly(),
        classes=DETACHABLES,
    ),
    Requirement(
        "W3",
        "check the tension carriage travel and the gauge",
        Interval.weekly(),
        classes=COUNTERWEIGHTED,
    ),
    Requirement("M1", "gearbox oil level and condition", Interval.monthly(), classes=ALL_CLASSES),
    Requirement(
        "M2",
        "test the telephone and the radio to both terminals",
        Interval.monthly(),
        classes=ALL_CLASSES,
    ),
    Requirement(
        "M3",
        "check the evacuation kit seals and the rope bags",
        Interval.monthly(),
        classes=AERIAL,
    ),
    Requirement(
        "M4",
        "run the auxiliary drive under load",
        Interval.monthly(),
        classes=AERIAL,
    ),
    Requirement(
        "A1",
        "annual inspection by a licensed inspector",
        Interval.annual(),
        classes=ALL_CLASSES,
    ),
    Requirement("A2", "load test both brakes", Interval.annual(), classes=AERIAL),
    Requirement(
        "A3", "inspect every splice in the haul rope", Interval.annual(), classes=ALL_CLASSES
    ),
    Requirement("A4", "run a full evacuation drill", Interval.annual(), classes=AERIAL),
    Requirement(
        "A5",
        "repaint the tower numbers and the signage",
        Interval.annual(),
        critical=False,
        classes=ALL_CLASSES,
    ),
    Requirement(
        "F1",
        "non destructive test of the drive shaft",
        Interval.five_yearly(),
        classes=ALL_CLASSES,
    ),
    Requirement(
        "F2",
        "non destructive test of both bullwheel shafts",
        Interval.five_yearly(),
        classes=ALL_CLASSES,
    ),
    Requirement(
        "H1",
        "change the gearbox oil",
        Interval(hours=1500, days=730, grace_days=30),
        classes=ALL_CLASSES,
    ),
    Requirement(
        "H2",
        "change the hydraulic filter",
        Interval(hours=1000, days=365, grace_days=14),
        classes=ALL_CLASSES,
    ),
    Requirement(
        "C1",
        "change the grip conveyor tyres",
        Interval(cycles=500_000, days=1095, grace_days=30),
        classes=DETACHABLES,
    ),
)


def library_for(lift_class: LiftClass) -> tuple[Requirement, ...]:
    """The jobs a machine of that class actually carries."""
    return tuple(job for job in STANDARD_LIBRARY if job.applies_to(lift_class))


def requirement(code: str) -> Requirement:
    """One job by its code, which is how the radio refers to it."""
    for job in STANDARD_LIBRARY:
        if job.code == code.upper():
            return job
    raise UpkeepError(f"there is no job {code}")


def codes_for(lift_class: LiftClass) -> tuple[str, ...]:
    return tuple(job.code for job in library_for(lift_class))


def critical_codes_for(lift_class: LiftClass) -> tuple[str, ...]:
    return tuple(job.code for job in library_for(lift_class) if job.critical)
