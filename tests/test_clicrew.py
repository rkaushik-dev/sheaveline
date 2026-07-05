import pytest
from click.testing import CliRunner

from hill import john_paul, strawberry
from office import stocked
from sheaveline.cli import cli

ON = "2025-01-15"


@pytest.fixture
def runner():
    return CliRunner()


@pytest.fixture
def database(tmp_path):
    return stocked(tmp_path, john_paul, strawberry)


def run(runner, database, *args):
    return runner.invoke(cli, ["--database", database, *args])


def carded(runner, database, person="D Chin", card="operator", expires="2025-11-01", *extra):
    return run(
        runner,
        database,
        "crew",
        "card",
        person,
        card,
        "--issued",
        "2024-11-01",
        "--expires",
        expires,
        "--by",
        "Utah tramway board",
        *extra,
    )


def manned(runner, database, person="D Chin", post="bottom", code="JP"):
    return run(runner, database, "crew", "assign", code, person, post, "--on", ON)


def test_a_card_goes_in_the_file(runner, database):
    reply = carded(runner, database)
    assert reply.exit_code == 0
    assert "D Chin" in reply.output


def test_a_card_can_be_limited_to_some_classes(runner, database):
    reply = carded(runner, database, "T Woo", "operator", "2025-11-01", "--only", "surface_tow")
    assert reply.exit_code == 0
    assert "covers   surface lift" in reply.output


def test_a_class_nobody_runs(runner, database):
    reply = carded(runner, database, "T Woo", "operator", "2025-11-01", "--only", "funicular")
    assert reply.exit_code == 2
    assert "is not one of" in reply.output


def test_the_same_card_is_not_issued_twice(runner, database):
    carded(runner, database)
    again = carded(runner, database)
    assert again.exit_code == 1
    assert "already has" in again.output


def test_the_file_lists_who_is_carded_for_what(runner, database):
    assert "nothing is on file" in run(runner, database, "crew", "cards").output
    carded(runner, database)
    reply = run(runner, database, "crew", "cards", "--on", ON)
    assert reply.exit_code == 0
    assert "D Chin" in reply.output
    assert "current" in reply.output


def test_a_lapsed_card_is_marked_lapsed(runner, database):
    carded(runner, database, expires="2024-12-01")
    reply = run(runner, database, "crew", "cards", "--on", ON)
    assert "lapsed" in reply.output


def test_a_card_running_out_soon_is_called_out(runner, database):
    carded(runner, database, expires="2025-02-01")
    reply = run(runner, database, "crew", "cards", "--on", ON, "--expiring", "45")
    assert "runs out 2025-02-01" in reply.output


def test_renewing_writes_a_new_card_and_keeps_the_old(runner, database):
    carded(runner, database)
    reply = run(
        runner,
        database,
        "crew",
        "renew",
        "D Chin",
        "operator",
        "--expires",
        "2026-11-01",
        "--by",
        "Utah tramway board",
    )
    assert reply.exit_code == 0
    listed = run(runner, database, "crew", "cards", "--person", "D Chin", "--on", ON)
    assert listed.output.count("D Chin") == 2


def test_renewing_a_card_nobody_holds(runner, database):
    reply = run(
        runner,
        database,
        "crew",
        "renew",
        "T Woo",
        "operator",
        "--expires",
        "2026-11-01",
        "--by",
        "Utah tramway board",
    )
    assert reply.exit_code == 1
    assert "no operator card to renew" in reply.output


def test_a_shift_goes_on_the_board(runner, database):
    reply = manned(runner, database)
    assert reply.exit_code == 0
    assert "D Chin on JP at bottom operator" in reply.output


def test_somebody_down_on_two_machines_at_once_is_called_out(runner, database):
    manned(runner, database, code="JP")
    reply = manned(runner, database, code="SB")
    assert reply.exit_code == 0
    assert "also down on JP" in reply.output


def test_an_empty_roster_is_short_of_everything(runner, database):
    reply = run(runner, database, "crew", "gap", "JP", "--on", ON)
    assert reply.exit_code == 1
    assert "SHORT" in reply.output
    assert "cannot be staffed" in reply.output


def test_a_post_filled_by_somebody_uncarded_is_still_short(runner, database):
    manned(runner, database)
    reply = run(runner, database, "crew", "gap", "JP", "--on", ON)
    assert reply.exit_code == 1
    assert "D Chin" in reply.output


def test_the_gap_names_who_could_cover(runner, database):
    carded(runner, database, "T Woo", "lead_operator")
    reply = run(runner, database, "crew", "gap", "JP", "--on", ON)
    assert "cover: T Woo" in reply.output


def test_with_nobody_carded_the_cover_line_says_so(runner, database):
    reply = run(runner, database, "crew", "gap", "JP", "--on", ON)
    assert "nobody carded" in reply.output


def test_with_no_machine_it_walks_the_whole_register(runner, database):
    reply = run(runner, database, "crew", "gap", "--on", ON)
    assert "JP" in reply.output
    assert "SB" in reply.output
    assert "2 machines cannot be staffed" in reply.output


def test_a_fully_carded_and_manned_machine_is_legal(runner, database):
    for person, card in (("D Chin", "operator"), ("T Woo", "lead_operator")):
        carded(runner, database, person, card)
    manned(runner, database, "D Chin", "bottom")
    manned(runner, database, "T Woo", "top")
    reply = run(runner, database, "crew", "gap", "JP", "--on", ON)
    assert reply.exit_code == 0
    assert "JP  legal" in reply.output
