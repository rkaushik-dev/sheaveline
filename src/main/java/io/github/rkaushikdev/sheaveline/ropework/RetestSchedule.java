package io.github.rkaushikdev.sheaveline.ropework;

import io.github.rkaushikdev.sheaveline.plant.HaulRope;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

/**
 * When the next magnetic test falls due.
 *
 * <p>How often a rope is tested is not a fixed interval. A new rope gets four years before its first
 * test, then two years between tests, and once it is ten years old it is tested every year. So the
 * interval depends on the rope's age at the time the question is asked, which is not the same as its
 * age at the last test.
 *
 * @param rope the rope
 * @param lastTest the day of its last magnetic test, empty when it has never had one
 */
public record RetestSchedule(HaulRope rope, Optional<LocalDate> lastTest) {

    /** Years from installation to the first test. */
    public static final int FIRST_TEST_YEARS = 4;

    /** The age in whole seasons from which a rope is tested every year. */
    public static final int MID_LIFE_YEARS = 10;

    /** Years between tests on a rope younger than mid life. */
    public static final int MID_LIFE_INTERVAL_YEARS = 2;

    /** Years between tests on a rope past mid life. */
    public static final int LATE_LIFE_INTERVAL_YEARS = 1;

    /**
     * A schedule.
     *
     * @throws RopeworkError when the last test predates the rope
     */
    public RetestSchedule {
        Objects.requireNonNull(rope, "rope");
        Objects.requireNonNull(lastTest, "lastTest");
        if (lastTest.isPresent() && lastTest.get().isBefore(rope.installed())) {
            throw new RopeworkError("a test cannot predate the rope it is of");
        }
    }

    /**
     * A rope that has never been tested.
     *
     * @param rope the rope
     */
    public RetestSchedule(HaulRope rope) {
        this(rope, Optional.empty());
    }

    /**
     * A rope last tested on a day.
     *
     * @param rope the rope
     * @param lastTest the day of its last test
     */
    public RetestSchedule(HaulRope rope, LocalDate lastTest) {
        this(rope, Optional.of(lastTest));
    }

    /**
     * The interval in force at a date, which turns on the rope's age then.
     *
     * @param on the day asked about
     * @return whole years between tests
     */
    public int intervalYears(LocalDate on) {
        long age = rope.seasonsInService(on);
        if (age >= MID_LIFE_YEARS) {
            return LATE_LIFE_INTERVAL_YEARS;
        }
        return MID_LIFE_INTERVAL_YEARS;
    }

    /**
     * When the rope is next due, read as at a given date.
     *
     * @param on the day asked about
     * @return the due date
     */
    public LocalDate nextDue(LocalDate on) {
        return lastTest.map(last -> addYears(last, intervalYears(on)))
                .orElseGet(() -> addYears(rope.installed(), FIRST_TEST_YEARS));
    }

    /**
     * Whether the rope is past its due date.
     *
     * @param on the day asked about
     * @return true after the due date
     */
    public boolean isOverdue(LocalDate on) {
        return on.isAfter(nextDue(on));
    }

    /**
     * Days left before the rope falls due.
     *
     * @param on the day asked about
     * @return the days, negative once it is overdue
     */
    public long daysUntilDue(LocalDate on) {
        return ChronoUnit.DAYS.between(on, nextDue(on));
    }

    /**
     * The schedule after a test is recorded.
     *
     * @param test the test just run
     * @return the schedule counting from that test
     * @throws RopeworkError when the test is of another rope
     */
    public RetestSchedule after(MagneticTest test) {
        if (!test.rope().serial().equals(rope.serial())) {
            throw new RopeworkError("that test is of another rope");
        }
        return new RetestSchedule(rope, test.taken());
    }

    /**
     * The same day of the same month some years on. The twenty ninth of February in a year that has
     * no twenty ninth becomes the first of March.
     */
    private static LocalDate addYears(LocalDate day, int years) {
        YearMonth month = YearMonth.of(day.getYear() + years, day.getMonth());
        if (month.isValidDay(day.getDayOfMonth())) {
            return month.atDay(day.getDayOfMonth());
        }
        return month.atDay(day.getDayOfMonth() - 1).plusDays(1);
    }
}
