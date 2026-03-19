"""Loading a machine from its drawing and handing the drawing back.

The load is deliberately all or nothing. A drawing with a bad tower on it puts
no machine on file at all, because a half loaded lift is worse than none: the
next person sees a code in the list and believes the rest of it.

The check command exists because the load is all or nothing. Somebody editing a
drawing wants to know it reads before they run the load against the office
database, and reading it costs nothing.
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import click

from sheaveline.cli.main import Console, handles_refusals
from sheaveline.cli.plant import lift
from sheaveline.errors import SheavelineError
from sheaveline.plant.blueprint import read_lift, write_lift
from sheaveline.plant.identity import LiftCode


class DrawingError(SheavelineError):
    """A drawing file could not be read."""


def read_document(path: Path) -> dict[str, Any]:
    """Read the file and say which file it was if it does not read."""
    try:
        text = path.read_text(encoding="utf-8")
    except OSError as missing:
        raise DrawingError(f"cannot read {path}") from missing
    try:
        document = json.loads(text)
    except json.JSONDecodeError as bad:
        raise DrawingError(f"{path} is not json: {bad.msg} at line {bad.lineno}") from bad
    if not isinstance(document, dict):
        raise DrawingError(f"{path} holds a {type(document).__name__}, not a drawing")
    return document


@lift.command("load")
@click.argument("drawing", type=click.Path(exists=True, dir_okay=False, path_type=Path))
@click.option("--replace", is_flag=True, help="Take the old machine off first.")
@click.pass_obj
@handles_refusals
def load_lift(console: Console, drawing: Path, replace: bool) -> None:
    """Put a machine on file from its drawing. Nothing is written if it does not read."""
    machine = read_lift(read_document(drawing))
    with console.work() as work:
        if replace and work.lifts.exists(machine.code):
            work.lifts.remove(machine.code)
            work.flush()
        work.lifts.add(machine)
        work.commit()
    console.say(f"{machine.code} {machine.name} loaded")
    console.say(f"{len(machine.towers)} towers, {machine.carriers_on_line()} carriers")


@lift.command("export")
@click.argument("code")
@click.option(
    "--output",
    type=click.Path(dir_okay=False, path_type=Path),
    help="Write to this file instead of the page.",
)
@click.pass_obj
@handles_refusals
def export_lift(console: Console, code: str, output: Path | None) -> None:
    """Write a machine back out as the drawing it would be read from."""
    with console.work() as work:
        machine = work.lifts.get(LiftCode.parse(code))
    text = json.dumps(write_lift(machine), indent=2) + "\n"
    if output is None:
        console.say(text.rstrip())
        return
    output.write_text(text, encoding="utf-8")
    console.say(f"{machine.code} written to {output}")


@lift.command("check")
@click.argument("drawing", type=click.Path(exists=True, dir_okay=False, path_type=Path))
@click.pass_obj
@handles_refusals
def check_drawing(console: Console, drawing: Path) -> None:
    """Read a drawing and say whether it makes a machine, without writing anything."""
    machine = read_lift(read_document(drawing))
    console.say(f"{machine.code} {machine.name} reads")
    console.say(f"class            {machine.lift_class.value}")
    console.say(f"towers           {len(machine.towers)}")
    console.say(f"carriers         {machine.carriers_on_line()}")
    console.say(f"slope length     {machine.slope_length().feet:.0f} ft")
    agrees = "yes" if machine.rope_length_agrees() else "no"
    console.say(f"rope length fits {agrees}")
