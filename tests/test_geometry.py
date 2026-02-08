from hill import john_paul
from sheaveline.line.catenary import SagModel
from sheaveline.line.clearance import GroundUse, GroundUseMap, UseSegment
from sheaveline.line.geometry import (
    Finding,
    GeometryVerdict,
    LineGeometry,
    Severity,
)
from sheaveline.measures.length import Length
from sheaveline.measures.load import Load, LoadPerFoot

LIFT = john_paul()
SAG = SagModel(LoadPerFoot.from_pounds_per_foot("6.20"), Load.from_pounds(34500))


def geometry(snow_feet=4, uses=None, tension=34500):
    return LineGeometry(
        lift=LIFT,
        sag=SagModel(LoadPerFoot.from_pounds_per_foot("6.20"), Load.from_pounds(tension)),
        snow=Length.from_feet(snow_feet),
        uses=uses or GroundUseMap(),
    )


def segment(start, end, use):
    return UseSegment(Length.from_feet(start), Length.from_feet(end), use)


def test_the_walk_covers_every_span_and_every_tower():
    walk = geometry()
    assert len(walk.spans()) == 8
    assert len(walk.tightest_clearances()) == 8
    assert len(walk.tower_loadings()) == 7


def test_the_carrier_drop_is_the_longest_hanger_on_the_line():
    assert geometry().carrier_drop() == Length.from_feet_inches(6, 4)


def test_each_tower_loading_sits_between_the_right_two_spans():
    for position, loading in enumerate(geometry().tower_loadings()):
        assert loading.tower.number == position + 1
        assert loading.below.upper.station == loading.tower.station
        assert loading.above.lower.station == loading.tower.station


def test_the_worst_clearance_is_the_least_margin_across_the_line():
    walk = geometry()
    worst = walk.worst_clearance()
    assert worst.margin <= min(reading.margin for reading in walk.tightest_clearances())


def test_a_line_with_room_everywhere_is_fit():
    walk = geometry(snow_feet=2)
    assert walk.verdict() is GeometryVerdict.FIT
    assert walk.verdict().may_run
    assert walk.findings() == ()


def test_deep_enough_snow_takes_the_line_out():
    walk = geometry(snow_feet=40)
    assert walk.verdict() is GeometryVerdict.UNFIT
    assert not walk.verdict().may_run
    assert any(finding.severity is Severity.STOP for finding in walk.findings())


def test_water_under_a_span_can_restrict_a_line_that_is_otherwise_fine():
    walk = geometry(snow_feet=2)
    assert walk.verdict() is GeometryVerdict.FIT
    over_water = geometry(
        snow_feet=18,
        uses=GroundUseMap.of([segment(1500, 1700, GroundUse.WATER)]),
    )
    assert over_water.verdict() is not GeometryVerdict.FIT


def test_dropping_the_tension_lets_the_rope_down_onto_the_ground():
    tight = geometry(snow_feet=6, tension=40000)
    slack = geometry(snow_feet=6, tension=9000)
    assert slack.worst_clearance().available < tight.worst_clearance().available


def test_findings_name_where_they_are():
    walk = geometry(snow_feet=40)
    for finding in walk.findings():
        assert finding.where
        assert finding.detail
        assert str(finding).startswith(finding.where)


def test_a_stop_finding_beats_a_restrict_one():
    assert Severity.STOP.stops_the_lift
    assert not Severity.RESTRICT.stops_the_lift
    assert not Severity.NOTE.stops_the_lift


def test_a_finding_prints_where_and_what():
    finding = Finding("span 3", Severity.STOP, "clearance 4.0 ft over open ground")
    assert str(finding) == "span 3: clearance 4.0 ft over open ground"


def test_changing_the_snow_makes_a_new_walk_and_can_change_the_verdict():
    dry = geometry(snow_feet=2)
    deep = dry.with_snow(Length.from_feet(40))
    assert dry.verdict() is GeometryVerdict.FIT
    assert deep.verdict() is GeometryVerdict.UNFIT
    assert dry.snow == Length.from_feet(2)


def test_changing_the_tension_makes_a_new_walk():
    slack = geometry().with_tension(
        SagModel(LoadPerFoot.from_pounds_per_foot("6.20"), Load.from_pounds(12000))
    )
    assert slack.sag.tension == Load.from_pounds(12000)
    assert slack.lift is LIFT


def test_a_finer_step_never_reports_fewer_problems():
    walk = geometry(snow_feet=30)
    coarse = len(walk.findings(Length.from_feet(200)))
    fine = len(walk.findings(Length.from_feet(25)))
    assert fine >= coarse


def test_the_verdict_reads_the_worst_finding_and_nothing_else():
    walk = geometry(snow_feet=40)
    assert walk.verdict() is GeometryVerdict.UNFIT
    assert any(finding.severity is Severity.STOP for finding in walk.findings())
