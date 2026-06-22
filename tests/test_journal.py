import io
import sys

import pytest

from sheaveline.journal import Journal, Level, field, new_request_id


def book(level=Level.INFO):
    stream = io.StringIO()
    return Journal(stream, level, clock="2025-01-15T07:00:00"), stream


def lines(stream):
    return [one for one in stream.getvalue().splitlines() if one]


def test_a_line_carries_the_stamp_the_level_and_the_event():
    made, stream = book()
    made.info("started")
    assert lines(stream) == ["2025-01-15T07:00:00 info started"]


def test_the_fields_go_on_in_the_order_they_were_given():
    made, stream = book()
    made.info("request", method="GET", path="/lifts", status=200)
    assert lines(stream)[0].endswith("request method=GET path=/lifts status=200")


def test_a_value_with_a_space_in_it_is_quoted():
    assert field("who", "R Skaggs") == 'who="R Skaggs"'
    assert field("who", "Skaggs") == "who=Skaggs"


def test_an_empty_value_is_quoted_so_the_field_does_not_vanish():
    assert field("note", "") == 'note=""'
    assert field("note", None) == 'note=""'


def test_a_value_with_a_quote_in_it_is_escaped():
    assert field("what", 'the "big" one') == r'what="the \"big\" one"'


def test_a_windows_path_with_a_space_keeps_its_backslashes_readable():
    made = field("path", "c:\\Program Files\\office.sqlite3")
    assert made == 'path="c:\\\\Program Files\\\\office.sqlite3"'


def test_a_path_with_no_space_is_left_alone():
    assert field("path", "c:\\office.sqlite3") == "path=c:\\office.sqlite3"


def test_a_level_below_the_bar_writes_nothing():
    made, stream = book(Level.WARN)
    made.debug("noise")
    made.info("noise")
    made.warn("something")
    assert len(lines(stream)) == 1


def test_a_silent_journal_writes_nothing_at_all():
    made = Journal.silent()
    assert not made.wants(Level.ERROR)
    made.error("this goes nowhere")


def test_the_levels_are_ordered_the_way_a_comparison_reads():
    assert Level.DEBUG < Level.INFO < Level.WARN < Level.ERROR < Level.OFF


def test_a_level_is_looked_up_by_name():
    assert Level.named("warn") is Level.WARN
    assert Level.named("  Error  ") is Level.ERROR


def test_a_level_nobody_has():
    with pytest.raises(ValueError, match="no such level"):
        Level.named("chatty")


def test_context_goes_on_every_line_from_then_on():
    made, stream = book()
    about = made.about(request="abc123")
    about.info("one")
    about.info("two", status=200)
    assert all("request=abc123" in one for one in lines(stream))
    assert "status=200" in lines(stream)[1]


def test_context_adds_rather_than_replaces():
    made, stream = book()
    made.about(request="abc").about(lift="JP").info("both")
    assert "request=abc" in lines(stream)[0]
    assert "lift=JP" in lines(stream)[0]


def test_the_journal_context_does_not_leak_back_to_the_one_it_came_from():
    made, stream = book()
    made.about(request="abc").info("one")
    made.info("two")
    assert "request=abc" in lines(stream)[0]
    assert "request=abc" not in lines(stream)[1]


def test_timing_writes_when_the_work_is_done():
    made, stream = book()
    with made.timing("season", label="2024-25"):
        pass
    line = lines(stream)[0]
    assert "outcome=ok" in line
    assert "ms=" in line
    assert "label=2024-25" in line


def test_timing_still_writes_when_the_work_falls_over():
    made, stream = book()
    with pytest.raises(ZeroDivisionError), made.timing("season"):
        raise ZeroDivisionError
    line = lines(stream)[0]
    assert "error" in line
    assert "outcome=failed" in line
    assert "error=ZeroDivisionError" in line


def test_a_request_id_is_short_enough_to_read_out_and_different_every_time():
    made = new_request_id()
    assert len(made) == 12
    assert made != new_request_id()


def test_a_journal_on_stderr_writes_where_it_says_it_does():
    made = Journal.to_stderr(Level.DEBUG)
    assert made.wants(Level.DEBUG)
    assert made.stream is sys.stderr
