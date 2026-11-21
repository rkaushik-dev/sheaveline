package io.github.rkaushikdev.sheaveline.ropework;

import io.github.rkaushikdev.sheaveline.plant.Carrier;
import io.github.rkaushikdev.sheaveline.plant.CarrierSet;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A season of grip slip testing on one lift, and the sample size that changes when one fails.
 *
 * <p>A sample of the fleet comes off every season and gets tested on the bench: ten percent of the
 * fleet in an ordinary season, never fewer than four grips. But a failure anywhere in the sample
 * means the sample was not big enough to trust, and the whole fleet gets tested that season. So the
 * rule that decides how many grips to test depends on the result of testing them, which is why it is
 * written here once and read everywhere else.
 *
 * @param fleet the carriers on the lift
 * @param seasonLabel the season, such as {@code 2024/25}
 * @param tests the tests run this season
 * @param previousSeasonFailed whether a grip failed last season
 */
public record SlipProgramme(CarrierSet fleet, String seasonLabel, List<SlipTest> tests, boolean previousSeasonFailed) {

    /** The part of the fleet tested in an ordinary season. */
    public static final BigDecimal SAMPLE_FRACTION = new BigDecimal("0.10");

    /** The fewest grips an ordinary season tests. */
    public static final int MINIMUM_SAMPLE = 4;

    /**
     * A programme for one season.
     *
     * @throws RopeworkError when the season is not named, a carrier is tested twice, or a test is of a
     *     carrier that is not on the lift
     */
    public SlipProgramme {
        Objects.requireNonNull(fleet, "fleet");
        Objects.requireNonNull(seasonLabel, "seasonLabel");
        tests = List.copyOf(tests);
        if (seasonLabel.strip().isEmpty()) {
            throw new RopeworkError("a slip programme names the season it belongs to");
        }
        if (tests.stream().map(SlipTest::carrier).distinct().count() != tests.size()) {
            throw new RopeworkError("a carrier is slip tested once a season");
        }
        Set<Integer> onLine = fleet.carriers().stream().map(Carrier::number).collect(Collectors.toSet());
        for (SlipTest test : tests) {
            if (!onLine.contains(test.carrier())) {
                throw new RopeworkError("carrier " + test.carrier() + " is not on this lift");
            }
        }
    }

    /**
     * A season with nothing tested yet, after a clean season.
     *
     * @param fleet the carriers
     * @param seasonLabel the season
     * @return the programme
     */
    public static SlipProgramme of(CarrierSet fleet, String seasonLabel) {
        return of(fleet, seasonLabel, List.of());
    }

    /**
     * A season after a clean season.
     *
     * @param fleet the carriers
     * @param seasonLabel the season
     * @param tests the tests run
     * @return the programme
     */
    public static SlipProgramme of(CarrierSet fleet, String seasonLabel, List<SlipTest> tests) {
        return of(fleet, seasonLabel, tests, false);
    }

    /**
     * A season.
     *
     * @param fleet the carriers
     * @param seasonLabel the season
     * @param tests the tests run
     * @param previousSeasonFailed whether a grip failed last season
     * @return the programme
     */
    public static SlipProgramme of(
            CarrierSet fleet, String seasonLabel, List<SlipTest> tests, boolean previousSeasonFailed) {
        return new SlipProgramme(fleet, seasonLabel, tests, previousSeasonFailed);
    }

    /**
     * How many carriers are on the line.
     *
     * @return the count
     */
    public int fleetSize() {
        return fleet.onLine().size();
    }

    /**
     * The sample in an ordinary season.
     *
     * @return a tenth of the fleet rounded up, and never fewer than four
     */
    public int ordinarySample() {
        int wanted = Decimals.multiply(Decimals.of(fleetSize()), SAMPLE_FRACTION)
                .setScale(0, RoundingMode.UP)
                .intValueExact();
        return Math.max(wanted, MINIMUM_SAMPLE);
    }

    /**
     * Ten percent, unless something has failed, in which case all of them.
     *
     * @return the grips to test this season
     */
    public int requiredSample() {
        if (previousSeasonFailed || !failures().isEmpty()) {
            return fleetSize();
        }
        return Math.min(ordinarySample(), fleetSize());
    }

    /**
     * How many grips have been tested.
     *
     * @return the count
     */
    public int testedCount() {
        return tests.size();
    }

    /**
     * The tests a grip failed.
     *
     * @return those tests, in the order run
     */
    public List<SlipTest> failures() {
        return tests.stream().filter(test -> !test.passed()).toList();
    }

    /**
     * The tests in which a grip let go early.
     *
     * @return those tests, in the order run
     */
    public List<SlipTest> earlySlips() {
        return tests.stream().filter(SlipTest::slippedEarly).toList();
    }

    /**
     * How many more grips have to be tested.
     *
     * @return the count still to do, never negative
     */
    public int outstanding() {
        return Math.max(0, requiredSample() - testedCount());
    }

    /**
     * Whether the season's sample is done.
     *
     * @return true with nothing outstanding
     */
    public boolean isComplete() {
        return outstanding() == 0;
    }

    /**
     * The carriers on the line not yet tested this season.
     *
     * @return their numbers, in fleet order
     */
    public List<Integer> untestedCarriers() {
        Set<Integer> done = tests.stream().map(SlipTest::carrier).collect(Collectors.toSet());
        return fleet.onLine().stream()
                .map(Carrier::number)
                .filter(number -> !done.contains(number))
                .toList();
    }

    /**
     * The programme with one more test on file.
     *
     * @param test the new test
     * @return the new programme, its sample worked out again
     */
    public SlipProgramme withTest(SlipTest test) {
        List<SlipTest> more = new ArrayList<>(tests);
        more.add(test);
        return of(fleet, seasonLabel, more, previousSeasonFailed);
    }

    /** Such as {@code 2024/25 slip tests, 1 of 9 done}. */
    @Override
    public String toString() {
        return seasonLabel + " slip tests, " + testedCount() + " of " + requiredSample() + " done";
    }
}
