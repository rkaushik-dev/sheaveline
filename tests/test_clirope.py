import pytest
from click.testing import CliRunner

from hill import john_paul, strawberry, wildcat
from office import copied
from sheaveline.cli.main import cli
from sheaveline.cli.rope import parse_reading
from sheaveline.errors import ParseError
from sheaveline.measures.length import Length
from sheaveline.reports.forms import PAGE_WIDTH

SERIAL = "FAT-2019-04412"


@pytest.fixture
def runner():
    return CliRunner()


def two_machines_and_a_spare(work):
    work.lifts.add(john_paul())
    work.lifts.add(strawberry())
    work.ropes.add(wildcat().rope)


@pytest.fixture
def database(tmp_path):
    return copied(tmp_path, "ropes", two_machines_and_a_spare)


def run(runner, database, *args, **kwargs):
    return runner.invoke(cli, ["--database", database, *args], **kwargs)


def test_a_reading_reads_in_the_order_it_is_written_on_the_clipboard():
    reading = parse_reading("1200 ft:1.352 in:1.348 in")
    assert reading.station == Length.from_feet(1200)
    assert reading.across_crowns == Length.from_inches("1.352")
    assert reading.across_valleys == Length.from_inches("1.348")


def test_a_reading_with_the_wrong_number_of_parts_is_refused():
    with pytest.raises(ParseError):
        parse_reading("1200 ft:1.352 in")
    with pytest.raises(ParseError):
        parse_reading("1200:1.352:1.348:extra")


def test_the_list_names_every_rope(runner, database):
    reply = run(runner, database, "rope", "list")
    assert reply.exit_code == 0
    assert SERIAL in reply.output
    assert "BRI-2011-01887" in reply.output
    assert "BRI-2016-00294" in reply.output


def test_the_list_carries_the_construction_and_the_age(runner, database):
    reply = run(runner, database, "rope", "list")
    assert "6x25 filler wire" in reply.output
    assert "2019-10-04" in reply.output


def test_the_spares_are_the_ropes_on_no_machine(runner, database):
    reply = run(runner, database, "rope", "list", "--spares")
    assert "BRI-2016-00294" in reply.output
    assert SERIAL not in reply.output


def test_the_file_prints_the_way_it_goes_in_the_folder(runner, database):
    reply = run(runner, database, "rope", "file", SERIAL)
    assert reply.exit_code == 0
    assert f"ROPE FILE {SERIAL}" in reply.output
    assert "on machine               JP John Paul" in reply.output
    assert "nothing recorded against it" in reply.output


def test_a_spare_rope_prints_as_in_the_shop(runner, database):
    reply = run(runner, database, "rope", "file", "BRI-2016-00294")
    assert "on machine               in the shop" in reply.output


def test_a_rope_that_is_not_on_file(runner, database):
    reply = run(runner, database, "rope", "file", "FAT-2019-99999")
    assert reply.exit_code == 1
    assert "there is no rope" in reply.output
    assert "Traceback" not in reply.output


def test_something_that_is_not_a_serial(runner, database):
    reply = run(runner, database, "rope", "file", "nonsense")
    assert reply.exit_code == 1
    assert "a rope serial" in reply.output


def test_a_walk_is_recorded_and_the_verdict_printed(runner, database):
    reply = run(
        runner,
        database,
        "rope",
        "walk",
        SERIAL,
        "--taken",
        "2025-01-15",
        "--inspector",
        "R Halvorsen",
        "--reading",
        "0 ft:1.376 in:1.376 in",
        "--reading",
        "1200 ft:1.352 in:1.348 in",
    )
    assert reply.exit_code == 0
    assert "2 readings recorded" in reply.output
    assert "verdict serviceable" in reply.output
    assert "% of nominal" in reply.output


def test_a_worn_rope_comes_back_condemned(runner, database):
    reply = run(
        runner,
        database,
        "rope",
        "walk",
        SERIAL,
        "--taken",
        "2025-01-15",
        "--inspector",
        "R Halvorsen",
        "--reading",
        "1200 ft:1.270 in:1.270 in",
    )
    assert "verdict condemned" in reply.output


def test_a_recorded_walk_comes_back_on_the_list(runner, database):
    run(
        runner,
        database,
        "rope",
        "walk",
        SERIAL,
        "--taken",
        "2025-01-15",
        "--inspector",
        "R Halvorsen",
        "--reading",
        "0 ft:1.376 in:1.376 in",
    )
    reply = run(runner, database, "rope", "walks", SERIAL)
    assert "2025-01-15" in reply.output
    assert "R Halvorsen" in reply.output


def test_a_recorded_walk_reaches_the_printed_file(runner, database):
    run(
        runner,
        database,
        "rope",
        "walk",
        SERIAL,
        "--taken",
        "2025-01-15",
        "--inspector",
        "R Halvorsen",
        "--reading",
        "1200 ft:1.270 in:1.270 in",
    )
    reply = run(runner, database, "rope", "file", SERIAL)
    assert "standing                 condemned" in reply.output
    assert "caliper walks" in reply.output


def test_a_walk_with_a_bad_reading_is_refused_before_anything_is_written(runner, database):
    reply = run(
        runner,
        database,
        "rope",
        "walk",
        SERIAL,
        "--taken",
        "2025-01-15",
        "--inspector",
        "R Halvorsen",
        "--reading",
        "1200 ft:about an inch",
    )
    assert reply.exit_code == 1
    assert run(runner, database, "rope", "walks", SERIAL).output.count("R Halvorsen") == 0


def test_a_walk_taken_before_the_rope_went_on_is_refused(runner, database):
    reply = run(
        runner,
        database,
        "rope",
        "walk",
        SERIAL,
        "--taken",
        "2015-01-15",
        "--inspector",
        "R Halvorsen",
        "--reading",
        "0 ft:1.376 in:1.376 in",
    )
    assert reply.exit_code == 1
    assert "predate" in reply.output


def test_a_walk_needs_at_least_one_reading(runner, database):
    reply = run(
        runner,
        database,
        "rope",
        "walk",
        SERIAL,
        "--taken",
        "2025-01-15",
        "--inspector",
        "R Halvorsen",
    )
    assert reply.exit_code != 0


def test_every_printed_line_fits_the_page(runner, database):
    for args in (
        ("rope", "list"),
        ("rope", "file", SERIAL),
        ("rope", "walks", SERIAL),
    ):
        reply = run(runner, database, *args)
        assert all(len(one) <= PAGE_WIDTH for one in reply.output.splitlines()), args


def test_the_group_lists_its_commands(runner, database):
    reply = run(runner, database, "rope", "--help")
    for name in ("list", "file", "walk", "walks"):
        assert name in reply.output
