from datetime import date

import pytest

from hill import john_paul
from sheaveline.measures.length import Length
from sheaveline.ropework.splice import Splice, SpliceRegister
from sheaveline.ropework.wear import RopeworkError

ROPE = john_paul().rope
LONG = ROPE.diameter.scaled(1200)


def splice(station_feet=0, made=date(2019, 10, 4), mill=False, length=None, tucks=5):
    return Splice(
        station=Length.from_feet(station_feet),
        made=made,
        made_by="B Kettner",
        tucks=tucks,
        length=length or LONG,
        mill=mill,
    )


def register(*splices, rope=ROPE):
    return SpliceRegister.of(rope, splices or [splice(mill=True)])


def test_a_splice_checks_its_own_description():
    with pytest.raises(RopeworkError):
        splice(station_feet=-1)
    with pytest.raises(RopeworkError):
        Splice(Length.from_feet(0), date(2019, 10, 4), " ", 5, LONG)
    with pytest.raises(RopeworkError):
        splice(tucks=3)
    with pytest.raises(RopeworkError):
        splice(length=Length.from_feet(0))


def test_a_long_splice_is_twelve_hundred_diameters():
    assert splice().minimum_length(ROPE.spec) == ROPE.diameter.scaled(1200)
    assert splice().is_long_enough(ROPE.spec)
    assert not splice(length=Length.from_feet(50)).is_long_enough(ROPE.spec)


def test_a_splice_reports_its_age():
    assert splice().age_in_days(date(2020, 10, 3)) == 365
    with pytest.raises(RopeworkError):
        splice().age_in_days(date(2019, 1, 1))


def test_a_splice_prints_which_kind_it_is():
    assert str(splice(mill=True)) == "mill splice at 0 ft, 2019-10-04"
    assert str(splice(station_feet=3200)) == "field splice at 3200 ft, 2019-10-04"


def test_a_register_needs_exactly_one_mill_splice():
    with pytest.raises(RopeworkError):
        SpliceRegister.of(ROPE, [])
    with pytest.raises(RopeworkError):
        register(splice(0, mill=False))
    with pytest.raises(RopeworkError):
        register(splice(0, mill=True), splice(3200, mill=True))


def test_two_splices_cannot_share_a_station():
    with pytest.raises(RopeworkError):
        register(splice(0, mill=True), splice(0))


def test_a_splice_past_the_end_of_the_rope_is_refused():
    with pytest.raises(RopeworkError):
        register(splice(0, mill=True), splice(12000))


def test_the_register_sorts_by_station_and_counts():
    held = register(splice(3200), splice(0, mill=True), splice(6400))
    assert [s.station.feet for s in held.splices] == [0, 3200, 6400]
    assert held.count() == 3
    assert len(held.field_splices()) == 2


def test_a_single_splice_leaves_the_whole_rope_as_one_gap():
    assert register().separations() == (ROPE.length,)


def test_separations_go_round_the_loop_and_wrap_past_the_end():
    held = register(splice(0, mill=True), splice(4000))
    gaps = held.separations()
    assert gaps[0] == Length.from_feet(4000)
    assert gaps[1] == ROPE.length - Length.from_feet(4000)


def test_splices_have_to_be_kept_apart():
    assert register().minimum_separation() == ROPE.diameter.scaled(3000)
    close = register(splice(0, mill=True), splice(200))
    assert close.too_close()
    assert not close.is_acceptable()


def test_a_register_with_room_between_the_splices_is_acceptable():
    assert register(splice(0, mill=True), splice(4000)).is_acceptable()


def test_short_splices_are_listed_and_fail_the_register():
    held = register(splice(0, mill=True), splice(4000, length=Length.from_feet(60)))
    assert len(held.short_splices()) == 1
    assert not held.is_acceptable()


def test_too_many_splices_finishes_the_rope_however_good_they_are():
    held = register(
        splice(0, mill=True),
        splice(1800),
        splice(3600),
        splice(5400),
        splice(7200),
    )
    assert held.count() == 5
    assert not held.too_close()
    assert not held.is_acceptable()


def test_the_nearest_splice_to_a_station_is_found():
    held = register(splice(0, mill=True), splice(4000), splice(8000))
    assert held.nearest_to(Length.from_feet(3900)).station == Length.from_feet(4000)
    assert held.nearest_to(Length.from_feet(100)).station == Length.from_feet(0)


def test_cutting_a_piece_out_shortens_the_rope_and_adds_a_splice():
    before = register(splice(0, mill=True))
    after = before.shortened_by(Length.from_feet(40), splice(2000))
    assert after.rope.length == ROPE.length - Length.from_feet(40)
    assert after.count() == 2
    assert after.rope.splices == 2
    assert after.total_removed(ROPE.length) == Length.from_feet(40)


def test_a_shortening_removes_a_real_length_and_not_the_whole_rope():
    before = register(splice(0, mill=True))
    with pytest.raises(RopeworkError):
        before.shortened_by(Length.from_feet(0), splice(2000))
    with pytest.raises(RopeworkError):
        before.shortened_by(Length.from_feet(20000), splice(2000))


def test_a_shortening_is_never_recorded_as_a_mill_splice():
    before = register(splice(0, mill=True))
    with pytest.raises(RopeworkError):
        before.shortened_by(Length.from_feet(40), splice(2000, mill=True))
