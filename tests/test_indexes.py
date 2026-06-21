"""The questions the office asks most, and what the planner does with them.

An index nobody uses is a write that costs and a read that does not benefit, and
a missing index only shows up when the table is big enough to hurt, which on a
lift department is about four seasons in. So the queries are written out here as
the repositories issue them and the plan is read back.

The assertion is on the word SCAN. sqlite says SEARCH when it walks an index and
SCAN when it walks the table, and that one word is the difference between reading
a hundred rows and reading forty thousand.
"""

import pytest
from sqlalchemy import text

from sheaveline.persistence.base import build_engine, create_schema

QUESTIONS = {
    "what is stopped right now": ("select * from stops where restarted is null order by stopped"),
    "what stops touched a day": (
        "select * from stops where lift_code = 'JP' and stopped <= '2025-01-15' "
        "and (restarted is null or restarted >= '2025-01-15') order by stopped"
    ),
    "what holds are on a machine": ("select * from holds where lift_code = 'JP' order by started"),
    "what defects are open": "select * from defects where cleared is null",
    "what work is open on a machine": (
        "select * from work_orders where lift_code = 'JP' and closed is null"
    ),
    "what has not been filed": ("select * from incidents where filing_number is null order by at"),
    "what has been filed on a machine": (
        "select * from filings where lift_code = 'JP' order by filed"
    ),
    "what the weather has been since": (
        "select * from observations where taken >= '2025-01-01' order by taken"
    ),
    "what a machine was inspected for": (
        "select * from inspection_records where lift_code = 'JP' order by done"
    ),
    "what a rope has been walked for": (
        "select * from wear_surveys where rope_serial = 'FAT-2019-04412' order by taken"
    ),
    "what days a machine was written up": (
        "select * from day_logs where lift_code = 'JP' order by day"
    ),
    "what the meters have read": (
        "select * from meter_readings where lift_code = 'JP' order by day"
    ),
}


@pytest.fixture(scope="module")
def engine():
    made = build_engine()
    create_schema(made)
    yield made
    made.dispose()


def plan_for(engine, query):
    with engine.connect() as opened:
        rows = opened.execute(text(f"explain query plan {query}"))
        return " ".join(str(row[3]) for row in rows)


@pytest.mark.parametrize("question", sorted(QUESTIONS))
def test_the_planner_walks_an_index_rather_than_the_table(engine, question):
    plan = plan_for(engine, QUESTIONS[question])
    assert "SCAN" not in plan, f"{question}: {plan}"
    assert "SEARCH" in plan, f"{question}: {plan}"


def test_the_index_a_question_uses_is_the_one_it_was_written_for():
    wanted = {
        "what is stopped right now": "ix_stops_still_open",
        "what defects are open": "ix_defects_still_open",
        "what work is open on a machine": "ix_work_orders_open_by_machine",
        "what has not been filed": "ix_incidents_not_yet_filed",
        "what has been filed on a machine": "ix_filings_by_machine",
        "what the weather has been since": "ix_observations_by_moment",
        "what holds are on a machine": "ix_holds_by_machine",
    }
    made = build_engine()
    create_schema(made)
    try:
        for question, index in wanted.items():
            assert index in plan_for(made, QUESTIONS[question]), question
    finally:
        made.dispose()


def test_every_index_this_package_declares_reaches_the_database():
    from sheaveline.persistence.base import Base

    declared = {one.name for table in Base.metadata.tables.values() for one in table.indexes}
    made = build_engine()
    create_schema(made)
    with made.connect() as opened:
        built = {
            row[0]
            for row in opened.execute(text("select name from sqlite_master where type = 'index'"))
        }
    made.dispose()
    assert declared
    assert declared <= built
