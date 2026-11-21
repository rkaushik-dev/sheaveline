package io.github.rkaushikdev.sheaveline.ropework;

import static io.github.rkaushikdev.sheaveline.Hill.jpCarriers;
import static io.github.rkaushikdev.sheaveline.Hill.sbCarriers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.measures.Torque;
import io.github.rkaushikdev.sheaveline.plant.CarrierSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RetorqueTest {

    private static final Torque SPEC = Torque.ofInchPounds(450);
    private static final LocalDate CHECKED = LocalDate.of(2025, 1, 15);

    private static TorqueCheck check(int carrier, long found, LocalDate checked, Optional<Long> reset) {
        return new TorqueCheck(
                carrier, checked, "M Vetter", SPEC, Torque.ofInchPounds(found), reset.map(Torque::ofInchPounds));
    }

    private static TorqueCheck check(int carrier, long found, LocalDate checked) {
        return check(carrier, found, checked, Optional.empty());
    }

    private static TorqueCheck check(int carrier, long found) {
        return check(carrier, found, CHECKED);
    }

    private static TorqueCheck reset(int carrier, long found, long reset) {
        return check(carrier, found, CHECKED, Optional.of(reset));
    }

    private static TorqueCheck found(long found) {
        return check(1, found);
    }

    private static RetorqueCycle cycle(CarrierSet fleet, int interval, List<TorqueCheck> checks) {
        return RetorqueCycle.of(fleet, SPEC, checks, interval);
    }

    private static RetorqueCycle cycle(List<TorqueCheck> checks) {
        return cycle(sbCarriers(), 365, checks);
    }

    private static RetorqueCycle cycle(TorqueCheck... checks) {
        return cycle(List.of(checks));
    }

    private static List<TorqueCheck> round(int from, int to, long found) {
        return IntStream.rangeClosed(from, to)
                .mapToObj(number -> check(number, found))
                .toList();
    }

    private static List<TorqueCheck> joined(TorqueCheck first, List<TorqueCheck> rest) {
        List<TorqueCheck> all = new ArrayList<>();
        all.add(first);
        all.addAll(rest);
        return all;
    }

    @Test
    void aCheckChecksItsOwnRecord() {
        assertThatThrownBy(() -> check(0, 450)).isInstanceOf(RopeworkError.class);
        assertThatThrownBy(() -> new TorqueCheck(1, CHECKED, " ", SPEC, SPEC)).isInstanceOf(RopeworkError.class);
        assertThatThrownBy(() -> new TorqueCheck(1, CHECKED, "M Vetter", new Torque(0), SPEC))
                .isInstanceOf(RopeworkError.class);
    }

    @Test
    void aGripInsideATenthOfTheFigureIsInBand() {
        assertThat(found(450).withinBand()).isTrue();
        assertThat(found(410).withinBand()).isTrue();
        assertThat(found(495).withinBand()).isTrue();
        assertThat(found(390).withinBand()).isFalse();
    }

    @Test
    void slackMeansBelowTheBandAndNotAboveIt() {
        assertThat(found(380).foundSlack()).isTrue();
        assertThat(found(520).foundSlack()).isFalse();
        assertThat(found(430).foundSlack()).isFalse();
    }

    @Test
    void theShortfallIsHowFarUnderItRead() {
        assertThat(found(380).shortfall()).isEqualTo(Torque.ofInchPounds(70));
    }

    @Test
    void aGripLeftAsFoundIsOnlyCorrectIfItWasInBand() {
        assertThat(found(440).leftCorrect()).isTrue();
        assertThat(found(380).leftCorrect()).isFalse();
        assertThat(found(380).wasReset()).isFalse();
    }

    @Test
    void aGripResetToTheFigureIsCorrectHoweverItWasFound() {
        TorqueCheck reset = reset(1, 380, 450);
        assertThat(reset.wasReset()).isTrue();
        assertThat(reset.leftCorrect()).isTrue();
        assertThat(reset.foundSlack()).isTrue();
    }

    @Test
    void aGripResetToTheWrongFigureIsStillWrong() {
        assertThat(reset(1, 380, 390).leftCorrect()).isFalse();
    }

    @Test
    void aCheckPrintsWhatTheWrenchFound() {
        assertThat(check(7, 380)).hasToString("carrier 7 found 380 in lb slack");
        assertThat(check(7, 445)).hasToString("carrier 7 found 445 in lb in band");
    }

    @Test
    void onlyAFixedGripFleetGoesOnARetorqueCycle() {
        assertThatThrownBy(() -> cycle(jpCarriers(), 365, List.of())).isInstanceOf(RopeworkError.class);
    }

    @Test
    void aCycleChecksItsIntervalAndItsCarriers() {
        assertThatThrownBy(() -> cycle(sbCarriers(), 0, List.of())).isInstanceOf(RopeworkError.class);
        assertThatThrownBy(() -> cycle(check(900, 450))).isInstanceOf(RopeworkError.class);
    }

    @Test
    void aCleanRoundLeavesTheIntervalAlone() {
        RetorqueCycle walked = cycle(round(1, 20, 445));
        assertThat(walked.slackRate()).isEqualByComparingTo("0");
        assertThat(walked.intervalDays()).isEqualTo(365);
    }

    @Test
    void oneSlackGripInTwentyLeavesTheIntervalAlone() {
        RetorqueCycle walked = cycle(joined(check(1, 380), round(2, 20, 445)));
        assertThat(walked.slackRate()).isEqualByComparingTo("0.05");
        assertThat(walked.intervalDays()).isEqualTo(365);
    }

    @Test
    void moreThanOneInTenSlackHalvesTheInterval() {
        List<TorqueCheck> checks = new ArrayList<>(round(1, 3, 380));
        checks.add(check(4, 445));
        RetorqueCycle walked = cycle(checks);
        assertThat(walked.slackRate()).isEqualByComparingTo("0.75");
        assertThat(walked.intervalDays()).isEqualTo(182);
    }

    @Test
    void theShortenedIntervalPullsGripsIntoBeingOverdue() {
        List<TorqueCheck> checks = IntStream.rangeClosed(1, 3)
                .mapToObj(number -> check(number, 380, LocalDate.of(2024, 6, 1)))
                .toList();
        RetorqueCycle tight = cycle(checks);
        RetorqueCycle loose = cycle(check(1, 445, LocalDate.of(2024, 6, 1)));
        LocalDate on = LocalDate.of(2025, 1, 15);
        assertThat(tight.overdue(on)).contains(1);
        assertThat(loose.overdue(on)).isEmpty();
    }

    @Test
    void anEmptyCycleHasNoSlackRateAndTheBaseInterval() {
        assertThat(cycle().slackRate()).isEqualByComparingTo("0");
        assertThat(cycle().intervalDays()).isEqualTo(365);
    }

    @Test
    void theLastCheckOnACarrierIsTheMostRecentOne() {
        RetorqueCycle walked = cycle(check(3, 400, LocalDate.of(2023, 5, 1)), check(3, 445, LocalDate.of(2024, 5, 1)));
        Optional<TorqueCheck> last = walked.lastCheck(3);
        assertThat(last).isPresent();
        assertThat(last.get().checked()).isEqualTo(LocalDate.of(2024, 5, 1));
        assertThat(walked.lastCheck(4)).isEmpty();
    }

    @Test
    void aGripFallsDueAnIntervalAfterItWasLastDone() {
        RetorqueCycle walked = cycle(check(3, 445, LocalDate.of(2024, 5, 1)));
        assertThat(walked.dueOn(3, LocalDate.of(2025, 1, 15))).isEqualTo(LocalDate.of(2025, 5, 1));
    }

    @Test
    void aGripNeverCheckedFallsDueAnIntervalFromToday() {
        RetorqueCycle walked = cycle(check(3, 445, LocalDate.of(2024, 5, 1)));
        assertThat(walked.dueOn(9, LocalDate.of(2025, 1, 15))).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(walked.neverChecked()).contains(9);
        assertThat(walked.neverChecked()).doesNotContain(3);
    }

    @Test
    void gripsLeftOutsideTheBandAreListed() {
        RetorqueCycle walked = cycle(check(1, 380), reset(2, 380, 450), check(3, 445));
        assertThat(walked.leftWrong().stream().map(TorqueCheck::carrier)).containsExactly(1);
    }

    @Test
    void addingACheckCanChangeTheInterval() {
        RetorqueCycle walked = cycle(check(1, 445));
        assertThat(walked.intervalDays()).isEqualTo(365);
        RetorqueCycle after = walked.withCheck(check(2, 370));
        assertThat(after.intervalDays()).isEqualTo(182);
    }

    @Test
    void aCyclePrintsAOneLineSummary() {
        RetorqueCycle walked = cycle(check(1, 380), check(2, 445));
        assertThat(walked).hasToString("2 checks, 1 slack, interval 182 days");
    }
}
