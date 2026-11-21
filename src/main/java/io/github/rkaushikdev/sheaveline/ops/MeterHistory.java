package io.github.rkaushikdev.sheaveline.ops;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Speed;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Every meter reading taken off one machine, oldest first, and the trouble with reading them.
 *
 * <p>Both counters on the drive lie eventually. A reading lower than the one before it is either a
 * wrapped hour meter or a new one, and the history has to say which, because the difference is
 * either eleven hours or a hundred thousand of them. Everything with an hour based interval on it
 * reads this record, so a wrong answer here quietly moves a gearbox oil change by a decade.
 *
 * @param lift the machine
 * @param readings the readings, oldest first
 */
public record MeterHistory(LiftCode lift, List<MeterReading> readings) {

    /**
     * A history of one machine, read once a day.
     *
     * @throws OperationsError when it is empty, holds another machine's reading, or holds two
     *     readings on one day
     */
    public MeterHistory {
        Objects.requireNonNull(lift, "lift");
        readings = List.copyOf(readings);
        if (readings.isEmpty()) {
            throw new OperationsError("a meter history has readings in it");
        }
        for (MeterReading reading : readings) {
            if (!reading.lift().equals(lift)) {
                throw new OperationsError(reading.lift() + " does not belong in a " + lift + " history");
            }
        }
        Set<LocalDate> days = new HashSet<>();
        for (MeterReading reading : readings) {
            days.add(reading.day());
        }
        if (days.size() != readings.size()) {
            throw new OperationsError("a machine is read once a day");
        }
    }

    /**
     * A history with its readings put oldest first.
     *
     * @param lift the machine
     * @param readings the readings, in any order
     * @return the history
     */
    public static MeterHistory of(LiftCode lift, List<MeterReading> readings) {
        return new MeterHistory(
                lift,
                readings.stream()
                        .sorted(Comparator.comparing(MeterReading::day))
                        .toList());
    }

    /**
     * The most recent reading.
     *
     * @return the last reading
     */
    public MeterReading latest() {
        return readings.get(readings.size() - 1);
    }

    /**
     * The oldest reading.
     *
     * @return the first reading
     */
    public MeterReading first() {
        return readings.get(0);
    }

    /**
     * The reading taken on one day.
     *
     * @param day the day
     * @return that day's reading
     * @throws OperationsError when the machine was not read that day
     */
    public MeterReading on(LocalDate day) {
        for (MeterReading reading : readings) {
            if (reading.day().equals(day)) {
                return reading;
            }
        }
        throw new OperationsError(lift + " was not read on " + day);
    }

    /**
     * Hours between two readings, allowing for a wrap or a new meter.
     *
     * @param earlier the earlier reading
     * @param later the later reading
     * @return what a new meter reads, or the difference, or the difference across the wrap
     * @throws OperationsError when the readings are the wrong way round
     */
    public BigDecimal step(MeterReading earlier, MeterReading later) {
        requireInOrder(earlier, later);
        if (later.meterReplaced()) {
            return later.hours();
        }
        if (later.hours().compareTo(earlier.hours()) >= 0) {
            return later.hours().subtract(earlier.hours());
        }
        return MeterReading.METER_ROLLOVER.subtract(earlier.hours()).add(later.hours());
    }

    /**
     * Cycles between two readings, allowing for a new counter.
     *
     * @param earlier the earlier reading
     * @param later the later reading
     * @return what a new counter reads, or the difference
     * @throws OperationsError when the readings are the wrong way round, or the counter fell without
     *     being replaced
     */
    public long cycleStep(MeterReading earlier, MeterReading later) {
        requireInOrder(earlier, later);
        if (later.meterReplaced()) {
            return later.cycles();
        }
        if (later.cycles() >= earlier.cycles()) {
            return later.cycles() - earlier.cycles();
        }
        throw new OperationsError("the cycle counter fell without the meter being replaced");
    }

    /**
     * Hours run from the first reading to the last.
     *
     * @return the summed steps, zero for a single reading
     */
    public BigDecimal hoursRun() {
        BigDecimal total = BigDecimal.ZERO;
        for (int at = 1; at < readings.size(); at++) {
            total = total.add(step(readings.get(at - 1), readings.get(at)));
        }
        return total;
    }

    /**
     * Cycles run from the first reading to the last.
     *
     * @return the summed steps, zero for a single reading
     * @throws OperationsError when the counter fell anywhere without being replaced
     */
    public long cyclesRun() {
        long total = 0;
        for (int at = 1; at < readings.size(); at++) {
            total += cycleStep(readings.get(at - 1), readings.get(at));
        }
        return total;
    }

    /**
     * What the machine ran on one day, which needs the day before it.
     *
     * @param day the day
     * @return the hours since the reading before it
     * @throws OperationsError when the day was not read, or is the first reading
     */
    public BigDecimal hoursOn(LocalDate day) {
        MeterReading current = on(day);
        int position = readings.indexOf(current);
        if (position == 0) {
            throw new OperationsError(day + " is the first reading, so it has no day");
        }
        return step(readings.get(position - 1), current);
    }

    /**
     * How far the rope has run, which is what a rope's life is measured in.
     *
     * @param speed the rope speed it ran at
     * @return the hours run at that speed, any part of a mil dropped
     */
    public Length ropeTravel(Speed speed) {
        BigDecimal minutes = Decimals.multiply(hoursRun(), 60);
        BigDecimal mils = Decimals.multiply(Decimals.of(speed.milsPerMinute()), minutes);
        return new Length(mils.setScale(0, RoundingMode.DOWN).longValueExact());
    }

    /**
     * How many times the hour meter wrapped.
     *
     * @return the falls in the hours that were not a new meter
     */
    public int rollovers() {
        int count = 0;
        for (int at = 1; at < readings.size(); at++) {
            MeterReading later = readings.get(at);
            if (!later.meterReplaced()
                    && later.hours().compareTo(readings.get(at - 1).hours()) < 0) {
                count += 1;
            }
        }
        return count;
    }

    /**
     * The mornings a new meter went on.
     *
     * @return those readings, oldest first
     */
    public List<MeterReading> replacements() {
        return readings.stream().filter(MeterReading::meterReplaced).toList();
    }

    /**
     * The same history with one more reading in it.
     *
     * @param reading the reading
     * @return a new history in order; this one is unchanged
     */
    public MeterHistory withReading(MeterReading reading) {
        List<MeterReading> all = new ArrayList<>(readings);
        all.add(reading);
        return of(lift, all);
    }

    /**
     * How many readings are in the history.
     *
     * @return the count, never zero
     */
    public int size() {
        return readings.size();
    }

    private static void requireInOrder(MeterReading earlier, MeterReading later) {
        if (later.day().isBefore(earlier.day())) {
            throw new OperationsError("those readings are the wrong way round");
        }
    }
}
