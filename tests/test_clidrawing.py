import json

import pytest
from click.testing import CliRunner

from hill import john_paul, strawberry
from sheaveline.cli.main import cli
from sheaveline.persistence.base import build_engine, create_schema


@pytest.fixture
def runner():
    return CliRunner()


@pytest.fixture
def database(tmp_path):
    url = f"sqlite+pysqlite:///{tmp_path / 'office.sqlite3'}"
    engine = build_engine(url)
    create_schema(engine)
    engine.dispose()
    return url


@pytest.fixture
def drawing(tmp_path):
    from sheaveline.plant.blueprint import write_lift

    path = tmp_path / "john-paul.json"
    path.write_text(json.dumps(write_lift(john_paul()), indent=2), encoding="utf-8")
    return path


def run(runner, database, *args, **kwargs):
    return runner.invoke(cli, ["--database", database, *args], **kwargs)


def test_a_drawing_that_reads_says_what_it_makes(runner, database, drawing):
    reply = run(runner, database, "lift", "check", str(drawing))
    assert reply.exit_code == 0
    assert "JP John Paul reads" in reply.output
    assert "towers           7" in reply.output
    assert "carriers         84" in reply.output
    assert "rope length fits yes" in reply.output


def test_checking_a_drawing_writes_nothing(runner, database, drawing):
    run(runner, database, "lift", "check", str(drawing))
    assert "John Paul" not in run(runner, database, "lift", "list").output


def test_loading_puts_the_machine_on_file(runner, database, drawing):
    reply = run(runner, database, "lift", "load", str(drawing))
    assert reply.exit_code == 0
    assert "JP John Paul loaded" in reply.output
    assert "7 towers, 84 carriers" in reply.output
    assert "John Paul" in run(runner, database, "lift", "list").output


def test_loading_the_same_machine_twice_is_refused(runner, database, drawing):
    run(runner, database, "lift", "load", str(drawing))
    reply = run(runner, database, "lift", "load", str(drawing))
    assert reply.exit_code == 1
    assert "already on file" in reply.output


def test_replace_takes_the_old_machine_off_first(runner, database, drawing):
    run(runner, database, "lift", "load", str(drawing))
    reply = run(runner, database, "lift", "load", str(drawing), "--replace")
    assert reply.exit_code == 0
    assert run(runner, database, "lift", "list").output.count("John Paul") == 1


def test_a_drawing_with_a_bad_tower_loads_nothing_at_all(runner, database, drawing, tmp_path):
    document = json.loads(drawing.read_text())
    del document["towers"][2]["height"]
    broken = tmp_path / "broken.json"
    broken.write_text(json.dumps(document), encoding="utf-8")
    reply = run(runner, database, "lift", "load", str(broken))
    assert reply.exit_code == 1
    assert "tower 3" in reply.output
    assert "John Paul" not in run(runner, database, "lift", "list").output


def test_a_file_that_is_not_json_says_where_it_stopped(runner, database, tmp_path):
    path = tmp_path / "not.json"
    path.write_text("{ this is not json", encoding="utf-8")
    reply = run(runner, database, "lift", "load", str(path))
    assert reply.exit_code == 1
    assert "is not json" in reply.output
    assert "line" in reply.output


def test_a_file_holding_a_list_is_not_a_drawing(runner, database, tmp_path):
    path = tmp_path / "list.json"
    path.write_text("[1, 2, 3]", encoding="utf-8")
    reply = run(runner, database, "lift", "load", str(path))
    assert reply.exit_code == 1
    assert "not a drawing" in reply.output


def test_a_file_that_is_not_there_is_refused_by_the_option(runner, database, tmp_path):
    reply = run(runner, database, "lift", "load", str(tmp_path / "nothing.json"))
    assert reply.exit_code != 0


def test_export_prints_the_drawing_to_the_page(runner, database, drawing):
    run(runner, database, "lift", "load", str(drawing))
    reply = run(runner, database, "lift", "export", "JP")
    assert reply.exit_code == 0
    document = json.loads(reply.output)
    assert document["code"] == "JP"
    assert len(document["towers"]) == 7


def test_export_writes_a_file_when_asked(runner, database, drawing, tmp_path):
    run(runner, database, "lift", "load", str(drawing))
    out = tmp_path / "again.json"
    reply = run(runner, database, "lift", "export", "JP", "--output", str(out))
    assert reply.exit_code == 0
    assert f"written to {out}" in reply.output
    assert json.loads(out.read_text())["name"] == "John Paul"


def test_a_machine_exported_and_loaded_again_is_the_same_machine(
    runner, database, drawing, tmp_path
):
    run(runner, database, "lift", "load", str(drawing))
    out = tmp_path / "again.json"
    run(runner, database, "lift", "export", "JP", "--output", str(out))
    first = run(runner, database, "lift", "show", "JP").output
    run(runner, database, "lift", "load", str(out), "--replace")
    assert run(runner, database, "lift", "show", "JP").output == first


def test_exporting_a_machine_that_is_not_there(runner, database):
    reply = run(runner, database, "lift", "export", "ZZ")
    assert reply.exit_code == 1
    assert "there is no lift ZZ" in reply.output


def test_a_second_machine_loads_alongside_the_first(runner, database, drawing, tmp_path):
    from sheaveline.plant.blueprint import write_lift

    other = tmp_path / "strawberry.json"
    other.write_text(json.dumps(write_lift(strawberry())), encoding="utf-8")
    run(runner, database, "lift", "load", str(drawing))
    run(runner, database, "lift", "load", str(other))
    listed = run(runner, database, "lift", "list").output
    assert "John Paul" in listed
    assert "Strawberry" in listed
