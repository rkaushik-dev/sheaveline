package io.github.rkaushikdev.sheaveline.upkeep;

import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * What is on the shelf, against what the line is going to want off it.
 *
 * <p>The part register knows when a sheave liner is spent. The shelf knows how many liners are in
 * the shed. Neither of them on its own answers the question the mechanic asks in October, which is
 * whether the season can be finished without somebody driving to Salt Lake, and that is the question
 * this class exists for.
 *
 * <p>The rule that makes it worth writing down is that a part's lead time and its remaining life are
 * measured on different clocks. A liner wears on cycles and arrives in weeks, and turning one into
 * the other needs a cycle rate, which is a thing the meter history knows and the shelf does not. So
 * the rate is asked for rather than assumed, and a shortage worked out at last winter's rate is
 * labelled as such rather than presented as a fact.
 *
 * <p>Nothing here orders anything. It says how many are short and by when.
 */
public final class Stock {

    /** How many days ahead a shortage is looked for unless the caller says otherwise. */
    public static final int WITHIN_DAYS = 180;

    private Stock() {}

    /**
     * When a fitted part runs out on the calendar, the only clock that needs no rate.
     *
     * @param part the part
     * @param on the day asked about
     * @return the days left, or empty when the part has no calendar life
     */
    public static Optional<Long> daysUntilSpent(FittedPart part, LocalDate on) {
        return daysUntilSpent(part, on, Meters.NOT_READ, UsageRates.NONE);
    }

    /**
     * When a fitted part runs out, in days, on whichever clock ends it first.
     *
     * <p>The calendar clock needs no rate. The other two need both a reading and a rate, and a clock
     * missing either is left out rather than worked out from a guess.
     *
     * @param part the part
     * @param on the day asked about
     * @param meters what the machine's meters read now
     * @param rates how fast the machine runs its meters over
     * @return the days left, nought for a part already spent, or empty when no clock can be projected
     * @throws UpkeepError when a meter reads less than it did at fitting
     */
    public static Optional<Long> daysUntilSpent(FittedPart part, LocalDate on, Meters meters, UsageRates rates) {
        PartKind kind = part.kind();
        Optional<Long> soonest = part.calendarDue().map(due -> Math.max(0, ChronoUnit.DAYS.between(on, due)));
        Optional<Long> cycleRate = rates.cyclesADay().filter(rate -> rate != 0);
        if (kind.cycles().isPresent() && meters.cycles().isPresent() && cycleRate.isPresent()) {
            long left = kind.cycles().get() - part.cyclesUsed(meters.cycles().get());
            soonest = sooner(soonest, Math.max(0, Math.floorDiv(left, cycleRate.get())));
        }
        Optional<BigDecimal> hourRate = rates.hoursADay().filter(rate -> rate.signum() != 0);
        if (kind.hours().isPresent() && meters.hours().isPresent() && hourRate.isPresent()) {
            BigDecimal remaining = BigDecimal.valueOf(kind.hours().get())
                    .subtract(part.hoursUsed(meters.hours().get()));
            long days = Decimals.divide(remaining, hourRate.get())
                    .setScale(0, RoundingMode.DOWN)
                    .longValueExact();
            soonest = sooner(soonest, Math.max(0, days));
        }
        return soonest;
    }

    /**
     * Every part number the machine will want in the next {@value #WITHIN_DAYS} days and cannot get.
     *
     * @param shelf the shed
     * @param register the fitted parts
     * @param lift the machine
     * @param on the day asked about
     * @param meters what the machine's meters read now
     * @param rates how fast the machine runs its meters over
     * @return the shortages, by part number
     */
    public static List<Shortage> shortages(
            Shelf shelf, PartRegister register, LiftCode lift, LocalDate on, Meters meters, UsageRates rates) {
        return shortages(shelf, register, lift, on, meters, rates, WITHIN_DAYS);
    }

    /**
     * Every part number the machine will want inside the window and cannot get.
     *
     * <p>A part counts as wanted when it runs out within the window. A part number is short when the
     * line wants more of it than the shelf can spare over its reserve, and it is reported even when
     * the shed does not stock it at all. The order by date is the lead time before the first of them
     * runs out.
     *
     * @param shelf the shed
     * @param register the fitted parts
     * @param lift the machine
     * @param on the day asked about
     * @param meters what the machine's meters read now
     * @param rates how fast the machine runs its meters over
     * @param within how many days ahead to look
     * @return the shortages, by part number
     */
    public static List<Shortage> shortages(
            Shelf shelf,
            PartRegister register,
            LiftCode lift,
            LocalDate on,
            Meters meters,
            UsageRates rates,
            int within) {
        Map<String, Integer> wanted = new TreeMap<>();
        Map<String, Long> soonest = new HashMap<>();
        for (FittedPart part : register.onLift(lift)) {
            Optional<Long> left = daysUntilSpent(part, on, meters, rates);
            if (left.isEmpty() || left.get() > within) {
                continue;
            }
            String code = part.kind().code();
            wanted.merge(code, 1, Integer::sum);
            soonest.merge(code, left.get(), Math::min);
        }
        List<Shortage> found = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : wanted.entrySet()) {
            String code = entry.getKey();
            int count = entry.getValue();
            Optional<ShelfItem> item = shelf.item(code);
            if (count <= item.map(ShelfItem::spare).orElse(0)) {
                continue;
            }
            PartKind kind = item.map(ShelfItem::kind).orElseGet(() -> kindOf(register, lift, code));
            LocalDate neededOn = on.plusDays(soonest.get(code));
            found.add(new Shortage(
                    kind,
                    count,
                    item.map(ShelfItem::onHand).orElse(0),
                    item.map(ShelfItem::least).orElse(0),
                    Optional.of(neededOn.minusDays(item.map(ShelfItem::leadDays).orElse(0))),
                    rates.anyGiven()));
        }
        return List.copyOf(found);
    }

    private static Optional<Long> sooner(Optional<Long> soonest, long found) {
        return Optional.of(soonest.map(days -> Math.min(days, found)).orElse(found));
    }

    private static PartKind kindOf(PartRegister register, LiftCode lift, String code) {
        return register.onLift(lift).stream()
                .map(FittedPart::kind)
                .filter(kind -> kind.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new UpkeepError("nothing on " + lift + " is a " + code));
    }
}
