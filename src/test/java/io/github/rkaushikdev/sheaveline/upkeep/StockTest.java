package io.github.rkaushikdev.sheaveline.upkeep;

import static io.github.rkaushikdev.sheaveline.Hill.JOHN_PAUL;
import static io.github.rkaushikdev.sheaveline.Hill.STRAWBERRY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class StockTest {

    private static final LocalDate ON = LocalDate.of(2025, 10, 1);
    private static final LocalDate FITTED = LocalDate.of(2024, 11, 1);

    private static final PartKind LINER = PartKind.ofCycles("LIN", "sheave liner", 1_000_000);
    private static final PartKind TYRE = PartKind.ofHours("TYR", "grip conveyor tyre", 4000);
    private static final PartKind SEAL = PartKind.ofDays("SEA", "gearbox output seal", 730);

    private static final Meters RUN = Meters.ofCycles(1_500_000);
    private static final UsageRates RATE = UsageRates.ofCyclesADay(4000);

    private static ShelfItem item(PartKind kind, int onHand, int lead, int least) {
        return new ShelfItem(kind, onHand, lead, least);
    }

    private static ShelfItem item(PartKind kind, int onHand, int lead) {
        return item(kind, onHand, lead, 0);
    }

    private static ShelfItem item(PartKind kind, int onHand) {
        return item(kind, onHand, 42);
    }

    private static ShelfItem item() {
        return item(LINER, 4);
    }

    private static FittedPart part(PartKind kind, String where, LiftCode lift, long cyclesAt, String hoursAt) {
        return new FittedPart(kind, lift, where, FITTED, new BigDecimal(hoursAt), cyclesAt);
    }

    private static FittedPart part(PartKind kind, String where) {
        return part(kind, where, JOHN_PAUL, 900_000, "40000.0");
    }

    private static FittedPart part(PartKind kind) {
        return part(kind, "JP-T4");
    }

    private static FittedPart part(String where) {
        return part(LINER, where);
    }

    private static List<Shortage> shortages(Shelf shelf, PartRegister register) {
        return Stock.shortages(shelf, register, JOHN_PAUL, ON, RUN, RATE);
    }

    @Test
    void aShelfItemCannotHoldANegativeNumber() {
        assertThatThrownBy(() -> item(LINER, -1))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("LIN cannot have -1 on the shelf");
        assertThatThrownBy(() -> item(LINER, 4, -1))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("LIN cannot arrive before it is ordered");
        assertThatThrownBy(() -> item(LINER, 4, 42, -1))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("LIN cannot want fewer than none in reserve");
    }

    @Test
    void whatIsSpareIsWhatIsOverTheReserve() {
        assertThat(item(LINER, 4, 42, 2).spare()).isEqualTo(2);
        assertThat(item(LINER, 1, 42, 2).spare()).isZero();
    }

    @Test
    void aPartNumberIsOnTheShelfOnce() {
        assertThatThrownBy(() -> new Shelf(List.of(item(), item())))
                .isInstanceOf(UpkeepError.class)
                .hasMessageContaining("on the shelf once");
    }

    @Test
    void theShelfAnswersWhatIsOnIt() {
        Shelf made = Shelf.of(List.of(item(LINER, 4), item(TYRE, 2)));
        assertThat(made.size()).isEqualTo(2);
        assertThat(made.onHand("lin")).isEqualTo(4);
        assertThat(made.onHand("nothing")).isZero();
        assertThat(made.item("TYR"))
                .hasValueSatisfying(one -> assertThat(one.kind()).isSameAs(TYRE));
    }

    @Test
    void fittingAPartTakesOneOffTheShelf() {
        Shelf made = Shelf.of(List.of(item(LINER, 4))).took("LIN");
        assertThat(made.onHand("LIN")).isEqualTo(3);
        assertThat(made.took("LIN", 3).onHand("LIN")).isZero();
    }

    @Test
    void takingMoreThanIsThere() {
        assertThatThrownBy(() -> Shelf.of(List.of(item(LINER, 1))).took("LIN", 2))
                .isInstanceOf(UpkeepError.class)
                .hasMessageContaining("1 on hand and 2 was asked for");
    }

    @Test
    void takingSomethingThatIsNotStocked() {
        assertThatThrownBy(() -> Shelf.of(List.of(item())).took("TYR"))
                .isInstanceOf(UpkeepError.class)
                .hasMessageContaining("no TYR on the shelf");
    }

    @Test
    void aDeliveryPutsThemBack() {
        Shelf made = Shelf.of(List.of(item(LINER, 1))).received("LIN", 6);
        assertThat(made.onHand("LIN")).isEqualTo(7);
        assertThatThrownBy(() -> made.received("LIN", 0))
                .isInstanceOf(UpkeepError.class)
                .hasMessageContaining("has something in it");
    }

    @Test
    void aPartOnTheCalendarRunsOutWithoutAnyRate() {
        FittedPart made = part(SEAL);
        long left = ChronoUnit.DAYS.between(ON, made.calendarDue().orElseThrow());
        assertThat(Stock.daysUntilSpent(made, ON)).contains(left);
    }

    @Test
    void aPartOnCyclesNeedsARateBeforeItCanBeProjected() {
        FittedPart made = part(LINER);
        assertThat(Stock.daysUntilSpent(made, ON, RUN, UsageRates.NONE)).isEmpty();
        assertThat(Stock.daysUntilSpent(made, ON, RUN, RATE)).contains(100L);
    }

    @Test
    void aPartOnHoursNeedsOneToo() {
        FittedPart made = part(TYRE);
        Meters ran = Meters.ofHours(new BigDecimal("42000.0"));
        assertThat(Stock.daysUntilSpent(made, ON, ran, UsageRates.NONE)).isEmpty();
        assertThat(Stock.daysUntilSpent(made, ON, ran, UsageRates.ofHoursADay(BigDecimal.valueOf(8))))
                .contains(250L);
    }

    @Test
    void aPartAlreadySpentRunsOutTodayRatherThanYesterday() {
        FittedPart made = part(LINER);
        assertThat(Stock.daysUntilSpent(made, ON, Meters.ofCycles(9_000_000), RATE))
                .contains(0L);
    }

    /** A hundred days of cycles left and thirty one of calendar. The calendar wins. */
    @Test
    void theSoonestClockIsTheOneThatEndsIt() {
        PartKind both = PartKind.ofCycles("BOT", "both", 1_000_000).withDays(365);
        FittedPart made = part(both);
        assertThat(Stock.daysUntilSpent(made, ON, RUN, RATE)).contains(31L);
        assertThat(Stock.daysUntilSpent(made, ON, RUN, UsageRates.NONE)).contains(31L);
        PartKind cyclesOnly = PartKind.ofCycles("CYC", "cycles only", 1_000_000);
        assertThat(Stock.daysUntilSpent(part(cyclesOnly), ON, RUN, RATE)).contains(100L);
    }

    @Test
    void nothingIsShortWhenTheShelfCoversTheLine() {
        PartRegister register = PartRegister.of(List.of(part("JP-T4"), part("JP-T5")));
        Shelf shelf = Shelf.of(List.of(item(LINER, 4)));
        assertThat(shortages(shelf, register)).isEmpty();
    }

    @Test
    void aShelfThatCannotCoverTheLineIsShort() {
        PartRegister register = PartRegister.of(
                IntStream.rangeClosed(1, 4).mapToObj(one -> part("JP-T" + one)).toList());
        Shelf shelf = Shelf.of(List.of(item(LINER, 1)));
        List<Shortage> found = shortages(shelf, register);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).wanted()).isEqualTo(4);
        assertThat(found.get(0).shortBy()).isEqualTo(3);
        assertThat(found.get(0).estimated()).isTrue();
    }

    @Test
    void theReserveIsNotCountedAsAvailable() {
        PartRegister register = PartRegister.of(List.of(part("JP-T4")));
        Shelf shelf = Shelf.of(List.of(item(LINER, 2, 42, 2)));
        List<Shortage> found = shortages(shelf, register);
        assertThat(found.get(0).shortBy()).isEqualTo(1);
    }

    @Test
    void theOrderDateIsTheLeadTimeBeforeItIsWanted() {
        PartRegister register = PartRegister.of(List.of(part("JP-T4")));
        Shelf shelf = Shelf.of(List.of(item(LINER, 0, 42)));
        List<Shortage> found = shortages(shelf, register);
        assertThat(ChronoUnit.DAYS.between(ON, found.get(0).orderBy().orElseThrow()))
                .isEqualTo(100 - 42);
    }

    @Test
    void aPartNumberNobodyStocksIsStillReported() {
        PartRegister register = PartRegister.of(List.of(part("JP-T4")));
        List<Shortage> found = shortages(Shelf.of(), register);
        assertThat(found.get(0).onHand()).isZero();
        assertThat(found.get(0).kind()).isSameAs(LINER);
    }

    @Test
    void aPartThatRunsOutAfterTheWindowIsLeftAlone() {
        PartRegister register = PartRegister.of(List.of(part("JP-T4")));
        Shelf shelf = Shelf.of(List.of(item(LINER, 0)));
        assertThat(Stock.shortages(shelf, register, JOHN_PAUL, ON, RUN, RATE, 30))
                .isEmpty();
    }

    @Test
    void aPartOnAnotherMachineIsNotThisMachinesProblem() {
        PartRegister register = PartRegister.of(List.of(part(LINER, "SB-T2", STRAWBERRY, 900_000, "40000.0")));
        Shelf shelf = Shelf.of(List.of(item(LINER, 0)));
        assertThat(shortages(shelf, register)).isEmpty();
    }

    @Test
    void aShortageThatShouldAlreadyBeOnOrderSaysSo() {
        PartRegister register = PartRegister.of(List.of(part("JP-T4")));
        Shelf shelf = Shelf.of(List.of(item(LINER, 0, 200)));
        List<Shortage> found = shortages(shelf, register);
        assertThat(found.get(0).isUrgent(ON)).isTrue();
        assertThat(found.get(0).toString()).contains("order by");
        assertThat(found.get(0).toString()).contains("estimated");
        assertThat(found.get(0)).hasToString("LIN short 1, order by 2025-06-23, estimated");
    }

    @Test
    void aCalendarOnlyShortageIsNotLabelledAnEstimate() {
        PartRegister register = PartRegister.of(List.of(part(SEAL, "JP-DRV")));
        Shelf shelf = Shelf.of(List.of(item(SEAL, 0)));
        List<Shortage> found = Stock.shortages(shelf, register, JOHN_PAUL, ON, Meters.NOT_READ, UsageRates.NONE, 400);
        assertThat(found).isNotEmpty();
        assertThat(found.get(0).estimated()).isFalse();
        assertThat(found.get(0).toString()).doesNotContain("estimated");
    }
}
