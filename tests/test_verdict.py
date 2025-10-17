from datetime import date

import pytest

from hill import john_paul
from sheaveline.measures.length import Length
from sheaveline.measures.load import Load
from sheaveline.ropework.breaks import BreakObservation, BreakSurvey
from sheaveline.ropework.mrt import Indication, MagneticTest, RetestSchedule
from sheaveline.ropework.splice import Splice, SpliceRegister
from sheaveline.ropework.tension import TensionKind, TensionSystem
from sheaveline.ropework.verdict import (
    WEAR_RULES,
    RopeFile,
    assess_breaks,
    assess_test,
    assess_wear,
    wear_findings,
)
from sheaveline.ropework.wear import (
    DiameterReading,
    LayReading,
    RopeworkError,
    WearSurvey,
    WearVerdict,
)

ROPE = john_paul().rope
TAKEN = date(2025, 1, 15)


def reading(station_feet, inches, second=None):
    return DiameterReading(
        station=Length.from_feet(station_feet),
        across_crowns=Length.from_inches(inches),
        across_valleys=Length.from_inches(second or inches),
    )


def wear(*readings, lays=()):
    return WearSurvey.of(ROPE, TAKEN, "R Halvorsen", readings or [reading(0, "1.376")], lays)


def breaks(*observations):
    return BreakSurvey.of(ROPE, TAKEN, "R Halvorsen", observations)


def seen(station_feet, one, six=None, valley=0):
    return BreakObservation(
        station=Length.from_feet(station_feet),
        in_one_lay=one,
        in_six_lays=six if six is not None else one,
        valley_breaks=valley,
    )


def magnetic(*indications):
    return MagneticTest.of(ROPE, TAKEN, "Wire Rope Diagnostics", "LMA-250", indications)


def splices(*entries):
    return SpliceRegister.of(
        ROPE,
        entries
        or [
            Splice(
                station=Length.from_feet(0),
                made=date(2019, 10, 4),
                made_by="B Kettner",
                tucks=5,
                length=ROPE.diameter.scaled(1200),
                mill=True,
            )
        ],
    )


def tension(carriage_feet=6):
    return TensionSystem(
        kind=TensionKind.HYDRAULIC,
        nominal=Load.from_pounds(34500),
        travel=Length.from_feet(16),
        carriage=Length.from_feet(carriage_feet),
    )


def test_the_wear_rules_are_rows_rather_than_a_chain():
    assert len(WEAR_RULES) == 5
    names = {rule.name for rule in WEAR_RULES}
    assert names == {"diameter", "lay length", "ovality"}


def test_a_good_rope_fires_no_rule():
    assert wear_findings(wear()) == ()
    assert assess_wear(wear()) is WearVerdict.SERVICEABLE


def test_a_thin_rope_fires_the_diameter_rule_and_says_the_reading():
    walked = wear(reading(0, "1.376"), reading(1200, "1.270"))
    found = wear_findings(walked)
    assert found
    assert found[0].source == "diameter"
    assert "% of nominal" in found[0].detail
    assert assess_wear(walked) is WearVerdict.CONDEMNED


def test_a_rope_between_the_two_diameter_rows_only_gets_watched():
    walked = wear(reading(0, "1.376"), reading(1200, "1.310"))
    assert assess_wear(walked) is WearVerdict.MONITOR


def test_an_oval_rope_fires_the_ovality_row():
    walked = wear(reading(0, "1.378"), reading(600, "1.400", "1.320"))
    assert {found.source for found in wear_findings(walked)} == {"ovality"}
    assert assess_wear(walked) is WearVerdict.MONITOR


def test_a_stretched_lay_fires_at_two_rows_depending_how_far():
    nominal = ROPE.spec.lay_length()
    watched = wear(lays=(LayReading(Length.from_feet(600), nominal.scaled("1.03")),))
    assert assess_wear(watched) is WearVerdict.MONITOR
    gone = wear(lays=(LayReading(Length.from_feet(600), nominal.scaled("1.08")),))
    assert assess_wear(gone) is WearVerdict.CONDEMNED


def test_broken_wires_are_assessed_from_the_counts():
    assert assess_breaks(breaks()) is WearVerdict.SERVICEABLE
    assert assess_breaks(breaks(seen(1200, 4, six=7))) is WearVerdict.MONITOR
    assert assess_breaks(breaks(seen(1200, 7, six=9))) is WearVerdict.CONDEMNED


def test_the_magnetic_trace_is_assessed_from_the_indications():
    assert assess_test(magnetic()) is WearVerdict.SERVICEABLE
    watched = magnetic(Indication.loss(Length.from_feet(1800), "7.4"))
    assert assess_test(watched) is WearVerdict.MONITOR
    gone = magnetic(Indication.loss(Length.from_feet(1800), "10.4"))
    assert assess_test(gone) is WearVerdict.CONDEMNED


def test_a_local_fault_and_a_loss_can_fire_together():
    both = magnetic(
        Indication.loss(Length.from_feet(1800), "10.4"),
        Indication.fault(Length.from_feet(2400), 3),
    )
    assert assess_test(both) is WearVerdict.CONDEMNED


def test_an_empty_rope_file_says_nothing_against_the_rope():
    empty = RopeFile()
    assert empty.findings() == ()
    assert empty.verdict() is WearVerdict.SERVICEABLE
    assert empty.may_run()
    with pytest.raises(RopeworkError):
        empty.worst()


def test_a_rope_file_reads_every_source_it_has():
    held = RopeFile(
        wear=wear(reading(0, "1.376"), reading(1200, "1.310")),
        breaks=breaks(seen(1200, 4, six=7)),
        magnetic=magnetic(Indication.loss(Length.from_feet(1800), "7.4")),
    )
    assert held.sources() == {"diameter", "broken wires", "magnetic test"}
    assert held.verdict() is WearVerdict.MONITOR


def test_the_worst_source_wins_the_file():
    held = RopeFile(
        wear=wear(reading(0, "1.376"), reading(1200, "1.310")),
        breaks=breaks(seen(1200, 7, six=9)),
    )
    assert held.verdict() is WearVerdict.CONDEMNED
    assert not held.may_run()
    assert held.worst().verdict is WearVerdict.CONDEMNED


def test_a_failing_splice_register_condemns_the_rope():
    close = splices(
        Splice(
            Length.from_feet(0), date(2019, 10, 4), "B Kettner", 5, ROPE.diameter.scaled(1200), True
        ),
        Splice(Length.from_feet(200), date(2021, 6, 1), "B Kettner", 5, ROPE.diameter.scaled(1200)),
    )
    held = RopeFile(splices=close)
    assert held.verdict() is WearVerdict.CONDEMNED
    assert "splices" in held.sources()


def test_a_carriage_with_no_reserve_puts_the_rope_on_watch():
    held = RopeFile(tension=tension(carriage_feet=15))
    assert held.verdict() is WearVerdict.MONITOR
    assert "tension" in held.sources()
    assert RopeFile(tension=tension(carriage_feet=6)).findings() == ()


def test_an_overdue_magnetic_test_condemns_the_rope_on_the_day_it_is_asked():
    schedule = RetestSchedule(ROPE, last_test=date(2022, 10, 4))
    held = RopeFile(retest=schedule)
    assert held.verdict(date(2025, 1, 15)) is WearVerdict.CONDEMNED
    assert held.verdict(date(2024, 6, 1)) is WearVerdict.SERVICEABLE
    assert held.verdict() is WearVerdict.SERVICEABLE


def test_a_finding_prints_its_source_and_its_reading():
    walked = wear(reading(0, "1.376"), reading(1200, "1.270"))
    assert str(wear_findings(walked)[0]).startswith("diameter: ")
