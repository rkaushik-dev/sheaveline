"""The template is only worth having if a test cannot see what another one wrote."""

from sqlalchemy import inspect

from hill import JOHN_PAUL, STRAWBERRY, WILDCAT, john_paul, strawberry
from office import WHOLE_HILL, copied, empty, stocked, template
from sheaveline.persistence.base import build_engine, table_names
from sheaveline.persistence.uow import UnitOfWork


def codes(url):
    engine = build_engine(url)
    with UnitOfWork.on(engine) as work:
        found = work.lifts.codes()
    engine.dispose()
    return found


def test_the_whole_hill_is_on_the_register_by_default(tmp_path):
    assert codes(stocked(tmp_path)) == [JOHN_PAUL, STRAWBERRY, WILDCAT]


def test_only_the_machines_that_were_asked_for(tmp_path):
    assert codes(stocked(tmp_path, john_paul)) == [JOHN_PAUL]
    assert codes(stocked(tmp_path / "two", john_paul, strawberry)) == [JOHN_PAUL, STRAWBERRY]


def test_an_empty_database_still_has_every_table(tmp_path):
    engine = build_engine(empty(tmp_path))
    built = set(inspect(engine).get_table_names())
    engine.dispose()
    assert built.issuperset(table_names())


def test_what_one_copy_writes_does_not_reach_the_next(tmp_path):
    first = stocked(tmp_path / "first", john_paul, strawberry)
    engine = build_engine(first)
    with UnitOfWork.on(engine) as work:
        work.lifts.remove(JOHN_PAUL)
        work.commit()
    engine.dispose()
    assert codes(first) == [STRAWBERRY]
    assert codes(stocked(tmp_path / "second", john_paul, strawberry)) == [JOHN_PAUL, STRAWBERRY]


def test_the_template_is_built_once_and_then_handed_back(tmp_path):
    def fill(work):
        work.lifts.add(john_paul())

    first = template("one-machine", fill)
    assert template("one-machine", fill) is first


def test_two_copies_of_the_same_template_are_different_files(tmp_path):
    one = copied(tmp_path / "one", "empty", lambda work: None)
    two = copied(tmp_path / "two", "empty", lambda work: None)
    assert one != two


def test_the_whole_hill_is_the_three_machines_the_fixtures_use():
    assert [one.__name__ for one in WHOLE_HILL] == ["john_paul", "strawberry", "wildcat"]
