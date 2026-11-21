package io.github.rkaushikdev.sheaveline.upkeep;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * What a part is and how long it is meant to last, on the two clocks it wears out on and the
 * calendar.
 *
 * <p>A sheave liner has a life in cycles because it is worn by rope passing over it. A gearbox seal
 * has a life in hours because it is worn by turning. A rubber tyre in a detachable terminal has
 * both, and it also perishes sitting still over a summer, so it has a calendar life as well and
 * whichever arrives first is the one that ends it.
 *
 * @param code the part number
 * @param name what the crew calls it
 * @param hours its life on the hour meter, when it wears by turning
 * @param cycles its life on the cycle counter, when it wears by rope passing over it
 * @param days its life on the calendar, when it perishes standing still
 */
public record PartKind(
        String code, String name, Optional<Integer> hours, Optional<Long> cycles, Optional<Integer> days) {

    /**
     * A kind of part with a life on at least one clock.
     *
     * @throws UpkeepError when the code or the name is blank, it has no life on any clock, or a life
     *     is under one
     */
    public PartKind {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(hours, "hours");
        Objects.requireNonNull(cycles, "cycles");
        Objects.requireNonNull(days, "days");
        if (code.strip().isEmpty()) {
            throw new UpkeepError("a part kind has a code");
        }
        if (name.strip().isEmpty()) {
            throw new UpkeepError(code + " has no name");
        }
        if (hours.isEmpty() && cycles.isEmpty() && days.isEmpty()) {
            throw new UpkeepError(code + " wears out on at least one clock");
        }
        refuseUnderOne(code, hours.map(Integer::longValue), "hours");
        refuseUnderOne(code, cycles, "cycles");
        refuseUnderOne(code, days.map(Integer::longValue), "days");
    }

    /**
     * A part that wears out on the hour meter.
     *
     * @param code the part number
     * @param name what the crew calls it
     * @param hours its life in running hours
     * @return the kind
     */
    public static PartKind ofHours(String code, String name, int hours) {
        return new PartKind(code, name, Optional.of(hours), Optional.empty(), Optional.empty());
    }

    /**
     * A part that wears out on the cycle counter.
     *
     * @param code the part number
     * @param name what the crew calls it
     * @param cycles its life in cycles
     * @return the kind
     */
    public static PartKind ofCycles(String code, String name, long cycles) {
        return new PartKind(code, name, Optional.empty(), Optional.of(cycles), Optional.empty());
    }

    /**
     * A part that perishes on the calendar.
     *
     * @param code the part number
     * @param name what the crew calls it
     * @param days its life in days
     * @return the kind
     */
    public static PartKind ofDays(String code, String name, int days) {
        return new PartKind(code, name, Optional.empty(), Optional.empty(), Optional.of(days));
    }

    /**
     * The same kind with a life on the hour meter as well, or a different one.
     *
     * @param hours its life in running hours
     * @return a new kind; this one is unchanged
     */
    public PartKind withHours(int hours) {
        return new PartKind(code, name, Optional.of(hours), cycles, days);
    }

    /**
     * The same kind with a life on the cycle counter as well, or a different one.
     *
     * @param cycles its life in cycles
     * @return a new kind; this one is unchanged
     */
    public PartKind withCycles(long cycles) {
        return new PartKind(code, name, hours, Optional.of(cycles), days);
    }

    /**
     * The same kind with a life on the calendar as well, or a different one.
     *
     * @param days its life in days
     * @return a new kind; this one is unchanged
     */
    public PartKind withDays(int days) {
        return new PartKind(code, name, hours, cycles, Optional.of(days));
    }

    /**
     * The clocks this kind of part wears out on.
     *
     * @return an unmodifiable set in declaration order, never empty
     */
    public Set<Basis> bases() {
        Set<Basis> found = EnumSet.noneOf(Basis.class);
        hours.ifPresent(any -> found.add(Basis.HOURS));
        cycles.ifPresent(any -> found.add(Basis.CYCLES));
        days.ifPresent(any -> found.add(Basis.CALENDAR));
        return Collections.unmodifiableSet(found);
    }

    /** Such as {@code TYR grip conveyor tyre, 4000 hours or 365 days}, cycles grouped in thousands. */
    @Override
    public String toString() {
        List<String> parts = new ArrayList<>();
        hours.ifPresent(life -> parts.add(life + " hours"));
        cycles.ifPresent(life -> parts.add(String.format(Locale.ROOT, "%,d", life) + " cycles"));
        days.ifPresent(life -> parts.add(life + " days"));
        return code + " " + name + ", " + String.join(" or ", parts);
    }

    private static void refuseUnderOne(String code, Optional<Long> value, String label) {
        if (value.isPresent() && value.get() < 1) {
            throw new UpkeepError(code + " has a life of " + value.get() + " " + label);
        }
    }
}
