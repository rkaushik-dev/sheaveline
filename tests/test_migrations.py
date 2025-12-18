"""The migration and the models have to agree, and only a run proves it.

Autogenerate is a helpful liar. It writes what it saw at the moment somebody ran
it, and the moment a column is added to a model and nobody runs it again, the
office server and the laptop are two different databases. So this walks the
migration onto an empty file and compares the result against what the models
would have built, table by table and column by column.
"""

from pathlib import Path

import pytest
from alembic import command
from alembic.config import Config
from alembic.script import ScriptDirectory
from sqlalchemy import inspect

from sheaveline.persistence.base import build_engine, create_schema

ROOT = Path(__file__).resolve().parents[1]


def alembic_config(url: str) -> Config:
    config = Config(str(ROOT / "alembic.ini"))
    config.set_main_option("script_location", str(ROOT / "migrations"))
    config.set_main_option("sqlalchemy.url", url)
    return config


@pytest.fixture
def migrated(tmp_path):
    url = f"sqlite+pysqlite:///{tmp_path / 'migrated.sqlite3'}"
    command.upgrade(alembic_config(url), "head")
    engine = build_engine(url)
    yield engine
    engine.dispose()


@pytest.fixture
def built(tmp_path):
    url = f"sqlite+pysqlite:///{tmp_path / 'built.sqlite3'}"
    engine = build_engine(url)
    create_schema(engine)
    yield engine
    engine.dispose()


def columns_of(engine, table):
    return {
        column["name"]: (str(column["type"]), column["nullable"])
        for column in inspect(engine).get_columns(table)
    }


def test_there_is_one_head_and_it_is_the_baseline():
    scripts = ScriptDirectory(str(ROOT / "migrations"))
    heads = scripts.get_heads()
    assert len(heads) == 1
    assert heads[0] == "0001"


def test_the_migration_builds_every_table_the_models_describe(migrated, built):
    assert set(inspect(migrated).get_table_names()) - {"alembic_version"} == set(
        inspect(built).get_table_names()
    )


def test_every_table_has_the_same_columns_either_way(migrated, built):
    for table in inspect(built).get_table_names():
        assert columns_of(migrated, table) == columns_of(built, table), table


def test_the_primary_keys_agree(migrated, built):
    for table in inspect(built).get_table_names():
        assert (
            inspect(migrated).get_pk_constraint(table)["constrained_columns"]
            == inspect(built).get_pk_constraint(table)["constrained_columns"]
        ), table


def test_the_foreign_keys_agree(migrated, built):
    for table in inspect(built).get_table_names():
        made = {
            (tuple(key["constrained_columns"]), key["referred_table"])
            for key in inspect(migrated).get_foreign_keys(table)
        }
        wanted = {
            (tuple(key["constrained_columns"]), key["referred_table"])
            for key in inspect(built).get_foreign_keys(table)
        }
        assert made == wanted, table


def test_the_unique_constraints_agree(migrated, built):
    for table in inspect(built).get_table_names():
        made = {
            tuple(sorted(one["column_names"]))
            for one in inspect(migrated).get_unique_constraints(table)
        }
        wanted = {
            tuple(sorted(one["column_names"]))
            for one in inspect(built).get_unique_constraints(table)
        }
        assert made == wanted, table


def test_the_migration_goes_back_down_again(tmp_path):
    url = f"sqlite+pysqlite:///{tmp_path / 'down.sqlite3'}"
    config = alembic_config(url)
    command.upgrade(config, "head")
    command.downgrade(config, "base")
    engine = build_engine(url)
    remaining = set(inspect(engine).get_table_names()) - {"alembic_version"}
    engine.dispose()
    assert remaining == set()


def test_the_environment_variable_wins_over_the_ini(monkeypatch, tmp_path):
    url = f"sqlite+pysqlite:///{tmp_path / 'fromenv.sqlite3'}"
    monkeypatch.setenv("SHEAVELINE_DATABASE_URL", url)
    config = Config(str(ROOT / "alembic.ini"))
    config.set_main_option("script_location", str(ROOT / "migrations"))
    command.upgrade(config, "head")
    engine = build_engine(url)
    assert "lifts" in inspect(engine).get_table_names()
    engine.dispose()
