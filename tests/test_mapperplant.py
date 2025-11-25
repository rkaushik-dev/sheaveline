import pytest
from sqlalchemy.orm import Session

from hill import john_paul, strawberry, wildcat
from sheaveline.persistence.base import StorageError, build_engine, create_schema
from sheaveline.persistence.mappers.plant import (
    lift_from_row,
    lift_to_row,
    rope_from_row,
    rope_to_row,
    spec_from_row,
    spec_to_row,
)
from sheaveline.persistence.models.plant import LiftRow


@pytest.fixture
def engine():
    made = build_engine()
    create_schema(made)
    yield made
    made.dispose()


def save_and_load(lift):
    made = build_engine()
    create_schema(made)
    with Session(made) as session:
        session.add(rope_to_row(lift.rope, lift.code))
        session.add(lift_to_row(lift))
        session.commit()
    with Session(made) as session:
        row = session.get(LiftRow, lift.code)
        assert row is not None
        rope = rope_from_row(row.ropes[0])
        loaded = lift_from_row(row, rope)
    made.dispose()
    return loaded


def test_a_rope_spec_round_trips():
    spec = john_paul().rope.spec
    assert spec_from_row(spec_to_row(spec)) == spec


def test_a_rope_round_trips():
    rope = john_paul().rope
    assert rope_from_row(rope_to_row(rope)) == rope


def test_the_quad_comes_back_the_way_it_went_in():
    before = john_paul()
    after = save_and_load(before)
    assert after.code == before.code
    assert after.name == before.name
    assert after.lift_class is before.lift_class
    assert after.bearing == before.bearing
    assert after.design_speed == before.design_speed
    assert after.commissioned == before.commissioned


def test_the_terminals_come_back_the_right_way_up():
    before = john_paul()
    after = save_and_load(before)
    assert after.bottom.label == before.bottom.label
    assert after.top.label == before.top.label
    assert after.bottom.role is before.bottom.role
    assert after.top.tension_travel == before.top.tension_travel


def test_the_drive_train_comes_back_with_its_brakes():
    before = john_paul()
    after = save_and_load(before)
    drive = after.drive_terminal().drive_train()
    original = before.drive_terminal().drive_train()
    assert drive.rated_horsepower == original.rated_horsepower
    assert drive.gearbox_ratio == original.gearbox_ratio
    assert drive.auxiliary is original.auxiliary
    assert {brake.kind for brake in drive.brakes} == {brake.kind for brake in original.brakes}
    assert drive.brake(original.brakes[0].kind).torque == original.brakes[0].torque


def test_the_return_terminal_comes_back_with_no_drive():
    after = save_and_load(john_paul())
    assert after.top.drive is None


def test_every_tower_comes_back_with_both_assemblies():
    before = john_paul()
    after = save_and_load(before)
    assert len(after.towers) == len(before.towers)
    for was, now in zip(before.towers, after.towers, strict=True):
        assert now.ref == was.ref
        assert now.station == was.station
        assert now.height == was.height
        assert now.uphill == was.uphill
        assert now.downhill == was.downhill


def test_every_carrier_comes_back_with_its_grip():
    before = john_paul()
    after = save_and_load(before)
    assert len(after.carriers) == len(before.carriers)
    was = before.carriers[7]
    now = after.carriers[7]
    assert now == was
    assert now.grip_of().rated_slip == was.grip_of().rated_slip


def test_a_surface_carrier_comes_back_with_no_grip():
    after = save_and_load(wildcat())
    assert all(carrier.grip is None for carrier in after.carriers)


def test_the_survey_comes_back_shot_for_shot():
    before = john_paul()
    after = save_and_load(before)
    assert [point.station for point in after.ground.points] == [
        point.station for point in before.ground.points
    ]
    assert after.ground.elevation_at(before.ground.points[3].station) == (
        before.ground.points[3].elevation
    )


def test_the_reloaded_lift_still_answers_its_own_questions():
    before = john_paul()
    after = save_and_load(before)
    assert after.slope_length() == before.slope_length()
    assert after.expected_rope_length() == before.expected_rope_length()
    assert after.rope_length_agrees()
    assert len(after.spans()) == len(before.spans())


def test_the_triple_and_the_tow_round_trip_as_well():
    for lift in (strawberry(), wildcat()):
        after = save_and_load(lift)
        assert after.code == lift.code
        assert after.lift_class is lift.lift_class
        assert after.carriers_on_line() == lift.carriers_on_line()


def test_a_lift_row_with_one_terminal_is_refused(engine):
    lift = john_paul()
    row = lift_to_row(lift)
    row.terminals.pop()
    with pytest.raises(StorageError):
        lift_from_row(row, lift.rope)


def test_a_carrier_row_with_half_a_grip_is_refused(engine):
    lift = john_paul()
    row = lift_to_row(lift)
    row.carriers[0].grip_model = None
    with pytest.raises(StorageError):
        lift_from_row(row, lift.rope)


def test_a_terminal_row_with_a_drive_type_and_no_rating_is_refused(engine):
    lift = john_paul()
    row = lift_to_row(lift)
    for terminal in row.terminals:
        if terminal.drive_type is not None:
            terminal.rated_horsepower = None
    with pytest.raises(StorageError):
        lift_from_row(row, lift.rope)
