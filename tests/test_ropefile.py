from datetime import date

from hill import john_paul
from sheaveline.measures.length import Length
from sheaveline.reports.forms import PAGE_WIDTH
from sheaveline.reports.ropefile import RopeFilePage
from sheaveline.ropework.breaks import BreakObservation, BreakSurvey
from sheaveline.ropework.mrt import Indication, MagneticTest
from sheaveline.ropework.splice import Splice, SpliceRegister
from sheaveline.ropework.wear import DiameterReading, WearSurvey

ROPE = john_paul().rope
ON = date(2025, 1, 15)


def wear(taken=ON, inches="1.376"):
    return WearSurvey.of(
        ROPE,
        taken,
        "R Halvorsen",
        [
            DiameterReading(
                Length.from_feet(1200),
                Length.from_inches(inches),
                Length.from_inches(inches),
            )
        ],
    )


def breaks(taken=ON, count=3):
    return BreakSurvey.of(
        ROPE,
        taken,
        "R Halvorsen",
        [BreakObservation(Length.from_feet(1200), count, count + 2)],
    )


def magnetic(taken=ON, loss="2.1"):
    return MagneticTest.of(
        ROPE,
        taken,
        "Wire Rope Diagnostics",
        "LMA-250",
        [Indication.loss(Length.from_feet(1800), loss)],
    )


def splices():
    long_splice = ROPE.diameter.scaled(1200)
    return SpliceRegister.of(
        ROPE,
        [
            Splice(Length.from_feet(0), date(2019, 10, 4), "B Kettner", 5, long_splice, True),
            Splice(Length.from_feet(4000), date(2022, 6, 1), "B Kettner", 5, long_splice),
        ],
    )


def page(**kwargs):
    fields = {"rope": ROPE, "on": ON, "machine": "JP John Paul"}
    fields.update(kwargs)
    return RopeFilePage.of(**fields)


def test_the_header_carries_the_mill_details():
    text = page().text()
    assert "ROPE FILE FAT-2019-04412" in text
    assert "construction             6x25 filler wire" in text
    assert 'nominal diameter         1.378"' in text
    assert 'condemning diameter      1.282"' in text
    assert "breaking strength        196,000 lb" in text


def test_the_age_is_worked_out_against_the_day_it_is_printed():
    assert "seasons in service       5" in page().text()
    assert "seasons in service       6" in page(on=date(2026, 1, 15)).text()


def test_a_rope_with_nothing_against_it_says_so():
    text = page().text()
    assert "nothing recorded against it" in text
    assert "standing                 serviceable" in text
    assert "may run                  yes" in text


def test_the_verdict_reads_the_most_recent_of_each_kind():
    text = page(wear=[wear(date(2024, 12, 1)), wear(ON, inches="1.270")]).text()
    assert "standing                 condemned" in text
    assert "may run                  no" in text


def test_an_older_walk_does_not_condemn_a_rope_that_has_been_walked_since():
    text = page(wear=[wear(date(2024, 12, 1), inches="1.270"), wear(ON)]).text()
    assert "standing                 serviceable" in text


def test_the_caliper_walks_are_listed_in_the_order_they_were_walked():
    text = page(wear=[wear(ON), wear(date(2024, 12, 1))]).text()
    assert "caliper walks" in text
    section = text[text.index("caliper walks") :]
    assert section.index("2024-12-01") < section.index("2025-01-15")


def test_each_walk_carries_its_own_verdict():
    text = page(wear=[wear(ON, inches="1.270")]).text()
    assert "condemned" in text
    assert "R Halvorsen" in text


def test_the_break_counts_get_their_own_section():
    text = page(breaks=[breaks(count=4)]).text()
    assert "broken wire counts" in text
    assert "monitor" in text


def test_the_magnetic_tests_get_their_own_section():
    text = page(tests=[magnetic(loss="7.4")]).text()
    assert "magnetic tests" in text
    assert "Wire Rope Diagnostics" in text
    assert "7.4%" in text


def test_the_splices_are_listed_with_which_kind_they_are():
    text = page(splices=splices()).text()
    assert "splices" in text
    assert "mill" in text
    assert "field" in text
    assert "B Kettner" in text


def test_a_section_with_nothing_in_it_is_not_printed():
    text = page().text()
    for heading in ("caliper walks", "broken wire counts", "magnetic tests"):
        assert heading not in text


def test_the_page_counts_how_many_times_the_rope_was_looked_at():
    held = page(wear=[wear(), wear(date(2024, 12, 1))], breaks=[breaks()], tests=[magnetic()])
    assert held.times_looked_at() == 4
    assert "looked at 4 times" in held.text()


def test_a_failing_splice_register_reaches_the_verdict():
    long_splice = ROPE.diameter.scaled(1200)
    close = SpliceRegister.of(
        ROPE,
        [
            Splice(Length.from_feet(0), date(2019, 10, 4), "B Kettner", 5, long_splice, True),
            Splice(Length.from_feet(200), date(2022, 6, 1), "B Kettner", 5, long_splice),
        ],
    )
    text = page(splices=close).text()
    assert "standing                 condemned" in text
    assert "splices: splice register fails" in text


def test_the_whole_page_fits_eighty_columns():
    held = page(
        wear=[wear(), wear(date(2024, 12, 1))],
        breaks=[breaks()],
        tests=[magnetic()],
        splices=splices(),
    )
    assert all(len(one) <= PAGE_WIDTH for one in held.render().lines())


def test_the_machine_the_rope_is_on_is_named():
    assert "on machine               JP John Paul" in page().text()
