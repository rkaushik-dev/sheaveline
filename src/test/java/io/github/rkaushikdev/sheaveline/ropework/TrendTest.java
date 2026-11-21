package io.github.rkaushikdev.sheaveline.ropework;

import static io.github.rkaushikdev.sheaveline.Hill.johnPaul;
import static io.github.rkaushikdev.sheaveline.Hill.strawberry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.plant.HaulRope;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class TrendTest {

    private static final HaulRope ROPE = johnPaul().rope();
    private static final LocalDate ON = LocalDate.of(2025, 9, 1);

    private static final List<Walked> STEADY = List.of(
            new Walked(LocalDate.of(2023, 1, 15), "1.378"),
            new Walked(LocalDate.of(2024, 1, 15), "1.354"),
            new Walked(LocalDate.of(2025, 1, 15), "1.330"));

    /** A day and the diameter read at the worst station on it. */
    private record Walked(LocalDate taken, String inches) {}

    private static Walked at(int year, int month, int day, String inches) {
        return new Walked(LocalDate.of(year, month, day), inches);
    }

    private static DiameterReading reading(String inches) {
        return new DiameterReading(Length.ofFeet(1200), Length.ofInches(inches), Length.ofInches(inches));
    }

    private static WearSurvey walk(LocalDate taken, String inches, HaulRope rope) {
        return WearSurvey.of(rope, taken, "R Halvorsen", List.of(reading(inches)));
    }

    private static List<WearSurvey> fileOf(HaulRope rope, List<Walked> pairs) {
        return pairs.stream()
                .map(pair -> walk(pair.taken(), pair.inches(), rope))
                .toList();
    }

    private static List<WearSurvey> fileOf(List<Walked> pairs) {
        return fileOf(ROPE, pairs);
    }

    private static List<WearSurvey> fileOf(Walked... pairs) {
        return fileOf(List.of(pairs));
    }

    @Test
    void aTrendIsDrawnThroughTheWorstStationOnEveryWalk() {
        WearTrend made = WearTrend.of(ROPE, fileOf(STEADY));
        assertThat(made.walks()).isEqualTo(3);
        assertThat(made.first().taken()).isEqualTo(LocalDate.of(2023, 1, 15));
        assertThat(made.last().smallest()).isEqualTo(Length.ofInches("1.330"));
    }

    @Test
    void theWalksComeBackInOrderWhicheverWayTheyAreGiven() {
        WearTrend made = WearTrend.of(ROPE, fileOf(STEADY.get(2), STEADY.get(0), STEADY.get(1)));
        assertThat(made.points().stream().map(WearPoint::taken))
                .containsExactlyElementsOf(STEADY.stream().map(Walked::taken).toList());
    }

    @Test
    void oneWalkIsNotALine() {
        assertThatThrownBy(() -> WearTrend.of(ROPE, fileOf(STEADY.get(0))))
                .isInstanceOf(RopeworkError.class)
                .hasMessageContaining("needs 2 walks");
    }

    @Test
    void twoWalksOnTheSameDayCannotBothBeTheTrend() {
        assertThatThrownBy(() -> WearTrend.of(ROPE, fileOf(STEADY.get(0), at(2023, 1, 15, "1.370"))))
                .isInstanceOf(RopeworkError.class)
                .hasMessageContaining("same day");
    }

    @Test
    void aWalkOnAnotherRopeDoesNotBelongInThisFile() {
        HaulRope other = strawberry().rope();
        assertThatThrownBy(() -> WearTrend.of(ROPE, fileOf(other, STEADY.subList(0, 2))))
                .isInstanceOf(RopeworkError.class)
                .hasMessageContaining(other.serial().toString());
    }

    @Test
    void theWearAndWhatIsLeftAreMeasuredFromTheFile() {
        WearTrend made = WearTrend.of(ROPE, fileOf(STEADY));
        assertThat(made.worn()).isEqualTo(Length.ofInches("0.048"));
        assertThat(made.left()).isEqualTo(Length.ofInches("0.048"));
        assertThat(made.daysCovered()).isEqualTo(731);
    }

    @Test
    void theRateIsWorkedOutADay() {
        WearTrend made = WearTrend.of(ROPE, fileOf(STEADY));
        BigDecimal rate = new BigDecimal(made.milsADay().doubleValue()).setScale(4, RoundingMode.HALF_EVEN);
        BigDecimal expected = new BigDecimal(48.0 / 731).setScale(4, RoundingMode.HALF_EVEN);
        assertThat(rate).isEqualByComparingTo(expected);
    }

    @Test
    void aRopeThatHasNotMeasurablyWornCarriesNoLine() {
        WearTrend flat = WearTrend.of(ROPE, fileOf(at(2024, 1, 15, "1.378"), at(2025, 1, 15, "1.378")));
        assertThat(flat.isWearing()).isFalse();
        Retirement made = new Retirement(flat, ON);
        assertThat(made.daysLeft()).isEmpty();
        assertThat(made.comesOff()).isEmpty();
        assertThat(made.orderBy()).isEmpty();
        assertThat(made.survives(LocalDate.of(2026, 4, 1))).isEmpty();
        assertThat(made.toString()).contains("not wearing measurably");
    }

    @Test
    void theProjectionReachesTheCondemningDiameter() {
        Retirement made = Trend.forecast(ROPE, fileOf(STEADY), ON);
        assertThat(made.daysLeft()).contains(731L);
        assertThat(made.comesOff()).contains(LocalDate.of(2027, 1, 16));
    }

    @Test
    void aRopeAlreadyAtTheLimitComesOffToday() {
        List<WearSurvey> worn = fileOf(at(2024, 1, 15, "1.320"), at(2025, 1, 15, "1.280"));
        Retirement made = Trend.forecast(ROPE, worn, ON);
        assertThat(made.daysLeft()).contains(0L);
        assertThat(made.comesOff()).contains(LocalDate.of(2025, 1, 15));
    }

    @Test
    void theOrderGoesInALeadTimeBeforeTheRopeComesOff() {
        Retirement made = Trend.forecast(ROPE, fileOf(STEADY), ON);
        assertThat(ChronoUnit.DAYS.between(
                        made.orderBy().orElseThrow(), made.comesOff().orElseThrow()))
                .isEqualTo(Retirement.LEAD_TIME_DAYS);
        assertThat(made.isOverdueToOrder()).isFalse();
    }

    @Test
    void aRopeWearingFastWantsOrderingAlready() {
        List<WearSurvey> fast = fileOf(at(2025, 1, 15, "1.340"), at(2025, 6, 15, "1.300"));
        Retirement made = Trend.forecast(ROPE, fast, ON);
        assertThat(made.isOverdueToOrder()).isTrue();
        assertThat(made.comesOff().orElseThrow()).isBefore(LocalDate.of(2026, 1, 1));
    }

    @Test
    void aShorterLeadTimeMovesTheOrderDateAndNothingElse() {
        Retirement slow = Trend.forecast(ROPE, fileOf(STEADY), ON, 30);
        Retirement longer = Trend.forecast(ROPE, fileOf(STEADY), ON, 300);
        assertThat(slow.comesOff()).isEqualTo(longer.comesOff());
        assertThat(slow.orderBy().orElseThrow()).isAfter(longer.orderBy().orElseThrow());
    }

    @Test
    void aLeadTimeNobodyHas() {
        assertThatThrownBy(() -> Trend.forecast(ROPE, fileOf(STEADY), ON, -1))
                .isInstanceOf(RopeworkError.class)
                .hasMessageContaining("not negative");
    }

    @Test
    void whetherTheRopeReachesTheEndOfASeason() {
        Retirement made = Trend.forecast(ROPE, fileOf(STEADY), ON);
        assertThat(made.survives(LocalDate.of(2026, 4, 15))).contains(true);
        assertThat(made.survives(LocalDate.of(2028, 4, 15))).contains(false);
    }

    @Test
    void howMuchOfALineTheFileCarriesIsSaidInOneWord() {
        Retirement two = Trend.forecast(ROPE, fileOf(STEADY.subList(0, 2)), ON);
        assertThat(two.confidence()).isEqualTo("two points");
        assertThat(Trend.forecast(ROPE, fileOf(STEADY), ON).confidence()).isEqualTo("provisional");
        List<WearSurvey> many = fileOf(
                at(2021, 1, 15, "1.378"),
                at(2022, 1, 15, "1.366"),
                at(2023, 1, 15, "1.354"),
                at(2024, 1, 15, "1.342"),
                at(2025, 1, 15, "1.330"));
        assertThat(Trend.forecast(ROPE, many, ON).confidence()).isEqualTo("settled");
    }

    @Test
    void aSettledFileNeedsAYearBehindItAsWellAsFiveWalks() {
        List<WearSurvey> tight = fileOf(
                at(2025, 1, 1, "1.378"),
                at(2025, 2, 1, "1.374"),
                at(2025, 3, 1, "1.370"),
                at(2025, 4, 1, "1.366"),
                at(2025, 5, 1, "1.362"));
        assertThat(Trend.forecast(ROPE, tight, ON).confidence()).isEqualTo("provisional");
    }

    @Test
    void theRopesThatWantOrderingComeBackSoonestFirst() {
        List<WearSurvey> fast = fileOf(at(2025, 1, 15, "1.340"), at(2025, 6, 15, "1.300"));
        List<WearSurvey> slower = fileOf(at(2025, 1, 15, "1.350"), at(2025, 6, 15, "1.320"));
        List<Retirement> found = Trend.orderable(
                Stream.of(fileOf(STEADY), fast, slower)
                        .map(walks -> new Trend.RopeWalks(ROPE, walks))
                        .toList(),
                ON);
        assertThat(found).hasSize(2);
        assertThat(found.get(0).comesOff().orElseThrow())
                .isBefore(found.get(1).comesOff().orElseThrow());
    }

    @Test
    void aRopeWithOneWalkIsLeftOutRatherThanGuessedAt() {
        assertThat(Trend.orderable(List.of(new Trend.RopeWalks(ROPE, fileOf(STEADY.get(0)))), ON))
                .isEmpty();
    }

    @Test
    void aForecastPrintsWhatThePurchaseOrderNeeds() {
        String printed = Trend.forecast(ROPE, fileOf(STEADY), ON).toString();
        assertThat(printed).contains(ROPE.serial().toString());
        assertThat(printed).contains("order by");
    }
}
