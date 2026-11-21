package io.github.rkaushikdev.sheaveline.upkeep;

import static io.github.rkaushikdev.sheaveline.Hill.JOHN_PAUL;
import static io.github.rkaushikdev.sheaveline.Hill.STRAWBERRY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PartlifeTest {

    private static final LocalDate FITTED = LocalDate.of(2024, 11, 1);
    private static final LocalDate ON = LocalDate.of(2025, 1, 15);

    private static PartKind liner(long cycles) {
        return PartKind.ofCycles("LIN", "sheave liner", cycles);
    }

    private static PartKind kind() {
        return liner(1_000_000);
    }

    private static FittedPart part(
            PartKind kind, LiftCode lift, String where, LocalDate fitted, String hoursAt, long cyclesAt) {
        return new FittedPart(kind, lift, where, fitted, new BigDecimal(hoursAt), cyclesAt);
    }

    private static FittedPart part(PartKind kind, String where, long cyclesAt) {
        return part(kind, JOHN_PAUL, where, FITTED, "40000.0", cyclesAt);
    }

    private static FittedPart part(PartKind kind) {
        return part(kind, "JP-T4", 900_000);
    }

    private static FittedPart part(LiftCode lift, String where) {
        return part(kind(), lift, where, FITTED, "40000.0", 900_000);
    }

    private static FittedPart part() {
        return part(kind());
    }

    private static List<String> places(List<FittedPart> parts) {
        return parts.stream().map(FittedPart::where).toList();
    }

    @Test
    void aPartKindWearsOutOnAtLeastOneClock() {
        assertThatThrownBy(
                        () -> new PartKind("LIN", "sheave liner", Optional.empty(), Optional.empty(), Optional.empty()))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("LIN wears out on at least one clock");
        assertThatThrownBy(() -> PartKind.ofCycles("  ", "sheave liner", 1_000_000))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("a part kind has a code");
        assertThatThrownBy(() -> PartKind.ofCycles("LIN", "", 1_000_000))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("LIN has no name");
        assertThatThrownBy(() -> liner(0)).isInstanceOf(UpkeepError.class).hasMessage("LIN has a life of 0 cycles");
    }

    @Test
    void aPartKindNamesTheClocksItRunsOn() {
        assertThat(liner(1000).bases()).isEqualTo(Set.of(Basis.CYCLES));
        assertThat(PartKind.ofHours("LIN", "sheave liner", 500).bases()).isEqualTo(Set.of(Basis.HOURS));
        PartKind both = liner(1000).withHours(500).withDays(365);
        assertThat(both.bases()).isEqualTo(Set.of(Basis.HOURS, Basis.CYCLES, Basis.CALENDAR));
    }

    @Test
    void aPartKindPrintsEveryLifeItHas() {
        String printed = liner(1_000_000).withHours(500).withDays(365).toString();
        assertThat(printed).contains("500 hours");
        assertThat(printed).contains("1,000,000 cycles");
        assertThat(printed).contains("365 days");
        assertThat(printed).isEqualTo("LIN sheave liner, 500 hours or 1,000,000 cycles or 365 days");
    }

    @Test
    void aFittedPartIsFittedSomewhere() {
        assertThatThrownBy(() -> part(kind(), "  ", 900_000))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("LIN was fitted nowhere in particular");
        assertThatThrownBy(() -> part(kind(), JOHN_PAUL, "JP-T4", FITTED, "-1", 900_000))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("a meter does not read negative");
    }

    @Test
    void theUsageIsMeasuredFromTheMetersAtFitting() {
        FittedPart one = part(kind(), "JP-T4", 900_000);
        assertThat(one.cyclesUsed(1_400_000)).isEqualTo(500_000);
        assertThat(one.hoursUsed(new BigDecimal("40500.0"))).isEqualByComparingTo("500.0");
        assertThat(one.daysUsed(ON)).isEqualTo(75);
    }

    @Test
    void aMeterThatHasGoneBackwardsIsRefused() {
        FittedPart one = part();
        assertThatThrownBy(() -> one.cyclesUsed(800_000))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("the cycle counter has gone backwards");
        assertThatThrownBy(() -> one.hoursUsed(new BigDecimal("39000.0")))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("the hour meter has gone backwards");
        assertThatThrownBy(() -> one.daysUsed(LocalDate.of(2024, 1, 1)))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("that day is before the part went on");
    }

    @Test
    void halfALifeReadsAsHalfUsed() {
        FittedPart one = part(liner(1_000_000), "JP-T4", 900_000);
        assertThat(one.used(ON, Meters.ofCycles(1_400_000))).isEqualByComparingTo("0.5");
    }

    @Test
    void aPartAtTheEndOfItsLifeIsDue() {
        FittedPart one = part(liner(1_000_000), "JP-T4", 900_000);
        assertThat(one.isDue(ON, Meters.ofCycles(1_800_000))).isFalse();
        assertThat(one.isDue(ON, Meters.ofCycles(1_900_000))).isTrue();
    }

    @Test
    void aPartAtNinetyPercentWantsOrdering() {
        FittedPart one = part(liner(1_000_000), "JP-T4", 900_000);
        assertThat(one.shouldBeOrdered(ON, Meters.ofCycles(1_700_000))).isFalse();
        assertThat(one.shouldBeOrdered(ON, Meters.ofCycles(1_800_000))).isTrue();
    }

    @Test
    void theWorstClockIsTheOneThatEndsThePart() {
        FittedPart both = part(liner(1_000_000).withHours(1000).withDays(365), "JP-T4", 900_000);
        // A quarter of the hours, a tenth of the cycles, a fifth of the calendar.
        Meters now = Meters.of(new BigDecimal("40250.0"), 1_000_000);
        assertThat(both.used(ON, now)).isEqualByComparingTo("0.25");
        assertThat(both.spentClock(ON, now)).isSameAs(Basis.HOURS);
    }

    @Test
    void theCalendarCanBeTheClockThatEndsIt() {
        FittedPart perishing = part(liner(100_000_000).withHours(100_000).withDays(90), "JP-T4", 900_000);
        assertThat(perishing.spentClock(ON)).isSameAs(Basis.CALENDAR);
        assertThat(perishing.isDue(ON)).isFalse();
        assertThat(perishing.isDue(LocalDate.of(2025, 2, 15))).isTrue();
    }

    @Test
    void aClockNotGivenIsReadAsOneThatHasNotMoved() {
        FittedPart one = part(liner(1_000_000), "JP-T4", 900_000);
        assertThat(one.used(ON)).isEqualByComparingTo("0");
        assertThat(one.readings(Meters.NOT_READ))
                .isEqualTo(new FittedPart.Readings(new BigDecimal("40000.0"), 900_000));
        assertThat(one.readings(Meters.of(new BigDecimal("41000.0"), 950_000)))
                .isEqualTo(new FittedPart.Readings(new BigDecimal("41000.0"), 950_000));
    }

    @Test
    void aClockThePartDoesNotRunOnCountsForNothing() {
        FittedPart one = part(liner(1_000_000));
        assertThat(one.usedOn(Basis.HOURS, ON, Meters.ofHours(new BigDecimal("99999.0"))))
                .isEqualByComparingTo("0");
        assertThat(one.usedOn(Basis.CALENDAR, ON)).isEqualByComparingTo("0");
    }

    @Test
    void theCalendarDueDateIsWorkedOutWhereThereIsOne() {
        assertThat(part(kind().withDays(90)).calendarDue()).contains(LocalDate.of(2025, 1, 30));
        assertThat(part(liner(1000)).calendarDue()).isEmpty();
    }

    @Test
    void theRegisterKeepsPartsByMachine() {
        PartRegister held = PartRegister.of(List.of(part(JOHN_PAUL, "JP-T4"), part(STRAWBERRY, "SB-T2")));
        assertThat(held.size()).isEqualTo(2);
        assertThat(held.onLift(JOHN_PAUL)).hasSize(1);
        assertThat(held.onLift(STRAWBERRY).get(0).where()).isEqualTo("SB-T2");
    }

    @Test
    void thePartsThatAreDueAreListed() {
        PartRegister held = PartRegister.of(
                List.of(part(liner(1_000_000), "JP-T4", 900_000), part(liner(10_000_000), "JP-T5", 900_000)));
        List<FittedPart> due = held.due(JOHN_PAUL, ON, Meters.ofCycles(1_950_000));
        assertThat(places(due)).containsExactly("JP-T4");
    }

    @Test
    void thePartsToOrderAreTheOnesCloseButNotYetDone() {
        PartRegister held = PartRegister.of(
                List.of(part(liner(1_000_000), "JP-T4", 900_000), part(liner(1_000_000), "JP-T5", 1_000_000)));
        Meters at = Meters.ofCycles(1_950_000);
        assertThat(places(held.due(JOHN_PAUL, ON, at))).containsExactly("JP-T4");
        assertThat(places(held.toOrder(JOHN_PAUL, ON, at))).containsExactly("JP-T5");
    }

    @Test
    void theWorstPartOnAMachineIsFound() {
        PartRegister held = PartRegister.of(
                List.of(part(liner(1_000_000), "JP-T4", 900_000), part(liner(10_000_000), "JP-T5", 900_000)));
        Optional<FittedPart> worst = held.worst(JOHN_PAUL, ON, Meters.ofCycles(1_500_000));
        assertThat(worst).isPresent();
        assertThat(worst.get().where()).isEqualTo("JP-T4");
        assertThat(held.worst(STRAWBERRY, ON)).isEmpty();
    }

    @Test
    void replacingAPartPutsTheNewOneOnAndTakesTheOldOneOff() {
        FittedPart going = part(kind(), "JP-T4", 900_000);
        PartRegister held = PartRegister.of(List.of(going));
        FittedPart coming = part(kind(), JOHN_PAUL, "JP-T4", ON, "40000.0", 1_500_000);
        PartRegister after = held.replace(going, coming);
        assertThat(after.size()).isEqualTo(1);
        assertThat(after.onLift(JOHN_PAUL).get(0).fitted()).isEqualTo(ON);
    }

    @Test
    void replacingAPartThatIsNotOnTheRegister() {
        assertThatThrownBy(() -> PartRegister.of().replace(part(), part()))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("LIN at JP-T4 is not on the register");
    }

    @Test
    void aPartPrintsWhereItIsAndWhenItWentOn() {
        assertThat(part()).hasToString("LIN at JP-T4, fitted 2024-11-01");
    }
}
