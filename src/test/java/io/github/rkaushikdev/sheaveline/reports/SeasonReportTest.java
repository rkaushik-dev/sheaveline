package io.github.rkaushikdev.sheaveline.reports;

import static io.github.rkaushikdev.sheaveline.Hill.JOHN_PAUL;
import static io.github.rkaushikdev.sheaveline.Hill.STRAWBERRY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.github.rkaushikdev.sheaveline.HillCalendar;
import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.ops.StopKind;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.schedule.Season;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SeasonReportTest {

    private static final Season SEASON = HillCalendar.theSeason();

    private static Day day() {
        return new Day();
    }

    private static SeasonSummary summary(Day... days) {
        List<DayRecord> records = new ArrayList<>();
        for (Day one : days) {
            records.add(one.record());
        }
        if (records.isEmpty()) {
            records.add(day().record());
        }
        return SeasonSummary.of(SEASON, records);
    }

    @Test
    void aDayWithNothingChargedIsFullyAvailable() {
        assertThat(day().record().availability()).isEqualByComparingTo("1");
    }

    @Test
    void aDayWithAnHourChargedLosesASeventh() {
        DayRecord day = day().scheduledHours(7).charged(60).record();
        assertThat(Decimals.quantize(day.availability(), 3)).isEqualByComparingTo("0.857");
    }

    @Test
    void aDayThatWasNeverScheduledHasNoAvailability() {
        DayRecord day = new DayRecord(
                JOHN_PAUL,
                LocalDate.of(2025, 1, 15),
                false,
                Duration.ZERO,
                Duration.ZERO,
                Duration.ZERO,
                Duration.ZERO);
        assertThat(day.availability()).isEqualByComparingTo("0");
    }

    @Test
    void theMachinesComeBackInCodeOrder() {
        SeasonSummary held = summary(day().lift(STRAWBERRY), day().lift(JOHN_PAUL));
        assertThat(held.lifts()).containsExactly(JOHN_PAUL, STRAWBERRY);
    }

    @Test
    void theDaysAMachineOpenedAndDidNot() {
        SeasonSummary held = summary(
                day().on(LocalDate.of(2025, 1, 15)),
                day().on(LocalDate.of(2025, 1, 16)).shut().scheduledHours(0),
                day().on(LocalDate.of(2025, 1, 17)));
        assertThat(held.daysOpen(JOHN_PAUL)).isEqualTo(2);
        assertThat(held.daysShut(JOHN_PAUL)).isEqualTo(1);
    }

    @Test
    void theTotalsAddUpAcrossTheSeason() {
        SeasonSummary held = summary(
                day().on(LocalDate.of(2025, 1, 15)).charged(30),
                day().on(LocalDate.of(2025, 1, 16)).charged(90));
        assertThat(held.scheduled(JOHN_PAUL)).isEqualTo(Duration.ofHours(14));
        assertThat(held.chargeable(JOHN_PAUL)).isEqualTo(Duration.ofHours(2));
        assertThat(held.downtime(JOHN_PAUL)).isEqualTo(Duration.ofHours(2));
        assertThat(held.running(JOHN_PAUL)).isEqualTo(Duration.ofHours(12));
    }

    @Test
    void availabilityChargesOnlyTheMachinesOwnStops() {
        SeasonSummary held =
                summary(day().on(LocalDate.of(2025, 1, 15)).down(120).charged(30));
        assertThat(held.downtime(JOHN_PAUL)).isEqualTo(Duration.ofHours(2));
        assertThat(held.chargeable(JOHN_PAUL)).isEqualTo(Duration.ofMinutes(30));
        assertThat(Decimals.quantize(held.availability(JOHN_PAUL), 3)).isEqualByComparingTo("0.929");
    }

    @Test
    void aMachineWithNothingScheduledHasNoAvailability() {
        SeasonSummary held = summary(day().shut().scheduledHours(0));
        assertThat(held.availability(JOHN_PAUL)).isEqualByComparingTo("0");
    }

    @Test
    void theWorstDayIsTheOneThatCostTheMost() {
        SeasonSummary held = summary(
                day().on(LocalDate.of(2025, 1, 15)).charged(30),
                day().on(LocalDate.of(2025, 1, 16)).charged(180),
                day().on(LocalDate.of(2025, 1, 17)).charged(45));
        assertThat(held.worstDay(JOHN_PAUL)).isPresent();
        assertThat(held.worstDay(JOHN_PAUL).orElseThrow().day()).isEqualTo(LocalDate.of(2025, 1, 16));
        assertThat(held.worstDay(STRAWBERRY)).isEmpty();
    }

    @Test
    void theStopsAreCountedByKindAMachine() {
        SeasonSummary held = summary(
                day().stops(StopKind.GATE, StopKind.GATE, StopKind.WEATHER),
                day().on(LocalDate.of(2025, 1, 16)).stops(StopKind.GATE));
        Map<StopKind, Integer> counted = held.stopsByKind(JOHN_PAUL);
        assertThat(counted.get(StopKind.GATE)).isEqualTo(3);
        assertThat(counted.get(StopKind.WEATHER)).isEqualTo(1);
        assertThat(held.stopsByKind(STRAWBERRY)).isEmpty();
    }

    @Test
    void theHillAvailabilityReadsEveryMachineAtOnce() {
        SeasonSummary held =
                summary(day().lift(JOHN_PAUL).charged(0), day().lift(STRAWBERRY).charged(420));
        assertThat(held.hillAvailability()).isEqualByComparingTo("0.5");
    }

    @Test
    void aSeasonWithNothingInItHasNoHillAvailability() {
        assertThat(SeasonSummary.of(SEASON, List.of()).hillAvailability()).isEqualByComparingTo("0");
    }

    @Test
    void thePageHeadsWithTheSeason() {
        String text = summary().text();
        assertThat(text).contains("SEASON SUMMARY 2024/25");
        assertThat(text).contains("opened                   2024-11-29");
        assertThat(text).contains("closed                   2025-04-20");
    }

    @Test
    void everyMachineGetsARowOnThePage() {
        String text = summary(day().lift(JOHN_PAUL), day().lift(STRAWBERRY)).text();
        assertThat(text).contains("by machine");
        assertThat(text).contains("\nJP ");
        assertThat(text).contains("\nSB ");
    }

    @Test
    void theWorstDaySectionNamesTheDay() {
        String text = summary(day().on(LocalDate.of(2025, 1, 16)).charged(180)).text();
        assertThat(text).contains("worst day a machine");
        assertThat(text).contains("2025-01-16");
    }

    @Test
    void theStopTableHasAColumnAMachine() {
        String text = summary(
                        day().lift(JOHN_PAUL).stops(StopKind.GATE),
                        day().lift(STRAWBERRY).stops(StopKind.WEATHER))
                .text();
        assertThat(text).contains("stops by kind");
        assertThat(text).contains("loading gate");
        assertThat(text).contains("weather");
    }

    @Test
    void theWholePageFitsEightyColumns() {
        List<Day> days = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            LocalDate on = LocalDate.of(2025, 1, 15).plusDays(index);
            days.add(day().lift(JOHN_PAUL).on(on).charged(index).stops(StopKind.GATE));
            days.add(day().lift(STRAWBERRY).on(on).charged(index * 2L));
        }
        SeasonSummary page = summary(days.toArray(Day[]::new));
        assertThat(page.render().lines())
                .allSatisfy(one -> assertThat(one.length()).isLessThanOrEqualTo(Forms.PAGE_WIDTH));
    }

    @Test
    void theRecordsComeBackSortedByDayThenMachine() {
        SeasonSummary held = summary(
                day().lift(STRAWBERRY).on(LocalDate.of(2025, 1, 16)),
                day().lift(JOHN_PAUL).on(LocalDate.of(2025, 1, 16)),
                day().lift(JOHN_PAUL).on(LocalDate.of(2025, 1, 15)));
        assertThat(held.records())
                .extracting(one -> one.day().getDayOfMonth(), one -> one.lift().value())
                .containsExactly(tuple(15, "JP"), tuple(16, "JP"), tuple(16, "SB"));
    }

    @Test
    void everyMachineIsTalliedOnceOnTheWayIn() {
        SeasonSummary held = summary(
                day().lift(JOHN_PAUL).on(LocalDate.of(2025, 1, 15)),
                day().lift(JOHN_PAUL).on(LocalDate.of(2025, 1, 16)),
                day().lift(STRAWBERRY).on(LocalDate.of(2025, 1, 15)));
        assertThat(held.tallies()).containsOnlyKeys(JOHN_PAUL, STRAWBERRY);
        assertThat(held.tally(JOHN_PAUL).days()).hasSize(2);
        assertThat(held.tally(STRAWBERRY).days()).hasSize(1);
    }

    @Test
    void theTallyHoldsTheSameAnswersTheSummaryGives() {
        SeasonSummary held = summary(
                day().on(LocalDate.of(2025, 1, 15)).scheduledHours(7).charged(30),
                day().on(LocalDate.of(2025, 1, 16)).scheduledHours(8).shut());
        LiftTally one = held.tally(JOHN_PAUL);
        assertThat(one.daysOpen()).isEqualTo(held.daysOpen(JOHN_PAUL));
        assertThat(one.daysShut()).isEqualTo(held.daysShut(JOHN_PAUL));
        assertThat(one.scheduled()).isEqualTo(held.scheduled(JOHN_PAUL));
        assertThat(one.running()).isEqualTo(held.running(JOHN_PAUL));
        assertThat(one.downtime()).isEqualTo(held.downtime(JOHN_PAUL));
        assertThat(one.chargeable()).isEqualTo(held.chargeable(JOHN_PAUL));
        assertThat(one.availability()).isEqualTo(held.availability(JOHN_PAUL));
        assertThat(one.worstDay()).isEqualTo(held.worstDay(JOHN_PAUL));
    }

    @Test
    void aMachineWithNoDaysTalliesToNothingRatherThanFailing() {
        SeasonSummary held = summary(day().lift(JOHN_PAUL));
        LiftTally empty = held.tally(STRAWBERRY);
        assertThat(empty.days()).isEmpty();
        assertThat(empty.daysOpen()).isZero();
        assertThat(empty.scheduled().isZero()).isTrue();
        assertThat(empty.availability()).isEqualByComparingTo("0");
        assertThat(empty.worstDay()).isEmpty();
    }

    @Test
    void theStopCountsAreAddedUpOnceAndHandedOutAsCopies() {
        SeasonSummary held = summary(
                day().stops(StopKind.WEATHER, StopKind.WEATHER),
                day().on(LocalDate.of(2025, 1, 16)).stops(StopKind.WEATHER, StopKind.POWER));
        assertThat(held.tally(JOHN_PAUL).stops()).isEqualTo(Map.of(StopKind.WEATHER, 3, StopKind.POWER, 1));
        Map<StopKind, Integer> handed = held.stopsByKind(JOHN_PAUL);
        handed.put(StopKind.WEATHER, 99);
        assertThat(held.tally(JOHN_PAUL).stops().get(StopKind.WEATHER)).isEqualTo(3);
    }

    @Test
    void theRecordsAreOnlyWalkedOnceHoweverManyQuestionsAreAsked() {
        List<Day> days = new ArrayList<>();
        for (LiftCode lift : List.of(JOHN_PAUL, STRAWBERRY)) {
            for (int offset = 0; offset < 40; offset++) {
                days.add(day().lift(lift).on(LocalDate.of(2025, 1, 1).plusDays(offset)));
            }
        }
        SeasonSummary held = summary(days.toArray(Day[]::new));
        // The same tallies with no records behind them: a page that walked the records again would
        // come out different, or come out empty.
        SeasonSummary talliesAlone = new SeasonSummary(SEASON, List.of(), held.tallies());
        assertThat(talliesAlone.text()).isEqualTo(held.text());
        assertThat(talliesAlone.text()).contains("\nJP ").contains("\nSB ");
    }

    /** One day record, built the way a test wants it. */
    private static final class Day {

        private LiftCode lift = JOHN_PAUL;
        private LocalDate on = LocalDate.of(2025, 1, 15);
        private boolean opened = true;
        private long scheduledHours = 7;
        private long chargedMinutes;
        private long downMinutes;
        private List<StopKind> stops = List.of();

        Day lift(LiftCode value) {
            lift = value;
            return this;
        }

        Day on(LocalDate value) {
            on = value;
            return this;
        }

        Day shut() {
            opened = false;
            return this;
        }

        Day scheduledHours(long value) {
            scheduledHours = value;
            return this;
        }

        Day charged(long minutes) {
            chargedMinutes = minutes;
            return this;
        }

        Day down(long minutes) {
            downMinutes = minutes;
            return this;
        }

        Day stops(StopKind... kinds) {
            stops = List.of(kinds);
            return this;
        }

        DayRecord record() {
            Duration scheduled = Duration.ofHours(scheduledHours);
            Duration downtime = Duration.ofMinutes(downMinutes != 0 ? downMinutes : chargedMinutes);
            return new DayRecord(
                    lift,
                    on,
                    opened,
                    scheduled,
                    scheduled.minus(downtime),
                    downtime,
                    Duration.ofMinutes(chargedMinutes),
                    stops);
        }
    }
}
