package io.github.rkaushikdev.sheaveline.upkeep;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * The shed, read against a machine's fitted parts.
 *
 * @param items what is on it, by part number when built with {@link #of(List)}
 */
public record Shelf(List<ShelfItem> items) {

    /**
     * A shelf holding these items in the order given.
     *
     * @throws UpkeepError when a part number is on it twice
     */
    public Shelf {
        items = List.copyOf(items);
        Set<String> codes = new HashSet<>();
        for (ShelfItem one : items) {
            codes.add(one.kind().code());
        }
        if (codes.size() != items.size()) {
            throw new UpkeepError("a part number is on the shelf once");
        }
    }

    /**
     * An empty shelf.
     *
     * @return a shelf with nothing on it
     */
    public static Shelf of() {
        return new Shelf(List.of());
    }

    /**
     * A shelf with its items put in order by part number.
     *
     * @param items the items, in any order
     * @return the shelf
     * @throws UpkeepError when a part number is on it twice
     */
    public static Shelf of(List<ShelfItem> items) {
        return new Shelf(items.stream()
                .sorted(Comparator.comparing((ShelfItem one) -> one.kind().code()))
                .toList());
    }

    /**
     * The item stocked under a part number.
     *
     * @param code the part number, in either case
     * @return the item, or empty when the shed does not stock it
     */
    public Optional<ShelfItem> item(String code) {
        String wanted = code.toUpperCase(Locale.ROOT);
        return items.stream().filter(one -> one.kind().code().equals(wanted)).findFirst();
    }

    /**
     * How many of a part number are on the shelf.
     *
     * @param code the part number, in either case
     * @return the count, nought when the shed does not stock it
     */
    public int onHand(String code) {
        return item(code).map(ShelfItem::onHand).orElse(0);
    }

    /**
     * One off the shelf, which is what fitting a part does to the shed.
     *
     * @param code the part number
     * @return a new shelf; this one is unchanged
     * @throws UpkeepError when the shed does not stock it or has none
     */
    public Shelf took(String code) {
        return took(code, 1);
    }

    /**
     * Some off the shelf.
     *
     * @param code the part number
     * @param count how many
     * @return a new shelf; this one is unchanged
     * @throws UpkeepError when the shed does not stock it or has fewer than that on hand
     */
    public Shelf took(String code, int count) {
        ShelfItem found = stocked(code);
        if (found.onHand() < count) {
            throw new UpkeepError(code + " has " + found.onHand() + " on hand and " + count + " was asked for");
        }
        return changed(found, one -> new ShelfItem(one.kind(), one.onHand() - count, one.leadDays(), one.least()));
    }

    /**
     * A delivery put on the shelf.
     *
     * @param code the part number
     * @param count how many arrived
     * @return a new shelf; this one is unchanged
     * @throws UpkeepError when the shed does not stock it, or the delivery is empty
     */
    public Shelf received(String code, int count) {
        ShelfItem found = stocked(code);
        if (count < 1) {
            throw new UpkeepError("a delivery has something in it");
        }
        return changed(found, one -> new ShelfItem(one.kind(), one.onHand() + count, one.leadDays(), one.least()));
    }

    /**
     * How many part numbers the shed stocks.
     *
     * @return the count
     */
    public int size() {
        return items.size();
    }

    private ShelfItem stocked(String code) {
        return item(code).orElseThrow(() -> new UpkeepError("there is no " + code + " on the shelf"));
    }

    private Shelf changed(ShelfItem found, UnaryOperator<ShelfItem> change) {
        return of(items.stream()
                .map(one -> one.equals(found) ? change.apply(one) : one)
                .toList());
    }
}
