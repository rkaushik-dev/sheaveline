import pytest

from sheaveline.reports.forms import (
    PAGE_WIDTH,
    Align,
    Column,
    Report,
    ReportError,
    Table,
    centre,
    field,
    rule,
    wrap,
)


def test_a_column_places_its_text_the_way_it_says():
    assert Column("code", 6).cell("JP") == "JP    "
    assert Column("count", 6, Align.RIGHT).cell(84) == "    84"
    assert Column("name", 7, Align.CENTRE).cell("abc") == "  abc  "


def test_nothing_becomes_an_empty_cell_rather_than_the_word_none():
    assert Column("code", 4).cell(None) == "    "


def test_a_cell_too_wide_is_truncated_rather_than_wrapped():
    assert Column("name", 6).cell("John Paul") == "John …"
    assert Column("x", 1).cell("abc") == "a"


def test_a_column_needs_a_width_its_heading_fits_in():
    with pytest.raises(ReportError):
        Column("code", 0)
    with pytest.raises(ReportError):
        Column("a rather long heading", 4)


def test_a_table_lines_up_its_columns():
    table = Table.of(
        [Column("code", 4), Column("carriers", 8, Align.RIGHT)],
        [("JP", 84), ("SB", 46)],
    )
    lines = table.lines()
    assert lines[0] == "code carriers"
    assert lines[1] == "---- --------"
    assert lines[2] == "JP         84"
    assert lines[3] == "SB         46"
    assert len({len(one) for one in lines}) == 1


def test_a_table_reports_its_own_width():
    table = Table.of([Column("a", 4), Column("b", 5)])
    assert table.width() == 10


def test_a_table_wider_than_the_page_is_refused():
    with pytest.raises(ReportError):
        Table.of([Column("a", 60), Column("b", 40)])


def test_a_table_needs_columns_and_rows_that_fit_them():
    with pytest.raises(ReportError):
        Table.of([])
    with pytest.raises(ReportError):
        Table.of([Column("a", 4)], [("one", "two")])


def test_a_row_can_be_added_to_a_table():
    table = Table.of([Column("code", 4)]).with_row(["JP"])
    assert len(table.rows) == 1
    assert table.lines()[-1] == "JP  "


def test_prose_breaks_at_spaces_and_nowhere_else():
    lines = wrap("the rope is not on the chord so work out where it hangs", 20)
    assert all(len(one) <= 20 for one in lines)
    assert " ".join(lines) == "the rope is not on the chord so work out where it hangs"


def test_a_word_longer_than_the_line_is_cut():
    assert wrap("supercalifragilistic", 8) == ["supercal"]


def test_wrapping_nothing_gives_one_empty_line():
    assert wrap("   ", 20) == [""]
    with pytest.raises(ReportError):
        wrap("anything", 0)


def test_the_page_furniture():
    assert rule("=", 5) == "====="
    assert centre("JP", 6) == "  JP"
    assert field("lift", "John Paul", 10) == "lift       John Paul"


def test_a_report_builds_up_a_page():
    page = Report()
    page.heading("morning report")
    page.field("lift", "John Paul")
    page.blank()
    page.line("clear to open")
    lines = page.lines()
    assert lines[0].strip() == "MORNING REPORT"
    assert lines[1] == "=" * PAGE_WIDTH
    assert lines[2].startswith("lift")
    assert lines[3] == ""
    assert lines[4] == "clear to open"
    assert len(page) == 5


def test_a_report_refuses_a_line_that_does_not_fit():
    page = Report(width=20)
    with pytest.raises(ReportError):
        page.line("a" * 21)


def test_a_page_narrower_than_twenty_columns_is_not_a_page():
    with pytest.raises(ReportError):
        Report(width=10)


def test_a_section_underlines_only_its_own_heading():
    page = Report()
    page.section("rope file")
    lines = page.lines()
    assert lines[0] == ""
    assert lines[1] == "rope file"
    assert lines[2] == "-" * len("rope file")


def test_prose_in_a_report_is_wrapped_to_the_page():
    page = Report(width=30)
    page.prose("the rope is not on the chord so work out where it actually hangs")
    assert all(len(one) <= 30 for one in page.lines())
    assert len(page) > 1


def test_a_table_can_be_printed_into_a_report():
    page = Report()
    page.table(Table.of([Column("code", 4)], [("JP",), ("SB",)]))
    assert page.lines()[-1] == "SB  "


def test_the_text_of_a_report_ends_with_a_newline():
    page = Report()
    page.line("one").line("two")
    assert page.text() == "one\ntwo\n"


def test_nothing_in_a_report_is_ever_wider_than_the_page():
    page = Report()
    page.heading("season summary")
    page.section("the machines")
    page.table(
        Table.of(
            [Column("code", 6), Column("name", 24), Column("carriers", 8, Align.RIGHT)],
            [("JP", "John Paul", 84)],
        )
    )
    page.prose("a" * 200)
    assert all(len(one) <= PAGE_WIDTH for one in page.lines())


def test_building_a_report_hands_the_report_back_so_calls_chain():
    page = Report().line("one").blank().line("two")
    assert len(page) == 3
