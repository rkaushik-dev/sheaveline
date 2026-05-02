import ast
import pathlib

import pytest
from fastapi import status

from hill import john_paul
from sheaveline.api.errors import NotFound
from sheaveline.api.lookups import Lookup
from sheaveline.api.security import Role
from sheaveline.measures.length import Length
from sheaveline.persistence.uow import UnitOfWork
from sheaveline.plant.identity import LiftCode
from sheaveline.weather.observation import Station
from wire import client_for, headers, settings

ROUTES = pathlib.Path(__file__).resolve().parents[1] / "src" / "sheaveline" / "api" / "routes"


@pytest.fixture
def built():
    made = settings()
    with UnitOfWork.on(made.engine) as work:
        work.lifts.add(john_paul())
        work.weather.add_station(Station("summit", Length.from_feet(7981)))
        work.commit()
    return made


@pytest.fixture
def look(built):
    with UnitOfWork.on(built.engine) as work:
        yield Lookup(work)


def test_a_machine_on_the_register_comes_back(look):
    assert look.lift("JP").name == "John Paul"
    assert look.lift_code("JP") == LiftCode("JP")


def test_a_machine_that_is_not_on_the_register(look):
    with pytest.raises(NotFound) as raised:
        look.lift("ZZ")
    assert str(raised.value) == "there is no lift ZZ"
    with pytest.raises(NotFound):
        look.lift_code("ZZ")


def test_a_rope_a_station_an_order_and_an_incident_all_answer_the_same_way(look):
    for call, wanted in (
        (lambda: look.rope("ZZZ-2019-0404"), "there is no rope ZZZ-2019-0404"),
        (lambda: look.station("nowhere"), "there is no station called nowhere"),
        (lambda: look.work_order(404), "there is no work order 404"),
        (lambda: look.incident(404), "there is no incident 404"),
    ):
        with pytest.raises(NotFound) as raised:
            call()
        assert str(raised.value) == wanted


def test_a_station_that_is_on_file(look):
    assert look.station("summit").name == "summit"


def test_no_route_module_writes_its_own_lookup_any_more():
    """The five private helpers this replaced, and the eight blocks they inlined."""
    for path in sorted(ROUTES.glob("*.py")):
        source = path.read_text()
        assert "def _lift(" not in source, path.name
        assert "def _known_lift(" not in source, path.name
        assert "def _rope(" not in source, path.name
        assert "raise NotFound(" not in source, path.name


def test_every_route_that_looks_something_up_asks_for_the_lookup():
    for path in sorted(ROUTES.glob("*.py")):
        tree = ast.parse(path.read_text())
        for node in tree.body:
            if not isinstance(node, ast.FunctionDef):
                continue
            body = ast.get_source_segment(path.read_text(), node) or ""
            if "look." not in body:
                continue
            names = [one.arg for one in node.args.args]
            assert "look" in names, f"{path.name}:{node.name}"


def test_the_lookup_and_the_route_share_one_session(built):
    """Two dependencies, one unit of work, or a withdrawal would not commit."""
    with client_for(built) as client:
        reply = client.delete("/lifts/JP", headers=headers(Role.MANAGER))
        assert reply.status_code == status.HTTP_204_NO_CONTENT
    with UnitOfWork.on(built.engine) as work:
        assert not work.lifts.exists(LiftCode("JP"))


def test_the_sentence_the_client_reads_is_the_same_whichever_route_asked(built):
    with client_for(built) as client:
        for path in ("/lifts/ZZ", "/lifts/ZZ/towers", "/lifts/ZZ/spans"):
            reply = client.get(path, headers=headers())
            assert reply.status_code == status.HTTP_404_NOT_FOUND
            assert reply.json()["detail"] == "there is no lift ZZ"
            assert reply.json()["kind"] == "NotFound"
