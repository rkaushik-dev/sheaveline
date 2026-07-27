"""Drills, both ways.

The teams that took part are their own rows rather than a joined string, because
the question the register answers is which teams are short of a drill this
season, and that is a set operation. It is also the question the state asks in
the annual packet, so getting it out of a comma separated column once a year is
not the shape to be in.

The elapsed time goes down as whole seconds. A drill is timed with a stopwatch by
somebody in a harness and the sub second part is noise, but storing it as a float
would still let a rounding error decide whether a machine cleared inside its
allowed time.
"""

from __future__ import annotations

from collections.abc import Sequence

from sheaveline.evac.drill import Drill, DrillKind, DrillRegister
from sheaveline.measures.duration import Duration
from sheaveline.persistence.models.board import DrillRow, DrillTeamRow


def drill_to_row(drill: Drill) -> DrillRow:
    row = DrillRow(
        lift_code=drill.lift,
        on=drill.on,
        kind=drill.kind,
        carriers_worked=drill.carriers_worked,
        elapsed_seconds=drill.elapsed.seconds,
        observer=drill.observer,
    )
    for team in sorted(drill.teams):
        row.teams.append(DrillTeamRow(team=team))
    return row


def drill_from_row(row: DrillRow) -> Drill:
    return Drill.of(
        lift=row.lift_code,
        on=row.on,
        kind=DrillKind(row.kind),
        teams=sorted(one.team for one in row.teams),
        carriers_worked=row.carriers_worked,
        elapsed=Duration.from_seconds(row.elapsed_seconds),
        observer=row.observer,
    )


def register_from_rows(rows: Sequence[DrillRow]) -> DrillRegister:
    return DrillRegister.of([drill_from_row(one) for one in rows])
