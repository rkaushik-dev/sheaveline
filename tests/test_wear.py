from datetime import date
from decimal import Decimal

import pytest

from hill import john_paul
from sheaveline.measures.length import Length
from sheaveline.ropework.wear import (
    DiameterReading,
    LayReading,
    RopeworkError,
    WearSurvey,
)

ROPE = john_paul().rope
NOMINAL = ROPE.diameter


def reading(station_feet, inches, second=None):
    return DiameterReading(
        station=Length.from_feet(station_feet),
        across_crowns=Length.from_inches(inches),
        across_valleys=Length.from_inches(second or inches),
    )


def survey(*readings, lays=(), taken=date(2025, 1, 15)):
    return WearSurvey.of(ROPE, taken, "R Halvorsen", readings or [reading(0, "1.378")], lays)


def test_a_reading_averages_the_two_axes():
    assert reading(0, "1.370", "1.360").mean() == Length.from_inches("1.365")
    assert reading(0, "1.370", "1.360").ovality() == Length.from_inches("0.010")


def test_a_round_rope_has_no_ovality():
    assert reading(0, "1.378").ovality().is_zero()


def test_a_reading_checks_its_own_numbers():
    with pytest.raises(RopeworkError):
        DiameterReading(Length.from_feet(-1), NOMINAL, NOMINAL)
    with pytest.raises(RopeworkError):
        DiameterReading(Length.from_feet(0), Length.from_inches(0), NOMINAL)


def test_a_reading_prints_the_way_the_sheet_reads():
    assert str(reading(1200, "1.352")) == '1.352" at 1200 ft'


def test_a_lay_reading_reports_how_far_it_has_opened_out():
    nominal = ROPE.spec.lay_length()
    stretched = LayReading(Length.from_feet(0), nominal.scaled("1.03"))
    assert stretched.stretch_against(nominal).quantize(Decimal("0.001")) == Decimal("0.030")


def test_a_lay_reading_has_to_be_a_length():
    with pytest.raises(RopeworkError):
        LayReading(Length.from_feet(0), Length.from_inches(0))


def test_a_survey_is_signed_and_has_readings():
    with pytest.raises(RopeworkError):
        WearSurvey.of(ROPE, date(2025, 1, 15), "  ", [reading(0, "1.378")])
    with pytest.raises(RopeworkError):
        WearSurvey.of(ROPE, date(2025, 1, 15), "R Halvorsen", [])


def test_a_survey_cannot_predate_the_rope():
    with pytest.raises(RopeworkError):
        WearSurvey.of(ROPE, date(2019, 1, 1), "R Halvorsen", [reading(0, "1.378")])


def test_a_reading_past_the_end_of_the_rope_is_refused():
    with pytest.raises(RopeworkError):
        survey(reading(12000, "1.378"))


def test_the_smallest_reading_is_the_one_that_matters():
    walked = survey(
        reading(0, "1.372"),
        reading(1200, "1.351"),
        reading(2400, "1.366"),
    )
    assert walked.smallest().station == Length.from_feet(1200)
    assert walked.percent_of_nominal(walked.smallest()).quantize(Decimal("0.1")) == Decimal("98.0")


def test_the_worst_ovality_is_found_separately():
    walked = survey(
        reading(0, "1.372", "1.370"),
        reading(1200, "1.360", "1.300"),
    )
    assert walked.worst_ovality().station == Length.from_feet(1200)


def test_readings_below_the_condemning_diameter_are_listed():
    walked = survey(
        reading(0, "1.372"),
        reading(1200, "1.270"),
        reading(2400, "1.240"),
    )
    below = walked.below_condemning()
    assert [r.station.feet for r in below] == [1200, 2400]


def test_a_survey_with_no_lay_readings_reports_no_stretch():
    assert survey(reading(0, "1.376")).worst_lay_stretch() == 0


def test_the_stations_walked_come_back_in_the_order_recorded():
    walked = survey(reading(0, "1.372"), reading(1200, "1.370"), reading(2400, "1.368"))
    assert [s.feet for s in walked.stations()] == [0, 1200, 2400]
