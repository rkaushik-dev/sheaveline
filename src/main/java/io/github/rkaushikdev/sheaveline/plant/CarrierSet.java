package io.github.rkaushikdev.sheaveline.plant;

import io.github.rkaushikdev.sheaveline.measures.Load;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Every carrier on one lift, numbered without duplicates and all of one kind.
 *
 * <p>The fleet is a set of numbered records rather than a count, and the count is derived.
 *
 * @param carriers the carriers in number order
 */
public record CarrierSet(List<Carrier> carriers) implements Iterable<Carrier> {

    /**
     * A fleet, put in number order and checked.
     *
     * @throws PlantError when it is empty, two carriers share a number, or the kinds are mixed
     */
    public CarrierSet {
        carriers = carriers.stream()
                .sorted(Comparator.comparingInt(Carrier::number))
                .toList();
        if (carriers.isEmpty()) {
            throw new PlantError("a lift has carriers");
        }
        if (carriers.stream().map(Carrier::number).distinct().count() != carriers.size()) {
            throw new PlantError("two carriers share a number");
        }
        if (carriers.stream().map(Carrier::kind).distinct().count() > 1) {
            throw new PlantError("a lift carries one kind of carrier");
        }
    }

    /**
     * What the fleet is made of.
     *
     * @return the one kind of carrier
     */
    public CarrierType kind() {
        return carriers.get(0).kind();
    }

    /**
     * How many carriers are on the books, on the line or not.
     *
     * @return the count
     */
    public int size() {
        return carriers.size();
    }

    @Override
    public Iterator<Carrier> iterator() {
        return carriers.iterator();
    }

    /**
     * A carrier by its painted number.
     *
     * @param number the number
     * @return the carrier
     * @throws PlantError when there is no such carrier
     */
    public Carrier carrier(int number) {
        return carriers.stream()
                .filter(one -> one.number() == number)
                .findFirst()
                .orElseThrow(() -> new PlantError("there is no carrier " + number));
    }

    /**
     * The carriers on the line.
     *
     * @return those carriers in order
     */
    public List<Carrier> onLine() {
        return carriers.stream().filter(Carrier::inService).toList();
    }

    /**
     * The carriers off the line.
     *
     * @return those carriers in order
     */
    public List<Carrier> withdrawn() {
        return carriers.stream().filter(one -> !one.inService()).toList();
    }

    /**
     * Seats on the line.
     *
     * @return the total over carriers in service
     */
    public int seatsOnLine() {
        return onLine().stream().mapToInt(Carrier::seats).sum();
    }

    /**
     * The design load of the carriers on the line.
     *
     * @return the total
     */
    public Load designLoad() {
        return onLine().stream().map(Carrier::designLoad).reduce(Load.ZERO, Load::plus);
    }

    /**
     * The gross load of the carriers on the line.
     *
     * @return the total
     */
    public Load gross() {
        return onLine().stream().map(Carrier::gross).reduce(Load.ZERO, Load::plus);
    }

    /**
     * Refuse a fleet with any carrier that does not belong on the class.
     *
     * @param liftClass the class
     * @throws PlantError naming the first carrier that does not belong
     */
    public void requireSuits(LiftClass liftClass) {
        carriers.forEach(one -> one.requireSuits(liftClass));
    }

    /**
     * Gaps in the painted numbers, which usually means a carrier is in the shop.
     *
     * @return the missing numbers below the highest, in order
     */
    public List<Integer> missingNumbers() {
        Set<Integer> numbers = carriers.stream().map(Carrier::number).collect(Collectors.toSet());
        int highest = carriers.get(carriers.size() - 1).number();
        return IntStream.rangeClosed(1, highest)
                .filter(number -> !numbers.contains(number))
                .boxed()
                .toList();
    }
}
