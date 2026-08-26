"""The order the office actually does things in, printed at the keyboard.

Every command in this program has a help string and none of them says what to run
before it. Somebody starting a season needs to know that a machine goes on the
register before it can be drilled, and that it has to be drilled before the
morning page will let it open, and that is not a thing --help can tell you.

So the sequences live here, as data, and the command prints them. Deliberately
not a document in the repository: a document nobody opens goes stale and this is
in the same binary as the commands it names, so a command that gets renamed
breaks the test that checks the guide names real commands.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Final

import click

from sheaveline.cli.main import Console, cli, handles_refusals
from sheaveline.reports.forms import Report


@dataclass(frozen=True)
class Step:
    """One command, and why it is at this point in the order."""

    command: str
    why: str


@dataclass(frozen=True)
class Sequence:
    """One thing the office does, and the order it does it in."""

    name: str
    when: str
    steps: tuple[Step, ...]

    def commands(self) -> tuple[str, ...]:
        return tuple(one.command for one in self.steps)


SEQUENCES: Final[tuple[Sequence, ...]] = (
    Sequence(
        name="opening a machine",
        when="once, when a lift goes on the register",
        steps=(
            Step("setup", "build the tables, which is safe to run again"),
            Step("lift load", "read the machine off its drawing file"),
            Step("lift show", "check the register has what the drawing said"),
            Step("lift shoot", "shoot the towers, so there is something to trend against"),
        ),
    ),
    Sequence(
        name="starting a season",
        when="November, before anybody rides",
        steps=(
            Step("upkeep due", "read the ladder and see what the summer left"),
            Step("upkeep record", "sign off every job, oldest first"),
            Step("rope walk", "caliper the rope so the file has a second point"),
            Step("evac drill", "run the annual, or the machine does not carry the public"),
            Step("crew card", "put this year's cards in the file"),
            Step("crew gap", "check every post can be filled before the first weekend"),
        ),
    ),
    Sequence(
        name="a day on the hill",
        when="every operating day",
        steps=(
            Step("day start", "walk the sheet and open the book"),
            Step("report morning", "print the seven o'clock page"),
            Step("day stop", "when it stops, and say why"),
            Step("day restart", "when it runs again"),
            Step("day meter", "read the hour meter and the counter at the end of it"),
            Step("day show", "read the book back before signing off"),
        ),
    ),
    Sequence(
        name="something went wrong",
        when="the same afternoon, not the next morning",
        steps=(
            Step("day stop", "stop it and write it up"),
            Step("board record", "put it on the register and read the filing deadline"),
            Step("upkeep record", "write up the walk that found the cause"),
            Step("upkeep raise", "raise the order off the defect"),
            Step("board file", "file it before the deadline the record printed"),
            Step("upkeep close", "close the order when the work is done"),
        ),
    ),
    Sequence(
        name="closing a season",
        when="May, before the board meets",
        steps=(
            Step("report season", "add up every day that was written up"),
            Step("rope forecast", "see which ropes want ordering before next winter"),
            Step("lift drift", "see which towers have moved and how fast"),
            Step("report packet", "print the annual packet for the state"),
            Step("report export", "hand the numbers to finance as a spreadsheet"),
        ),
    ),
)


def sequence(name: str) -> Sequence | None:
    """Any word of the name will do, because nobody types a day on the hill."""
    wanted = name.lower().strip()
    if not wanted:
        return None
    for one in SEQUENCES:
        if wanted in one.name:
            return one
    return None


def render(wanted: tuple[Sequence, ...]) -> Report:
    page = Report()
    page.heading("what the office does, in order")
    for one in wanted:
        page.section(one.name)
        page.line(f"  {one.when}")
        page.blank()
        for number, step in enumerate(one.steps, start=1):
            page.line(f"  {number}. sheaveline {step.command}")
            page.line(f"     {step.why}")
    return page


@cli.command()
@click.argument("name", required=False)
@click.pass_obj
@handles_refusals
def guide(console: Console, name: str | None) -> None:
    """The order the office does things in, which help cannot tell you.

    With no argument it prints all of them. With one it prints the sequence whose
    name contains that word, so guide day is enough for a day on the hill.
    """
    if name is None:
        console.page(render(SEQUENCES).lines())
        console.say("")
        console.say(f"{len(SEQUENCES)} sequences. Name one to print just that.")
        return
    found = sequence(name)
    if found is None:
        console.say(f"there is no sequence called {name}")
        console.say("try: " + ", ".join(one.name for one in SEQUENCES))
        raise SystemExit(1)
    console.page(render((found,)).lines())
