package io.github.rkaushikdev.sheaveline.ops;

import static io.github.rkaushikdev.sheaveline.Hill.JOHN_PAUL;
import static io.github.rkaushikdev.sheaveline.Hill.STRAWBERRY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StopTest {

    private static LocalDateTime at(int hour, int minute) {
        return LocalDateTime.of(2025, 1, 15, hour, minute);
    }

    private static LocalDateTime at(int hour) {
        return at(hour, 0);
    }

    private static StopEvent stop(
            StopKind kind,
            LocalDateTime stopped,
            Optional<LocalDateTime> restarted,
            String note,
            boolean checked,
            LiftCode lift) {
        return new StopEvent(
                lift, kind, stopped, "K Ostler", note, restarted, restarted.map(when -> "K Ostler"), checked);
    }

    private static StopEvent stop() {
        return stop(StopKind.OPERATOR);
    }

    private static StopEvent stop(StopKind kind) {
        return stop(kind, "");
    }

    private static StopEvent stop(StopKind kind, String note) {
        return stop(kind, at(10), Optional.empty(), note, false, JOHN_PAUL);
    }

    private static StopEvent stopped(LocalDateTime stopped) {
        return stop(StopKind.OPERATOR, stopped, Optional.empty(), "", false, JOHN_PAUL);
    }

    private static StopEvent between(LocalDateTime stopped, LocalDateTime restarted) {
        return between(StopKind.OPERATOR, stopped, restarted);
    }

    private static StopEvent between(StopKind kind, LocalDateTime stopped, LocalDateTime restarted) {
        return stop(kind, stopped, Optional.of(restarted), "", false, JOHN_PAUL);
    }

    @Test
    void theBoardHearsAboutSomeKindsAndNotOthers() {
        assertThat(StopKind.DEROPEMENT.isReportable()).isTrue();
        assertThat(StopKind.EVACUATION.isReportable()).isTrue();
        assertThat(StopKind.ROPE_POSITION.isReportable()).isTrue();
        assertThat(StopKind.GATE.isReportable()).isFalse();
        assertThat(StopKind.POWER.isReportable()).isFalse();
    }

    @Test
    void aPowerCutAndAWindHoldDoNotCountAgainstTheMachine() {
        assertThat(StopKind.POWER.countsAgainstTheMachine()).isFalse();
        assertThat(StopKind.WEATHER.countsAgainstTheMachine()).isFalse();
        assertThat(StopKind.SCHEDULED.countsAgainstTheMachine()).isFalse();
        assertThat(StopKind.GATE.countsAgainstTheMachine()).isTrue();
        assertThat(StopKind.SAFETY_CIRCUIT.countsAgainstTheMachine()).isTrue();
    }

    @Test
    void aStopIsSignedAndOrdered() {
        assertThatThrownBy(() -> new StopEvent(JOHN_PAUL, StopKind.OPERATOR, at(10), "  "))
                .isInstanceOf(OperationsError.class)
                .hasMessage("a stop is signed by whoever called it");
        assertThatThrownBy(() -> between(at(12), at(10)))
                .isInstanceOf(OperationsError.class)
                .hasMessage("a stop cannot end before it started");
    }

    @Test
    void aRestartCarriesATimeAndANameOrNeither() {
        assertThatThrownBy(() -> new StopEvent(
                        JOHN_PAUL,
                        StopKind.OPERATOR,
                        at(10),
                        "K Ostler",
                        "",
                        Optional.of(at(11)),
                        Optional.empty(),
                        false))
                .isInstanceOf(OperationsError.class)
                .hasMessage("a restart has a time and a name, or neither");
    }

    @Test
    void aReportableStopIsWrittenUp() {
        assertThatThrownBy(() -> stop(StopKind.DEROPEMENT))
                .isInstanceOf(OperationsError.class)
                .hasMessage("a deropement is written up, not just logged");
        assertThat(stop(StopKind.DEROPEMENT, "carrier 34 off at T4").kind().isReportable())
                .isTrue();
    }

    @Test
    void aShortOrdinaryStopNeedsNothingBeforeItRunsAgain() {
        StopEvent quick = between(at(10), at(10, 3));
        assertThat(quick.needsALineCheck()).isFalse();
        assertThat(quick.length()).isEqualTo(Duration.ofMinutes(3));
    }

    @Test
    void aLongStopNeedsTheLineRiddenWhateverKindItWas() {
        StopEvent longOne = stopped(at(10));
        assertThat(longOne.needsALineCheck(at(10, 25))).isTrue();
        assertThat(longOne.needsALineCheck(at(10, 5))).isFalse();
    }

    @Test
    void someKindsNeedTheLineRiddenHoweverShortTheyWere() {
        for (StopKind kind : List.of(StopKind.DEROPEMENT, StopKind.ROPE_POSITION, StopKind.SAFETY_CIRCUIT)) {
            StopEvent brief = stop(kind, at(10), Optional.of(at(10, 2)), "written up", false, JOHN_PAUL);
            assertThat(brief.needsALineCheck()).isTrue();
        }
    }

    @Test
    void aStopThatNeedsACheckWillNotRestartWithoutOne() {
        StopEvent circuit = stop(StopKind.SAFETY_CIRCUIT);
        assertThat(circuit.mayRestart(at(10, 5))).isFalse();
        assertThat(circuit.withLineCheck().mayRestart(at(10, 5))).isTrue();
    }

    @Test
    void restartingWithoutTheCheckIsRefusedRatherThanRecorded() {
        StopEvent circuit = stop(StopKind.SAFETY_CIRCUIT);
        assertThatThrownBy(() -> circuit.restart(at(10, 10), "M Vetter"))
                .isInstanceOf(OperationsError.class)
                .hasMessage("a safety circuit runs again only after a line check");
        StopEvent ran = circuit.restart(at(10, 10), "M Vetter", true);
        assertThat(ran.isOpen()).isFalse();
        assertThat(ran.lineChecked()).isTrue();
    }

    @Test
    void aShortGateStopRestartsWithNoCeremony() {
        StopEvent gate = stop(StopKind.GATE);
        StopEvent ran = gate.restart(at(10, 2), "K Ostler");
        assertThat(ran.length()).isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    void aStopCannotBeClosedTwice() {
        StopEvent ran = between(at(10), at(10, 5));
        assertThat(ran.isOpen()).isFalse();
        assertThat(ran.mayRestart(at(10, 10))).isFalse();
        assertThatThrownBy(() -> ran.restart(at(10, 10), "K Ostler"))
                .isInstanceOf(OperationsError.class)
                .hasMessage("that stop has already been closed");
    }

    @Test
    void anOpenStopNeedsAMomentToBeMeasured() {
        StopEvent openStop = stop();
        assertThatThrownBy(openStop::length)
                .isInstanceOf(OperationsError.class)
                .hasMessage("an open stop needs a moment to be measured against");
        assertThatThrownBy(() -> openStop.length(at(9)))
                .isInstanceOf(OperationsError.class)
                .hasMessage("that moment is before the stop");
        assertThat(openStop.length(at(10, 30))).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void aStopPrintsItsState() {
        assertThat(stop()).hasToString("JP operator stop at 10:00, still stopped");
        assertThat(between(at(10), at(10, 5))).hasToString("JP operator stop at 10:00, running");
    }

    @Test
    void aLogBelongsToOneMachine() {
        StopEvent elsewhere = stop(StopKind.OPERATOR, at(10), Optional.empty(), "", false, STRAWBERRY);
        assertThatThrownBy(() -> StopLog.of(JOHN_PAUL, List.of(elsewhere)))
                .isInstanceOf(OperationsError.class)
                .hasMessage("SB does not belong in a JP log");
    }

    @Test
    void aLogSortsAndCounts() {
        StopLog log = StopLog.of(JOHN_PAUL, List.of(between(at(14), at(14, 5)), between(at(9), at(9, 10))));
        assertThat(log.size()).isEqualTo(2);
        assertThat(log.stops()).extracting(one -> one.stopped().getHour()).containsExactly(9, 14);
    }

    @Test
    void downtimeAddsUpTheClosedStops() {
        StopLog log = StopLog.of(
                JOHN_PAUL,
                List.of(between(at(9), at(9, 10)), between(StopKind.POWER, at(11), at(11, 30)), stopped(at(14))));
        assertThat(log.downtime()).isEqualTo(Duration.ofMinutes(40));
        assertThat(log.chargeableDowntime()).isEqualTo(Duration.ofMinutes(10));
        assertThat(log.openStops()).hasSize(1);
    }

    @Test
    void availabilityOnlyChargesTheMachineForItsOwnStops() {
        StopLog log =
                StopLog.of(JOHN_PAUL, List.of(between(at(9), at(9, 30)), between(StopKind.WEATHER, at(11), at(13))));
        Duration scheduled = Duration.ofHours(8);
        assertThat(Decimals.quantize(log.availability(scheduled), 3)).isEqualByComparingTo("0.938");
        assertThatThrownBy(() -> log.availability(Duration.ZERO))
                .isInstanceOf(OperationsError.class)
                .hasMessage("availability needs a scheduled run time");
    }

    @Test
    void theReportableStopsCanBePulledOut() {
        StopLog log = StopLog.of(
                JOHN_PAUL,
                List.of(
                        between(at(9), at(9, 5)),
                        stop(
                                StopKind.DEROPEMENT,
                                at(11),
                                Optional.of(at(13)),
                                "carrier 34 off at T4",
                                true,
                                JOHN_PAUL)));
        assertThat(log.reportable()).hasSize(1);
        assertThat(log.longest().kind()).isSameAs(StopKind.DEROPEMENT);
        assertThat(log.ofKind(StopKind.OPERATOR)).hasSize(1);
    }

    @Test
    void aLogWithNothingClosedHasNoLongest() {
        StopLog log = StopLog.of(JOHN_PAUL, List.of(stop()));
        assertThatThrownBy(log::longest)
                .isInstanceOf(OperationsError.class)
                .hasMessage("nothing in this log has been closed yet");
    }

    @Test
    void addingAStopMakesANewLog() {
        StopLog log = StopLog.of(JOHN_PAUL);
        StopLog longer = log.withStop(stop());
        assertThat(log.size()).isZero();
        assertThat(longer.size()).isEqualTo(1);
    }
}
