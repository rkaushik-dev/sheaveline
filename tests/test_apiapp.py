import pytest
from fastapi import FastAPI, status
from fastapi.testclient import TestClient

from sheaveline.api.app import Settings, build_app, settings_of, unit_of_work
from sheaveline.api.errors import NotFound, body_for, status_for
from sheaveline.errors import ParseError, RangeError
from sheaveline.measures.length import Length
from sheaveline.persistence.base import StorageError
from sheaveline.plant.classes import PlantError
from sheaveline.upkeep.interval import UpkeepError


@pytest.fixture
def app():
    return build_app(Settings.in_memory())


@pytest.fixture
def client(app):
    with TestClient(app) as opened:
        yield opened


def test_the_service_answers_for_itself(client):
    reply = client.get("/health")
    assert reply.status_code == status.HTTP_200_OK
    body = reply.json()
    assert body["service"] == "sheaveline"
    assert body["resort"] == "Ogden Peak"


def test_the_factory_makes_a_fresh_application_every_time():
    first = build_app(Settings.in_memory())
    second = build_app(Settings.in_memory())
    assert first is not second
    assert settings_of(first).engine is not settings_of(second).engine


def test_the_resort_name_comes_off_the_settings():
    app = build_app(Settings.in_memory(resort="Alta View"))
    with TestClient(app) as client:
        assert client.get("/health").json()["resort"] == "Alta View"


def test_an_application_built_without_settings_says_so():
    bare = FastAPI()
    with pytest.raises(RuntimeError):
        settings_of(bare)


def test_a_unit_of_work_is_opened_and_closed_around_a_request(app):
    opened = list(unit_of_work(app))
    assert len(opened) == 1


def test_a_parse_failure_is_a_four_hundred():
    assert status_for(ParseError("nine", "a length")) == status.HTTP_400_BAD_REQUEST


def test_a_range_failure_is_a_four_hundred():
    assert status_for(RangeError("tower number", 0, 1, 99)) == status.HTTP_400_BAD_REQUEST


def test_a_domain_refusal_is_a_conflict():
    assert status_for(PlantError("a quad does not take a six place")) == (status.HTTP_409_CONFLICT)
    assert status_for(UpkeepError("no such job")) == status.HTTP_409_CONFLICT


def test_something_that_is_not_on_file_is_a_four_oh_four():
    assert status_for(StorageError("there is no lift ZZ")) == status.HTTP_404_NOT_FOUND
    assert status_for(NotFound("nothing here")) == status.HTTP_404_NOT_FOUND


def test_the_most_specific_row_in_the_table_wins():
    """ParseError is a MeasureError, and the narrower row is the one that counts."""
    assert status_for(ParseError("nine", "a length")) == status.HTTP_400_BAD_REQUEST


def test_anything_unmapped_is_a_five_hundred():
    assert status_for(RuntimeError("something else")) == (status.HTTP_500_INTERNAL_SERVER_ERROR)


def test_the_body_names_the_kind_and_the_sentence():
    body = body_for(PlantError("a quad does not take a six place"))
    assert body.kind == "PlantError"
    assert body.detail == "a quad does not take a six place"
    assert body.field is None


def test_a_range_failure_says_which_field_it_was():
    body = body_for(RangeError("tower number", 0, 1, 99))
    assert body.field == "tower number"


def test_a_parse_failure_says_what_it_wanted():
    body = body_for(ParseError("nine", "a length"))
    assert body.field == "a length"


def test_a_domain_failure_raised_in_a_route_comes_back_in_the_one_shape(app):
    @app.get("/scratch/refuse")
    def refuse() -> dict[str, str]:
        raise PlantError("a quad does not take a six place")

    with TestClient(app, raise_server_exceptions=False) as client:
        reply = client.get("/scratch/refuse")
    assert reply.status_code == status.HTTP_409_CONFLICT
    assert reply.json() == {
        "kind": "PlantError",
        "detail": "a quad does not take a six place",
        "field": None,
    }


def test_a_measure_failure_raised_in_a_route_is_a_four_hundred(app):
    @app.get("/scratch/measure")
    def measure() -> dict[str, str]:
        Length.parse("nine")
        return {}

    with TestClient(app, raise_server_exceptions=False) as client:
        reply = client.get("/scratch/measure")
    assert reply.status_code == status.HTTP_400_BAD_REQUEST
    assert reply.json()["field"] == "a length"


def test_a_bad_request_is_the_same_shape_as_everything_else(app):
    @app.get("/scratch/needs")
    def needs(count: int) -> dict[str, int]:
        return {"count": count}

    with TestClient(app, raise_server_exceptions=False) as client:
        reply = client.get("/scratch/needs?count=notanumber")
    assert reply.status_code == status.HTTP_422_UNPROCESSABLE_ENTITY
    body = reply.json()
    assert set(body) == {"kind", "detail", "field"}
    assert body["field"] == "count"
