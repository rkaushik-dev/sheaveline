"""The whole line walked at once, span by span and tower by tower.

The two checks before this one answer about a piece of the machine. This one
answers the question the mountain manager actually asks, which is whether the
line is fit to run today with this much snow on the ground and this much tension
in the rope. That means walking every span for clearance and every tower for
what the rope is doing to it, and then deciding what the worst of it means.

Three outcomes, because two is not enough. A line can be fine. It can be
runnable with something written on the daily sheet, such as a span the groomer
has to stay out of. Or it can be a line that does not turn until something is
done about it.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

from sheaveline.line.catenary import SagModel
from sheaveline.line.clearance import ClearanceCheck, ClearanceReading, GroundUseMap
from sheaveline.line.deviation import TowerLoading
from sheaveline.measures.length import Length
from sheaveline.plant.lift import Lift
from sheaveline.plant.span import Span

SAMPLE_STEP = Length.from_feet(25)


class Severity(Enum):
    """How much a finding costs."""

    NOTE = "note"
    RESTRICT = "restrict"
    STOP = "stop"

    @property
    def stops_the_lift(self) -> bool:
        return self is Severity.STOP


class GeometryVerdict(Enum):
    """What the walk says about the line."""

    FIT = "fit"
    RESTRICTED = "restricted"
    UNFIT = "unfit"

    @property
    def may_run(self) -> bool:
        return self is not GeometryVerdict.UNFIT


@dataclass(frozen=True)
class Finding:
    """One thing wrong, where it is and what it costs."""

    where: str
    severity: Severity
    detail: str

    def __str__(self) -> str:
        return f"{self.where}: {self.detail}"


@dataclass(frozen=True)
class LineGeometry:
    """One lift, one set of conditions, and the walk that follows from them."""

    lift: Lift
    sag: SagModel
    snow: Length
    uses: GroundUseMap = GroundUseMap()

    def spans(self) -> tuple[Span, ...]:
        return tuple(self.lift.spans())

    def carrier_drop(self) -> Length:
        """Hanger plus the depth of the carrier under it, for the tightest carrier."""
        return max(carrier.hanger for carrier in self.lift.carriers.on_line())

    def clearance_check(self, span: Span) -> ClearanceCheck:
        return ClearanceCheck(
            span=span,
            ground=self.lift.ground,
            sag=self.sag,
            carrier_drop=self.carrier_drop(),
            snow=self.snow,
            uses=self.uses,
        )

    def tightest_clearances(self, step: Length = SAMPLE_STEP) -> tuple[ClearanceReading, ...]:
        return tuple(self.clearance_check(span).tightest(step) for span in self.spans())

    def worst_clearance(self, step: Length = SAMPLE_STEP) -> ClearanceReading:
        return min(self.tightest_clearances(step), key=lambda reading: reading.margin.mils)

    def tower_loadings(self) -> tuple[TowerLoading, ...]:
        spans = self.spans()
        loadings = []
        for position, tower in enumerate(self.lift.towers):
            loadings.append(TowerLoading(tower, spans[position], spans[position + 1], self.sag))
        return tuple(loadings)

    def findings(self, step: Length = SAMPLE_STEP) -> tuple[Finding, ...]:
        found = []
        for span in self.spans():
            check = self.clearance_check(span)
            worst = check.tightest(step)
            if not worst.passes():
                found.append(
                    Finding(
                        where=f"span {span.index}",
                        severity=Severity.STOP,
                        detail=f"clearance {worst.available.feet:.1f} ft over "
                        f"{worst.use.value}, {worst.required.feet:.0f} ft wanted",
                    )
                )
            elif worst.margin < Length.from_feet(3):
                found.append(
                    Finding(
                        where=f"span {span.index}",
                        severity=Severity.RESTRICT,
                        detail=f"only {worst.margin.feet:.1f} ft of margin over {worst.use.value}",
                    )
                )
        for loading in self.tower_loadings():
            if loading.lifts_off():
                found.append(
                    Finding(
                        where=str(loading.tower.ref),
                        severity=Severity.STOP,
                        detail="hold down carries less than the minimum seating force",
                    )
                )
            elif not loading.agrees_with_schedule():
                found.append(
                    Finding(
                        where=str(loading.tower.ref),
                        severity=Severity.RESTRICT,
                        detail=f"rope bends {loading.deviation().format_degrees()} against a "
                        f"{'/'.join(sorted(kind.value for kind in loading.tower.kinds()))} tower",
                    )
                )
            elif not loading.matches_design():
                found.append(
                    Finding(
                        where=str(loading.tower.ref),
                        severity=Severity.NOTE,
                        detail=f"deviation off design by "
                        f"{loading.deviation_error().format_degrees()}",
                    )
                )
        return tuple(found)

    def verdict(self, step: Length = SAMPLE_STEP) -> GeometryVerdict:
        severities = {finding.severity for finding in self.findings(step)}
        if Severity.STOP in severities:
            return GeometryVerdict.UNFIT
        if Severity.RESTRICT in severities:
            return GeometryVerdict.RESTRICTED
        return GeometryVerdict.FIT

    def with_snow(self, snow: Length) -> LineGeometry:
        return LineGeometry(self.lift, self.sag, snow, self.uses)

    def with_tension(self, sag: SagModel) -> LineGeometry:
        return LineGeometry(self.lift, sag, self.snow, self.uses)
