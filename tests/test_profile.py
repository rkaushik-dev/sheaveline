import pytest

from sheaveline.measures.length import Length
from sheaveline.plant.classes import PlantError
from sheaveline.plant.profile import Clearance, GroundPoint, LineProfile
from sheaveline.plant.span import Span, Support

SURVEY = [
    (0, 6400),
    (300, 6440),
    (600, 6480),
    (900, 6560),
    (1200, 6700),
    (1600, 6820),
    (2000, 6900),
]


def profile():
    return LineProfile.from_survey(SURVEY)


def span(lower_station, lower_elev, upper_station, upper_elev):
    return Span(
        1,
        Support.terminal("a", Length.from_feet(lower_station), Length.from_feet(lower_elev)),
        Support.terminal("b", Length.from_feet(upper_station), Length.from_feet(upper_elev)),
    )


def test_a_profile_sorts_the_shots_it_is_given():
    scrambled = LineProfile.from_survey([(600, 6480), (0, 6400), (300, 6440)])
    assert [point.station.feet for point in scrambled.points] == [0, 300, 600]


def test_a_profile_needs_two_shots_and_no_duplicates():
    with pytest.raises(PlantError):
        LineProfile.from_survey([(0, 6400)])
    with pytest.raises(PlantError):
        LineProfile.from_survey([(0, 6400), (0, 6410)])


def test_the_ground_reads_back_at_a_shot():
    assert profile().elevation_at(Length.from_feet(600)) == Length.from_feet(6480)
    assert profile().elevation_at(Length.from_feet(0)) == Length.from_feet(6400)
    assert profile().elevation_at(Length.from_feet(2000)) == Length.from_feet(6900)


def test_between_two_shots_the_ground_is_a_straight_line():
    assert profile().elevation_at(Length.from_feet(450)) == Length.from_feet(6460)
    assert profile().elevation_at(Length.from_feet(1400)) == Length.from_feet(6760)


def test_off_the_survey_is_refused_rather_than_extrapolated():
    with pytest.raises(PlantError):
        profile().elevation_at(Length.from_feet(2400))
    with pytest.raises(PlantError):
        profile().elevation_at(Length.from_feet(-10))
    assert not profile().covers(Length.from_feet(2400))


def test_the_profile_knows_its_ends_and_its_extremes():
    ground = profile()
    assert ground.first_station() == Length.from_feet(0)
    assert ground.last_station() == Length.from_feet(2000)
    assert ground.highest().elevation == Length.from_feet(6900)
    assert ground.lowest().elevation == Length.from_feet(6400)


def test_a_clearance_is_the_gap_between_rope_and_ground():
    gap = Clearance(
        station=Length.from_feet(600),
        rope_elevation=Length.from_feet(6512),
        ground_elevation=Length.from_feet(6480),
    )
    assert gap.height == Length.from_feet(32)
    assert gap.is_below(Length.from_feet(40))
    assert not gap.is_below(Length.from_feet(20))
    assert str(gap) == "32.0 ft at station 600"


def test_the_sampler_walks_the_span_at_the_step():
    stations = list(profile().stations(span(0, 6440, 100, 6480), Length.from_feet(25)))
    assert [s.feet for s in stations] == [0, 25, 50, 75, 100]


def test_the_sampler_puts_the_survey_shots_in_as_well():
    stations = list(profile().stations(span(0, 6440, 700, 6580), Length.from_feet(250)))
    assert Length.from_feet(300) in stations
    assert Length.from_feet(600) in stations
    assert stations[0] == Length.from_feet(0)
    assert stations[-1] == Length.from_feet(700)


def test_the_sampler_always_includes_both_ends():
    stations = list(profile().stations(span(100, 6420, 350, 6450), Length.from_feet(200)))
    assert stations[0] == Length.from_feet(100)
    assert stations[-1] == Length.from_feet(350)


def test_a_step_of_nothing_is_refused():
    with pytest.raises(PlantError):
        list(profile().stations(span(0, 6440, 600, 6520), Length.from_feet(0)))


def test_clearance_reads_the_rope_and_the_ground_at_one_station():
    gap = profile().clearance_at(span(0, 6440, 600, 6540), Length.from_feet(300))
    assert gap.rope_elevation == Length.from_feet(6490)
    assert gap.ground_elevation == Length.from_feet(6440)
    assert gap.height == Length.from_feet(50)


def test_the_tightest_clearance_is_the_one_that_gets_acted_on():
    # The ground climbs faster than the chord in the middle of this span.
    steep = LineProfile.from_survey([(0, 6400), (300, 6520), (600, 6540)])
    worst = steep.tightest(span(0, 6440, 600, 6580), Length.from_feet(50))
    assert worst.station == Length.from_feet(300)
    assert worst.height == Length.from_feet(-10)


def test_the_tightest_is_found_between_shots_when_the_knoll_is_there():
    knoll = LineProfile.from_survey([(0, 6400), (250, 6560), (500, 6400)])
    worst = knoll.tightest(span(0, 6440, 500, 6440), Length.from_feet(25))
    assert worst.station == Length.from_feet(250)


def test_every_sampled_clearance_comes_back_in_order():
    gaps = list(profile().clearances(span(0, 6440, 600, 6540), Length.from_feet(150)))
    assert [gap.station.feet for gap in gaps] == [0, 150, 300, 450, 600]
    assert all(isinstance(gap, Clearance) for gap in gaps)


def test_a_ground_point_can_be_built_straight_from_feet():
    point = GroundPoint.at_feet(600, "6480.5")
    assert point.station == Length.from_feet(600)
    assert point.elevation == Length.from_feet("6480.5")
