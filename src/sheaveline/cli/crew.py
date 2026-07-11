"""Commands about who is carded and who is standing where.

The gap command is the one the lift supervisor runs on Thursday for the weekend.
It answers one question a machine: with the cards that are current on that day,
can every required post be filled, and if not, who is short of what.

A card is issued rather than edited. Renewing writes a new one and leaves the old
in the file, because the question an inspector asks is not what somebody holds
today but what they held on the day of the incident.
"""

from __future__ import annotations

from datetime import datetime

import click

from sheaveline.cli.common import DAY_FORMAT, day_of, day_option
from sheaveline.cli.main import Console, cli, handles_refusals
from sheaveline.ops.shift import Assignment, Post
from sheaveline.people.assignment import RosterCheck
from sheaveline.people.certification import POST_NEEDS, Certificate, Endorsement
from sheaveline.plant.classes import LiftClass
from sheaveline.plant.identity import LiftCode
from sheaveline.reports.forms import Align, Column, Table
from sheaveline.schedule.hours import OperatingWindow, TimeOfDay

WARN_WITHIN_DAYS = 45


@cli.group()
def crew() -> None:
    """Cards, shifts and whether the posts can be filled."""


def _classes(given: tuple[str, ...]) -> frozenset[LiftClass]:
    """A card names the classes by the short name, the way the radio does."""
    if not given:
        return frozenset(LiftClass)
    return frozenset(LiftClass[one.strip().upper()] for one in given)


@crew.command("card")
@click.argument("person")
@click.argument("endorsement", type=click.Choice([one.name.lower() for one in Endorsement]))
@click.option("--issued", type=click.DateTime([DAY_FORMAT]), required=True)
@click.option("--expires", type=click.DateTime([DAY_FORMAT]), required=True)
@click.option("--by", required=True, help="Who signed it.")
@click.option(
    "--only",
    multiple=True,
    type=click.Choice([one.name.lower() for one in LiftClass]),
    help="Limit the card to these classes. Give it once a class.",
)
@click.pass_obj
@handles_refusals
def issue_card(
    console: Console,
    person: str,
    endorsement: str,
    issued: datetime,
    expires: datetime,
    by: str,
    only: tuple[str, ...],
) -> None:
    """Write a card into the file."""
    card = Certificate(
        person=person,
        endorsement=Endorsement[endorsement.upper()],
        issued=issued.date(),
        expires=expires.date(),
        issued_by=by,
        classes=_classes(only),
    )
    with console.work() as work:
        work.personnel.issue(card)
        work.commit()
    console.say(str(card))
    if only:
        console.say("covers   " + ", ".join(sorted(one.value for one in card.classes)))


@crew.command("renew")
@click.argument("person")
@click.argument("endorsement", type=click.Choice([one.name.lower() for one in Endorsement]))
@click.option("--expires", type=click.DateTime([DAY_FORMAT]), required=True)
@click.option("--by", required=True, help="Who signed it.")
@click.pass_obj
@handles_refusals
def renew_card(console: Console, person: str, endorsement: str, expires: datetime, by: str) -> None:
    """Renew a card. The old one stays in the file."""
    with console.work() as work:
        renewed = work.personnel.renew(person, Endorsement[endorsement.upper()], expires.date(), by)
        work.commit()
    console.say(str(renewed))


@crew.command("cards")
@click.option("--person", default=None, help="Just this one.")
@click.option("--expiring", default=WARN_WITHIN_DAYS, show_default=True, help="Days ahead.")
@day_option()
@click.pass_obj
@handles_refusals
def list_cards(console: Console, person: str | None, expiring: int, on: datetime | None) -> None:
    """Who is carded for what, and what runs out soon."""
    when = day_of(console, on)
    with console.work() as work:
        held = work.personnel.file()
        cards = work.personnel.for_person(person) if person else list(held.certificates)
    if not cards:
        console.say("nothing is on file")
        return
    console.page(
        Table.of(
            [
                Column("person", 16),
                Column("card", 16),
                Column("expires", 12),
                Column("days", 6, Align.RIGHT),
                Column("state", 10),
            ],
            [
                (
                    one.person,
                    one.endorsement.value,
                    one.expires.isoformat(),
                    one.days_left(when),
                    "lapsed" if not one.is_current(when) else "current",
                )
                for one in sorted(cards, key=lambda one: (one.person, one.expires))
            ],
        ).lines()
    )
    soon = held.expiring_within(when, expiring)
    for one in soon:
        console.say(f"{one.person} {one.endorsement.value} runs out {one.expires.isoformat()}")


@crew.command("assign")
@click.argument("code")
@click.argument("person")
@click.argument("post", type=click.Choice([one.name.lower() for one in Post]))
@click.option("--starts", default="08:30", show_default=True)
@click.option("--ends", default="16:45", show_default=True)
@day_option()
@click.pass_obj
@handles_refusals
def assign_shift(
    console: Console,
    code: str,
    person: str,
    post: str,
    starts: str,
    ends: str,
    on: datetime | None,
) -> None:
    """Put somebody on a post for the day."""
    when = day_of(console, on)
    lift = LiftCode.parse(code)
    shift = Assignment(
        person=person,
        post=Post[post.upper()],
        starts=TimeOfDay.parse(starts),
        ends=TimeOfDay.parse(ends),
    )
    with console.work() as work:
        elsewhere = [
            (other, one)
            for other, one in work.personnel.working(person, when)
            if other != lift and one.overlaps(shift)
        ]
        work.personnel.assign(lift, when, shift)
        work.commit()
    console.say(f"{person} on {lift} at {shift.post.value}, {shift.starts} to {shift.ends}")
    for other, clash in elsewhere:
        console.say(f"but {person} is also down on {other} {clash.starts} to {clash.ends}")


@crew.command("gap")
@click.argument("code", required=False)
@click.option("--first-load", default="09:00", show_default=True)
@click.option("--last-load", default="16:00", show_default=True)
@click.option("--sweep-by", default="16:30", show_default=True)
@day_option()
@click.pass_obj
@handles_refusals
def staffing_gap(
    console: Console,
    code: str | None,
    first_load: str,
    last_load: str,
    sweep_by: str,
    on: datetime | None,
) -> None:
    """Whether every required post can be filled, and who is short of what.

    With no machine it walks the whole register, which is the version the
    supervisor runs on Thursday for the weekend.
    """
    when = day_of(console, on)
    window = OperatingWindow.parse(first_load, last_load, sweep_by)
    short = 0
    with console.work() as work:
        held = work.personnel.file()
        wanted = [LiftCode.parse(code)] if code else work.lifts.codes()
        for lift in wanted:
            roster = work.personnel.roster(lift, when, window)
            check = RosterCheck(roster, held, roster.lift_class, when)
            console.say(f"{lift}  {'legal' if check.is_legal() else 'SHORT'}")
            if check.is_legal():
                continue
            short += 1
            for problem in check.problems():
                cover = check.cover_for(problem.post, problem.at)
                needed = POST_NEEDS[problem.post].value
                spare = ", ".join(cover) if cover else f"nobody carded {needed} is free"
                console.say(f"  {problem}")
                console.say(f"    cover: {spare}")
    if short:
        console.say("")
        console.say(f"{short} machines cannot be staffed on {when.isoformat()}")
        raise SystemExit(1)
