package io.github.rkaushikdev.sheaveline.ropework;

import static io.github.rkaushikdev.sheaveline.Hill.johnPaul;
import static io.github.rkaushikdev.sheaveline.Hill.strawberry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.plant.HaulRope;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DressingTest {

    private static final HaulRope ROPE = johnPaul().rope();
    private static final String SERIAL = ROPE.serial().toString();
    private static final LocalDate ON = LocalDate.of(2025, 1, 15);
    private static final Length MILE = Length.ofFeet(5280);

    private static Dressing applied(long miles, String product, String serial) {
        return new Dressing(serial, ON, MILE.scaled(miles), product, "R Halvorsen");
    }

    private static Dressing applied(long miles) {
        return applied(miles, "Fuchs Ceplattyn", SERIAL);
    }

    private static DressingLog log(Length interval, Dressing... dressings) {
        return DressingLog.of(ROPE, List.of(dressings), interval);
    }

    private static DressingLog log(Dressing... dressings) {
        return log(DressingLog.DRESSING_INTERVAL, dressings);
    }

    private static Length miles(long count) {
        return MILE.scaled(count);
    }

    private static DiameterReading reading(String inches) {
        return new DiameterReading(Length.ofFeet(1200), Length.ofInches(inches), Length.ofInches(inches));
    }

    private static List<WearSurvey> walks() {
        return List.of(
                WearSurvey.of(ROPE, LocalDate.of(2023, 1, 15), "R Halvorsen", List.of(reading("1.378"))),
                WearSurvey.of(ROPE, LocalDate.of(2025, 1, 15), "R Halvorsen", List.of(reading("1.330"))));
    }

    @Test
    void aDressingNamesWhatWentOnAndWhoPutItOn() {
        assertThatThrownBy(() -> applied(0, "  ", SERIAL))
                .isInstanceOf(RopeworkError.class)
                .hasMessageContaining("names what went on");
        assertThatThrownBy(() -> new Dressing(SERIAL, ON, new Length(-1), "Ceplattyn", "R Halvorsen"))
                .isInstanceOf(RopeworkError.class)
                .hasMessageContaining("negative");
    }

    @Test
    void aDressingSaysHowFarTheRopeHadRun() {
        assertThat(applied(12_500).milesAt()).isEqualByComparingTo("12500");
        assertThat(applied(12_500).toString()).contains("12500 miles");
    }

    @Test
    void aDressingFromAnotherRopeDoesNotBelongInThisLog() {
        String other = strawberry().rope().serial().toString();
        assertThatThrownBy(() -> log(applied(0, "Fuchs Ceplattyn", other)))
                .isInstanceOf(RopeworkError.class)
                .hasMessageContaining(other);
    }

    @Test
    void aRopeCannotBeDressedAtADistanceItHasNotRun() {
        assertThatThrownBy(() -> new DressingLog(ROPE, List.of(applied(20_000), applied(5_000))))
                .isInstanceOf(RopeworkError.class)
                .hasMessageContaining("has not run");
    }

    @Test
    void theApplicationsComeBackInTheOrderTheyWentOn() {
        DressingLog made = log(applied(20_000), applied(5_000));
        assertThat(made.dressings().stream().map(Dressing::milesAt))
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(BigDecimal.valueOf(5_000), BigDecimal.valueOf(20_000));
        assertThat(made.last().orElseThrow().milesAt()).isEqualByComparingTo("20000");
    }

    @Test
    void aRopeNobodyHasDressedIsMeasuredFromTheDayItWentOn() {
        DressingLog made = log();
        assertThat(made.last()).isEmpty();
        assertThat(made.travelSince(miles(10_000))).isEqualTo(miles(10_000));
        assertThat(made.toString()).contains("never been dressed");
    }

    @Test
    void theIntervalIsMeasuredInMilesAndNotInWeeks() {
        assertThat(Length.ofFeet(5280 * 25_000)).isEqualTo(DressingLog.DRESSING_INTERVAL);
        DressingLog made = log(applied(0));
        assertThat(made.isDue(miles(24_000))).isFalse();
        assertThat(made.isDue(miles(25_000))).isTrue();
    }

    @Test
    void whatIsLeftBeforeItIsDue() {
        DressingLog made = log(applied(10_000));
        assertThat(made.travelUntilDue(miles(20_000))).isEqualTo(miles(15_000));
        assertThat(made.travelUntilDue(miles(60_000))).isEqualTo(new Length(0));
    }

    @Test
    void aMeterThatHasGoneBackwardsSinceTheLastDressing() {
        DressingLog made = log(applied(20_000));
        assertThatThrownBy(() -> made.travelSince(miles(10_000)))
                .isInstanceOf(RopeworkError.class)
                .hasMessageContaining("gone backwards");
    }

    @Test
    void aRopeInsideItsIntervalCarriesNoPenalty() {
        DressingLog made = log(applied(0));
        assertThat(made.intervalsOverdue(miles(24_000))).isEqualByComparingTo("0");
        assertThat(made.wearPenalty(miles(24_000))).isEqualByComparingTo("1");
    }

    @Test
    void aRopeOneIntervalLateWearsFaster() {
        DressingLog made = log(applied(0));
        BigDecimal penalty = made.wearPenalty(miles(50_000));
        assertThat(penalty).isGreaterThan(BigDecimal.ONE);
        assertThat(penalty).isEqualByComparingTo("1.35");
    }

    @Test
    void thePenaltyStopsRatherThanBeingInventedPastTheData() {
        DressingLog made = log(applied(0));
        assertThat(made.wearPenalty(miles(1_000_000))).isEqualByComparingTo(DressingLog.WORST_PENALTY);
    }

    @Test
    void aShorterIntervalMakesTheSameMileageLater() {
        Length steady = miles(30_000);
        assertThat(log(applied(0)).wearPenalty(steady)).isGreaterThan(BigDecimal.ONE);
        assertThat(log(miles(40_000), applied(0)).wearPenalty(steady)).isEqualByComparingTo("1");
    }

    @Test
    void anIntervalThatIsNotADistance() {
        assertThatThrownBy(() -> log(new Length(0)))
                .isInstanceOf(RopeworkError.class)
                .hasMessageContaining("is a distance");
    }

    @Test
    void aDryRopeComesOffSoonerThanTheFileAloneWouldSay() {
        Retirement lookedAfter = Trend.forecast(ROPE, walks(), ON);
        Retirement neglected = Trend.forecast(ROPE, walks(), ON, new BigDecimal("1.35"));
        assertThat(neglected.daysLeft().orElseThrow())
                .isLessThan(lookedAfter.daysLeft().orElseThrow());
        assertThat(neglected.comesOff().orElseThrow())
                .isBefore(lookedAfter.comesOff().orElseThrow());
        assertThat(neglected.isPenalised()).isTrue();
        assertThat(lookedAfter.isPenalised()).isFalse();
    }

    @Test
    void lookingAfterARopeDoesNotMakeItWearMoreSlowly() {
        assertThatThrownBy(() -> Trend.forecast(ROPE, walks(), ON, new BigDecimal("0.5")))
                .isInstanceOf(RopeworkError.class)
                .hasMessageContaining("more slowly");
    }

    @Test
    void thePenaltyFromTheLogIsTheOneTheForecastTakes() {
        DressingLog made = log(applied(0));
        BigDecimal penalty = made.wearPenalty(miles(50_000));
        assertThat(Trend.forecast(ROPE, walks(), ON, penalty).penalty()).isEqualByComparingTo("1.35");
    }
}
