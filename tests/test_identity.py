import pytest

from sheaveline.errors import ParseError, RangeError
from sheaveline.plant.identity import (
    FilingNumber,
    LiftCode,
    RopeSerial,
    TowerRef,
)


def test_a_lift_code_is_upper_case():
    assert LiftCode.parse("jp").value == "JP"
    assert str(LiftCode("STRAWBRY")) == "STRAWBRY"


@pytest.mark.parametrize("text", ["", "1A", "toolongforalift", "J P", "J-P", "j"])
def test_a_lift_code_refuses_what_is_not_one(text):
    with pytest.raises(ParseError):
        LiftCode.parse(text)


def test_a_tower_is_a_lift_and_a_number():
    tower = LiftCode("JP").tower(7)
    assert tower.lift == LiftCode("JP")
    assert tower.number == 7
    assert str(tower) == "JP-T7"


@pytest.mark.parametrize("text", ["JP-T7", "JPT7", "jp t7", "JP/T7"])
def test_a_tower_parses_from_the_shapes_a_crew_writes(text):
    assert TowerRef.parse(text) == LiftCode("JP").tower(7)


@pytest.mark.parametrize("text", ["", "JP", "T7", "JP-T0", "JP-T100", "JP-TX"])
def test_a_tower_refuses_what_is_not_one(text):
    with pytest.raises((ParseError, RangeError)):
        TowerRef.parse(text)


def test_a_tower_number_stays_inside_the_line():
    with pytest.raises(RangeError):
        LiftCode("JP").tower(0)
    with pytest.raises(RangeError):
        LiftCode("JP").tower(100)


def test_towers_sort_by_number_not_by_text():
    lift = LiftCode("JP")
    towers = [lift.tower(10), lift.tower(2), lift.tower(1)]
    assert [t.number for t in sorted(towers)] == [1, 2, 10]
    assert lift.tower(2) < lift.tower(10)
    assert lift.tower(10) > lift.tower(2)
    assert lift.tower(2) <= lift.tower(2)
    assert lift.tower(2) >= lift.tower(2)


def test_the_next_tower_up_the_line():
    assert LiftCode("JP").tower(7).next_up() == LiftCode("JP").tower(8)


def test_a_rope_serial_carries_the_mill_and_the_year():
    rope = RopeSerial.parse("FAT-2019-04412")
    assert rope.mill == "FAT"
    assert rope.year == 2019
    assert rope.serial == "04412"
    assert str(rope) == "FAT-2019-04412"


def test_a_rope_serial_is_case_insensitive_on_the_mill():
    assert RopeSerial.parse("fat-2019-04412") == RopeSerial("FAT", 2019, "04412")


@pytest.mark.parametrize(
    "text",
    ["", "FAT-2019", "F-2019-04412", "FATTY-2019-04412", "FAT-19-04412", "FAT-2019-123"],
)
def test_a_rope_serial_refuses_what_is_not_one(text):
    with pytest.raises((ParseError, RangeError)):
        RopeSerial.parse(text)


def test_a_rope_reports_its_age():
    rope = RopeSerial.parse("FAT-2019-04412")
    assert rope.age_in(2025) == 6
    assert rope.age_in(2019) == 0
    with pytest.raises(RangeError):
        rope.age_in(2018)


def test_a_rope_serial_checks_its_own_parts():
    with pytest.raises(RangeError):
        RopeSerial("FAT", 1800, "04412")
    with pytest.raises(ParseError):
        RopeSerial("F4T", 2019, "04412")
    with pytest.raises(ParseError):
        RopeSerial("FAT", 2019, "abcd")


def test_a_filing_number_pads_its_sequence():
    filing = FilingNumber("UT", 2025, 147)
    assert str(filing) == "UT-2025-0147"
    assert FilingNumber.parse("UT-2025-0147") == filing
    assert FilingNumber.parse("ut-2025-0147") == filing


def test_the_first_filing_of_a_year_is_number_one():
    assert FilingNumber.first_of("ut", 2025) == FilingNumber("UT", 2025, 1)
    assert str(FilingNumber.first_of("UT", 2025)) == "UT-2025-0001"


def test_filings_run_without_gaps():
    first = FilingNumber.first_of("UT", 2025)
    second = first.next_in_sequence()
    assert second.sequence == 2
    assert second.follows(first)
    assert not FilingNumber("UT", 2025, 4).follows(first)
    assert not FilingNumber("CO", 2025, 2).follows(first)
    assert FilingNumber("UT", 2024, 2).follows(FilingNumber("UT", 2024, 1)) is not False


def test_a_filing_sequence_has_a_ceiling():
    with pytest.raises(RangeError):
        FilingNumber("UT", 2025, 9999).next_in_sequence()
    with pytest.raises(RangeError):
        FilingNumber("UT", 2025, 0)


@pytest.mark.parametrize("text", ["", "UT-2025", "U-2025-0147", "UT-25-0147", "UT-2025-147"])
def test_a_filing_number_refuses_what_is_not_one(text):
    with pytest.raises((ParseError, RangeError)):
        FilingNumber.parse(text)


def test_filings_sort_in_issue_order():
    numbers = [
        FilingNumber("UT", 2025, 12),
        FilingNumber("UT", 2024, 300),
        FilingNumber("CO", 2025, 1),
    ]
    assert [str(n) for n in sorted(numbers)] == [
        "CO-2025-0001",
        "UT-2024-0300",
        "UT-2025-0012",
    ]
