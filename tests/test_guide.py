"""The guide is only worth having if every command it names is a real one."""

import pytest
from click.testing import CliRunner

from sheaveline.cli import cli
from sheaveline.cli.guide import SEQUENCES, render, sequence
from sheaveline.reports.forms import PAGE_WIDTH


@pytest.fixture
def runner():
    return CliRunner(mix_stderr=False)


def resolve(words):
    """Walk the command tree the way click does when somebody types it."""
    found = cli
    for word in words.split():
        if not hasattr(found, "commands"):
            return None
        found = found.commands.get(word)
        if found is None:
            return None
    return found


def test_every_command_the_guide_names_is_one_this_program_has():
    for one in SEQUENCES:
        for step in one.steps:
            assert resolve(step.command) is not None, f"{one.name}: {step.command}"


def test_every_step_says_why_it_is_where_it_is():
    for one in SEQUENCES:
        for step in one.steps:
            assert step.why.strip(), f"{one.name}: {step.command}"
            assert not step.why[0].isupper(), step.why


def test_no_two_sequences_share_a_name():
    names = [one.name for one in SEQUENCES]
    assert len(set(names)) == len(names)


def test_every_sequence_says_when_it_is_run():
    assert all(one.when.strip() for one in SEQUENCES)


def test_a_sequence_is_found_by_any_word_of_its_name():
    assert sequence("day") is sequence("a day on the hill")
    assert sequence("season").name == "starting a season"
    assert sequence("  DAY  ") is not None


def test_a_sequence_nobody_has():
    assert sequence("grooming") is None
    assert sequence("") is None


def test_the_page_is_eighty_columns():
    assert max(len(one) for one in render(SEQUENCES).lines()) <= PAGE_WIDTH


def test_the_whole_guide_prints(runner):
    reply = runner.invoke(cli, ["guide"])
    assert reply.exit_code == 0
    for one in SEQUENCES:
        assert one.name in reply.output
    assert f"{len(SEQUENCES)} sequences" in reply.output


def test_one_sequence_prints_on_its_own(runner):
    reply = runner.invoke(cli, ["guide", "day"])
    assert reply.exit_code == 0
    assert "a day on the hill" in reply.output
    assert "closing a season" not in reply.output


def test_asking_for_a_sequence_nobody_has_names_the_ones_there_are(runner):
    reply = runner.invoke(cli, ["guide", "grooming"])
    assert reply.exit_code == 1
    assert "no sequence called grooming" in reply.output
    assert "closing a season" in reply.output


def test_the_steps_are_numbered_from_one(runner):
    reply = runner.invoke(cli, ["guide", "opening"])
    assert "1. sheaveline setup" in reply.output
    assert "4. sheaveline lift shoot" in reply.output


def test_the_guide_reaches_every_command_group_this_program_has():
    """A group nobody's sequence mentions is a group nobody knows when to run."""
    named = {step.command.split()[0] for one in SEQUENCES for step in one.steps}
    groups = {name for name, command in cli.commands.items() if hasattr(command, "commands")}
    assert groups
    assert groups <= named, groups - named


def test_the_guide_does_not_name_a_group_this_program_does_not_have():
    """The drawing commands hang off lift. A list typed by hand said otherwise."""
    for one in SEQUENCES:
        for step in one.steps:
            head = step.command.split()[0]
            assert head in cli.commands, f"{one.name}: there is no {head} command"
