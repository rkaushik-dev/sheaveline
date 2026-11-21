package io.github.rkaushikdev.sheaveline.weather;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Every hold called this season, open and closed, and the rules about putting a machine back on
 * line.
 *
 * <p>Stopping a lift is easy and takes one person. Starting it again is the part that gets written
 * down, because two different clocks run and only one of them is the anemometer. A wind hold is
 * over when a fresh reading says it is. A lightning hold is over thirty minutes after the last
 * strike inside the radius, and it does not matter at all what the sky looks like at minute twenty
 * nine.
 *
 * <p>So a hold that was opened for two reasons has to be cleared against both, and the register is
 * the only place that knows which reasons those were.
 *
 * @param records the holds, in the order they were called
 */
public record HoldRegister(List<HoldRecord> records) {

    /** How long after the last strike a lightning hold comes off. */
    public static final Duration LIGHTNING_WAIT = Duration.ofMinutes(30);

    /** A register. */
    public HoldRegister {
        records = List.copyOf(records);
    }

    /** A register with nothing in it. */
    public HoldRegister() {
        this(List.of());
    }

    /**
     * A register with nothing in it.
     *
     * @return the empty register
     */
    public static HoldRegister of() {
        return new HoldRegister();
    }

    /**
     * A register with its holds put in the order they were called.
     *
     * @param records the holds, in any order
     * @return the register
     */
    public static HoldRegister of(List<HoldRecord> records) {
        return new HoldRegister(records.stream()
                .sorted(Comparator.comparing(HoldRecord::started))
                .toList());
    }

    /**
     * Every hold still on, on any machine.
     *
     * @return the open holds, in the order they were called
     */
    public List<HoldRecord> openHolds() {
        return records.stream().filter(HoldRecord::isOpen).toList();
    }

    /**
     * Every hold still on one machine.
     *
     * @param lift the machine
     * @return its open holds, in the order they were called
     */
    public List<HoldRecord> openHolds(LiftCode lift) {
        return records.stream()
                .filter(one -> one.isOpen() && one.lift().equals(lift))
                .toList();
    }

    /**
     * Whether anything is holding the machine.
     *
     * @param lift the machine
     * @return true while it has an open hold
     */
    public boolean isHeld(LiftCode lift) {
        return !openHolds(lift).isEmpty();
    }

    /**
     * What is holding the machine.
     *
     * @param lift the machine
     * @return the reasons of its open holds, in declaration order
     */
    public Set<HoldReason> openReasons(LiftCode lift) {
        Set<HoldReason> found = EnumSet.noneOf(HoldReason.class);
        openHolds(lift).forEach(one -> found.add(one.reason()));
        return Collections.unmodifiableSet(found);
    }

    /**
     * Open a record for every reason not already open on that machine.
     *
     * @param decision the machine read against a reading
     * @param when when the hold is called
     * @param by who calls it
     * @return a new register; this one is unchanged
     * @throws WeatherError when a hold is opened unsigned
     */
    public HoldRegister call(WeatherHold decision, LocalDateTime when, String by) {
        LiftCode lift = decision.lift().code();
        Set<HoldReason> already = openReasons(lift);
        List<HoldRecord> all = new ArrayList<>(records);
        for (Hold hold : decision.holds()) {
            if (!already.contains(hold.reason())) {
                all.add(new HoldRecord(lift, hold.reason(), when, by, hold.detail()));
            }
        }
        return of(all);
    }

    /**
     * Whether that one hold can come off, which depends on which hold it is.
     *
     * <p>A lightning hold comes off once the reading shows no strike and was taken at least {@link
     * #LIGHTNING_WAIT} before the moment asked about. Any other hold comes off once the decision no
     * longer gives its reason.
     *
     * @param hold the hold
     * @param now the moment asked about
     * @param reading the latest reading
     * @param decision the machine read against that reading
     * @return false for a hold that is already closed
     */
    public boolean mayClear(HoldRecord hold, LocalDateTime now, Observation reading, WeatherHold decision) {
        if (!hold.isOpen()) {
            return false;
        }
        if (hold.reason() == HoldReason.LIGHTNING) {
            return lightningServed(now, reading);
        }
        return !decision.reasons().contains(hold.reason());
    }

    /**
     * Close one hold in the register.
     *
     * @param hold the hold, as it is in this register
     * @param when when it came off
     * @param by who took it off
     * @return a new register with that hold closed; this one is unchanged
     * @throws WeatherError when the hold is not in this register, or is already closed
     */
    public HoldRegister clear(HoldRecord hold, LocalDateTime when, String by) {
        if (!records.contains(hold)) {
            throw new WeatherError("that hold is not in this register");
        }
        HoldRecord closed = hold.closed(when, by);
        List<HoldRecord> remaining = new ArrayList<>(records);
        remaining.remove(hold);
        remaining.add(closed);
        return of(remaining);
    }

    /**
     * The longest closed hold. The first called wins a tie.
     *
     * @return the hold
     * @throws WeatherError when nothing has been closed yet
     */
    public HoldRecord longest() {
        return records.stream()
                .filter(one -> !one.isOpen())
                .max(Comparator.comparingLong(one -> one.length().seconds()))
                .orElseThrow(() -> new WeatherError("nothing in this register has been closed yet"));
    }

    /**
     * Every hold called for one reason.
     *
     * @param reason the reason
     * @return the holds, open and closed, in the order they were called
     */
    public List<HoldRecord> byReason(HoldReason reason) {
        return records.stream().filter(one -> one.reason() == reason).toList();
    }

    /**
     * How long one machine has been held, counting closed holds only.
     *
     * @param lift the machine
     * @return the total of its closed holds' lengths
     */
    public Duration totalHeld(LiftCode lift) {
        Duration total = Duration.ZERO;
        for (HoldRecord one : records) {
            if (one.lift().equals(lift) && !one.isOpen()) {
                total = total.plus(one.length());
            }
        }
        return total;
    }

    /**
     * How many holds are in the register.
     *
     * @return the count, open and closed
     */
    public int size() {
        return records.size();
    }

    private static boolean lightningServed(LocalDateTime now, Observation reading) {
        if (reading.hasLightning()) {
            return false;
        }
        return !now.isBefore(reading.taken().plusSeconds(LIGHTNING_WAIT.seconds()));
    }
}
