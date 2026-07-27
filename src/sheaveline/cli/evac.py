"""Commands about getting people off the line.

The drill command is the one with teeth. A machine whose annual full line drill
has run out does not carry the public, and the register is the only thing that
says whether it has. So the check exits badly when a machine is out of date,
because it runs on a timer in November and somebody needs to be told.

The staging command is the planning one. It takes the teams that are on the hill
and works out where to park them so the busiest one finishes soonest, which is
what actually sets the clearing time, and prints the answer as a beat sheet
somebody can carry.
"""

from __future__ import annotations

from datetime import date, datetime

import click

from sheaveline.capacity.spacing import LineSpacing
from sheaveline.cli.common import day_of, day_option, machine, printed
from sheaveline.cli.main import Console, cli, handles_refusals
from sheaveline.errors import ParseError
from sheaveline.evac.drill import Drill, DrillKind
from sheaveline.evac.profile import EvacuationProfile
from sheaveline.evac.staging import stage
from sheaveline.evac.team import RescueKit, RescueTeam, TeamPool
from sheaveline.line.catenary import SagModel, line_weight
from sheaveline.measures.duration import Duration
from sheaveline.measures.length import Length
from sheaveline.measures.load import Load
from sheaveline.plant.identity import LiftCode
from sheaveline.plant.lift import Lift
from sheaveline.reports.forms import Align, Column, Table


@cli.group()
def evac() -> None:
    """Drills, staging and whether a machine may still carry the public."""


@evac.command("drill")
@click.argument("code")
@click.option(
    "--kind",
    type=click.Choice([one.name.lower() for one in DrillKind]),
    default="full_line",
    show_default=True,
)
@click.option("--team", "teams", multiple=True, required=True, help="Give it once a team.")
@click.option("--carriers", type=int, required=True, help="How many were actually worked.")
@click.option("--minutes", type=int, required=True, help="How long it took.")
@click.option("--observer", required=True, help="Who watched and signed it.")
@day_option("The day it was run.")
@click.pass_obj
@handles_refusals
def record_drill(
    console: Console,
    code: str,
    kind: str,
    teams: tuple[str, ...],
    carriers: int,
    minutes: int,
    observer: str,
    on: datetime | None,
) -> None:
    """Write a drill into the register."""
    when = day_of(console, on)
    drill = Drill.of(
        lift=LiftCode.parse(code),
        on=when,
        kind=DrillKind[kind.upper()],
        teams=teams,
        carriers_worked=carriers,
        elapsed=Duration.from_minutes(minutes),
        observer=observer,
    )
    with console.work() as work:
        work.drills.add(drill)
        work.commit()
    console.say(str(drill))
    if drill.counts_for_the_annual():
        console.say(f"the annual now runs to {drill.expires().isoformat()}")
    else:
        console.say("this one does not satisfy the annual")


@evac.command("check")
@day_option("The day to read the register against.")
@click.pass_obj
@handles_refusals
def check_drills(console: Console, on: datetime | None) -> None:
    """Whether every machine's annual drill is still current."""
    when = day_of(console, on)
    with console.work() as work:
        held = work.drills.register()
        codes = work.lifts.codes()
    rows = []
    stale = []
    for code in codes:
        expires = held.lift_expires(code)
        current = held.lift_is_current(code, when)
        if not current:
            stale.append(code)
        last = held.last_full(code)
        rows.append(
            (
                str(code),
                "never" if last is None else last.on.isoformat(),
                printed(expires, "never"),
                "current" if current else "OUT OF DATE",
            )
        )
    console.page(
        Table.of(
            [
                Column("lift", 5),
                Column("last full drill", 16),
                Column("runs out", 12),
                Column("state", 12),
            ],
            rows,
        ).lines()
    )
    if stale:
        console.say("")
        for code in stale:
            console.say(f"{code} does not carry the public until it has been drilled")
        raise SystemExit(1)


@evac.command("staging")
@click.argument("code")
@click.option("--team", "teams", multiple=True, required=True, metavar="NAME:LADDER:ROPE")
@click.option("--seats", default=3, show_default=True, help="How full the carriers are.")
@click.option("--snow", default="2 ft", show_default=True)
@click.option("--tension", default="34500 lb", show_default=True)
@day_option("The day to read the training currency against.")
@click.pass_obj
@handles_refusals
def staging_plan(
    console: Console,
    code: str,
    teams: tuple[str, ...],
    seats: int,
    snow: str,
    tension: str,
    on: datetime | None,
) -> None:
    """Where to park the teams so the busiest one finishes soonest.

    The teams are given as name:ladder:rope, in feet, because that is what is on
    the truck. A team with no rope gets a dash.
    """
    when = day_of(console, on)
    with console.work() as work:
        lift = machine(work, code)
    profile = EvacuationProfile(lift, _sag(lift, tension), Length.parse(snow))
    pool = TeamPool.of([_team(one, when) for one in teams])
    plan = stage(profile, pool, Length(0), when, seats_filled=seats)
    console.say(str(plan))
    if not plan.beats:
        for line in plan.refusals():
            console.say(line)
        raise SystemExit(1)
    console.page(
        Table.of(
            [
                Column("team", 12),
                Column("staged at", 12, Align.RIGHT),
                Column("carriers", 9, Align.RIGHT),
                Column("walking", 12, Align.RIGHT),
                Column("busy for", 12, Align.RIGHT),
            ],
            [
                (
                    one.team.name,
                    f"{one.staged_at.feet:.0f} ft",
                    len(one.stops),
                    f"{one.walking().feet:.0f} ft",
                    one.work().format_clock(),
                )
                for one in plan.beats
            ],
        ).lines()
    )
    for line in plan.refusals():
        console.say(line)
    if not plan.is_workable():
        raise SystemExit(1)


def _sag(lift: Lift, tension: str) -> SagModel:
    spacing = LineSpacing.of(lift)
    weight = line_weight(
        lift.rope.spec.weight, lift.carriers.on_line()[0].gross(), spacing.spacing()
    )
    return SagModel(weight, Load.parse(tension))


def _team(given: str, on: date) -> RescueTeam:
    """A team off the truck, written name:ladder:rope in feet."""
    parts = given.split(":")
    if len(parts) != 3:
        raise ParseError(given, "a team written name:ladder:rope")
    name, ladder, rope = parts
    return RescueTeam.of(
        name.strip(),
        (f"{name.strip()} lead", f"{name.strip()} two", f"{name.strip()} three"),
        RescueKit(
            ladder=None if ladder.strip() in ("", "-") else Length.parse(f"{ladder} ft"),
            rope=None if rope.strip() in ("", "-") else Length.parse(f"{rope} ft"),
            seats=2,
            harnesses=3,
            radios=1,
        ),
        Length(0),
        date(on.year + 1, on.month, on.day),
    )
