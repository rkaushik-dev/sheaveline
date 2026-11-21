package io.github.rkaushikdev.sheaveline.upkeep;

import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Every part fitted to the hill that has a life on it.
 *
 * @param parts the parts, by machine and then by where on it when built with {@link #of(List)}
 */
public record PartRegister(List<FittedPart> parts) {

    private static final Comparator<FittedPart> ORDER =
            Comparator.comparing((FittedPart one) -> one.lift().value()).thenComparing(FittedPart::where);

    /** A register holding these parts in the order given. */
    public PartRegister {
        parts = List.copyOf(parts);
    }

    /**
     * An empty register.
     *
     * @return a register with nothing on it
     */
    public static PartRegister of() {
        return new PartRegister(List.of());
    }

    /**
     * A register with its parts put in order by machine and then by where on it.
     *
     * @param parts the parts, in any order
     * @return the register
     */
    public static PartRegister of(List<FittedPart> parts) {
        return new PartRegister(parts.stream().sorted(ORDER).toList());
    }

    /**
     * The parts on one machine.
     *
     * @param lift the machine
     * @return its parts, in register order
     */
    public List<FittedPart> onLift(LiftCode lift) {
        return parts.stream().filter(one -> one.lift().equals(lift)).toList();
    }

    /**
     * The parts on one machine at the end of their life, with neither meter read.
     *
     * @param lift the machine
     * @param on the day asked about
     * @return those parts, in register order
     */
    public List<FittedPart> due(LiftCode lift, LocalDate on) {
        return due(lift, on, Meters.NOT_READ);
    }

    /**
     * The parts on one machine at the end of their life.
     *
     * @param lift the machine
     * @param on the day asked about
     * @param meters what the machine's meters read now
     * @return those parts, in register order
     */
    public List<FittedPart> due(LiftCode lift, LocalDate on, Meters meters) {
        return onLift(lift).stream().filter(one -> one.isDue(on, meters)).toList();
    }

    /**
     * The parts on one machine close enough to the end to order, with neither meter read.
     *
     * @param lift the machine
     * @param on the day asked about
     * @return those parts, in register order
     */
    public List<FittedPart> toOrder(LiftCode lift, LocalDate on) {
        return toOrder(lift, on, Meters.NOT_READ);
    }

    /**
     * The parts on one machine close enough to the end to order and not yet at it.
     *
     * @param lift the machine
     * @param on the day asked about
     * @param meters what the machine's meters read now
     * @return those parts, in register order
     */
    public List<FittedPart> toOrder(LiftCode lift, LocalDate on, Meters meters) {
        return onLift(lift).stream()
                .filter(one -> one.shouldBeOrdered(on, meters) && !one.isDue(on, meters))
                .toList();
    }

    /**
     * The part on one machine with the most of its life used, with neither meter read.
     *
     * @param lift the machine
     * @param on the day asked about
     * @return the part, or empty when the machine has none on the register
     */
    public Optional<FittedPart> worst(LiftCode lift, LocalDate on) {
        return worst(lift, on, Meters.NOT_READ);
    }

    /**
     * The part on one machine with the most of its life used. Between two equally worn, the first in
     * register order.
     *
     * @param lift the machine
     * @param on the day asked about
     * @param meters what the machine's meters read now
     * @return the part, or empty when the machine has none on the register
     */
    public Optional<FittedPart> worst(LiftCode lift, LocalDate on, Meters meters) {
        return onLift(lift).stream().max(Comparator.comparing((FittedPart one) -> one.used(on, meters)));
    }

    /**
     * The register with one part taken off and its replacement put on.
     *
     * @param going the part coming off; one part equal to it is taken off
     * @param coming the part going on
     * @return a new register in order; this one is unchanged
     * @throws UpkeepError when the part coming off is not on the register
     */
    public PartRegister replace(FittedPart going, FittedPart coming) {
        List<FittedPart> remaining = new ArrayList<>(parts);
        if (!remaining.remove(going)) {
            throw new UpkeepError(going.kind().code() + " at " + going.where() + " is not on the register");
        }
        remaining.add(coming);
        return of(remaining);
    }

    /**
     * The register with one more part on it.
     *
     * @param part the part
     * @return a new register in order; this one is unchanged
     */
    public PartRegister withPart(FittedPart part) {
        List<FittedPart> all = new ArrayList<>(parts);
        all.add(part);
        return of(all);
    }

    /**
     * How many parts are on the register.
     *
     * @return the count
     */
    public int size() {
        return parts.size();
    }
}
