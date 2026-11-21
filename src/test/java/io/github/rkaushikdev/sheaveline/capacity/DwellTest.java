package io.github.rkaushikdev.sheaveline.capacity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Speed;
import io.github.rkaushikdev.sheaveline.plant.LiftClass;
import org.junit.jupiter.api.Test;

class DwellTest {

    private static final Speed LINE = Speed.ofFeetPerMinute(1000);
    private static final Speed WALK = Speed.ofFeetPerMinute(120);

    private static TerminalTransit detachable(long slowFeet, long decelFeet, long accelFeet) {
        return new TerminalTransit(
                LINE, WALK, Length.ofFeet(decelFeet), Length.ofFeet(slowFeet), Length.ofFeet(accelFeet));
    }

    private static TerminalTransit detachable(long slowFeet) {
        return detachable(slowFeet, 40, 40);
    }

    private static TerminalTransit detachable() {
        return detachable(60);
    }

    private static TerminalTransit fixed(long fpm, long rampFeet) {
        return TerminalTransit.fixedGrip(Speed.ofFeetPerMinute(fpm), Length.ofFeet(rampFeet));
    }

    private static TerminalTransit fixed() {
        return fixed(450, 45);
    }

    private static LineSpacing spacing(long loopFeet, int carriers, long fpm) {
        return new LineSpacing(Length.ofFeet(loopFeet), carriers, Speed.ofFeetPerMinute(fpm));
    }

    private static LineSpacing spacing() {
        return spacing(10000, 100, 1000);
    }

    @Test
    void aDetachableTerminalSlowsTheCarrierDown() {
        assertThat(detachable().detaches()).isTrue();
        assertThat(fixed().detaches()).isFalse();
    }

    @Test
    void aTransitChecksItsOwnSpeeds() {
        assertThatThrownBy(() -> new TerminalTransit(new Speed(0), WALK, Length.ZERO, Length.ofFeet(60), Length.ZERO))
                .isInstanceOf(CapacityError.class);
        assertThatThrownBy(() -> new TerminalTransit(LINE, new Speed(0), Length.ZERO, Length.ofFeet(60), Length.ZERO))
                .isInstanceOf(CapacityError.class);
        assertThatThrownBy(() -> new TerminalTransit(WALK, LINE, Length.ZERO, Length.ofFeet(60), Length.ZERO))
                .isInstanceOf(CapacityError.class);
    }

    @Test
    void theSlowSectionSetsTheLoadingWindow() {
        assertThat(detachable(60).loadingWindow()).isEqualTo(Duration.ofSeconds(30));
        assertThat(detachable(120).loadingWindow()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void aFixedGripWindowIsTheRampAtLineSpeed() {
        assertThat(fixed(450, 45).loadingWindow()).isEqualTo(Duration.ofSeconds(6));
    }

    @Test
    void theTransitCoversTheRampsAsWellAsTheSlowSection() {
        TerminalTransit through = detachable();
        assertThat(through.transitTime()).isGreaterThan(through.loadingWindow());
        assertThat(through.length()).isEqualTo(Length.ofFeet(140));
    }

    @Test
    void slowingTheLineBarelyTouchesADetachableWindow() {
        TerminalTransit fast = detachable();
        TerminalTransit slow = new TerminalTransit(
                Speed.ofFeetPerMinute(600), WALK, Length.ofFeet(40), Length.ofFeet(60), Length.ofFeet(40));
        assertThat(slow.loadingWindow()).isEqualTo(fast.loadingWindow());
        assertThat(slow.transitTime()).isGreaterThan(fast.transitTime());
    }

    @Test
    void aCrewNeedsTimeThatGrowsWithTheSeats() {
        assertThat(new LoadingGate(detachable(), 4).timeNeeded()).isEqualTo(Duration.ofSeconds(7));
        assertThat(new LoadingGate(detachable(), 6).timeNeeded()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void aSecondAttendantHalvesTheSeatingTime() {
        LoadingGate one = new LoadingGate(detachable(), 6, 1);
        LoadingGate two = one.withAttendants(2);
        assertThat(two.timeNeeded()).isLessThan(one.timeNeeded());
    }

    @Test
    void aGateChecksItsOwnStaffing() {
        assertThatThrownBy(() -> new LoadingGate(detachable(), 0)).isInstanceOf(CapacityError.class);
        assertThatThrownBy(() -> new LoadingGate(detachable(), 4, 0)).isInstanceOf(CapacityError.class);
    }

    @Test
    void aDetachableGateKeepsUpEasily() {
        LoadingGate gate = new LoadingGate(detachable(), 6);
        assertThat(gate.keepsUp()).isTrue();
        assertThat(gate.slack()).isGreaterThan(Duration.ofSeconds(15));
    }

    @Test
    void aFixedGripGateIsTheOneThatStruggles() {
        LoadingGate gate = new LoadingGate(fixed(450, 30), 4);
        assertThat(gate.keepsUp()).isFalse();
        assertThat(gate.slack().isZero()).isTrue();
    }

    @Test
    void aSecondAttendantCanRescueATightGate() {
        LoadingGate gate = new LoadingGate(fixed(450, 45), 6);
        assertThat(gate.keepsUp()).isFalse();
        assertThat(gate.withAttendants(2).keepsUp()).isTrue();
        assertThat(gate.attendantsNeeded()).isEqualTo(2);
    }

    @Test
    void aGateNobodyCanLoadIsRefusedRatherThanStaffed() {
        LoadingGate gate = new LoadingGate(fixed(1000, 10), 8);
        assertThatThrownBy(gate::attendantsNeeded).isInstanceOf(CapacityError.class);
    }

    @Test
    void bothConstraintsCanHoldAtOnce() {
        TerminalCheck check =
                new TerminalCheck(spacing(), new LoadingGate(detachable(), 4), LiftClass.DETACHABLE_CHAIR);
        assertThat(check.holds()).isTrue();
        assertThat(check.bindingConstraint()).isEqualTo("neither");
    }

    @Test
    void theIntervalCanBindWhileTheGateIsFine() {
        LineSpacing tight = spacing(10000, 100, 1000);
        TerminalCheck check = new TerminalCheck(tight, new LoadingGate(detachable(), 4), LiftClass.FIXED_CHAIR);
        assertThat(check.intervalHolds()).isFalse();
        assertThat(check.gateHolds()).isTrue();
        assertThat(check.bindingConstraint()).isEqualTo("interval");
    }

    @Test
    void theGateCanBindWhileTheIntervalIsFine() {
        LineSpacing easy = spacing(10000, 40, 1000);
        TerminalCheck check = new TerminalCheck(easy, new LoadingGate(fixed(450, 20), 6), LiftClass.FIXED_CHAIR);
        assertThat(check.intervalHolds()).isTrue();
        assertThat(check.gateHolds()).isFalse();
        assertThat(check.bindingConstraint()).isEqualTo("gate");
        assertThat(check.mostCarriers()).isZero();
    }

    @Test
    void bothCanBindTogether() {
        LineSpacing tight = spacing(10000, 100, 1000);
        TerminalCheck check = new TerminalCheck(tight, new LoadingGate(fixed(450, 20), 6), LiftClass.FIXED_CHAIR);
        assertThat(check.bindingConstraint()).isEqualTo("both");
    }

    @Test
    void theCheckReportsTheClassFloorItIsUsing() {
        TerminalCheck check =
                new TerminalCheck(spacing(), new LoadingGate(detachable(), 4), LiftClass.DETACHABLE_CHAIR);
        assertThat(check.floor()).isEqualTo(Duration.ofSeconds(6));
        assertThat(check.mostCarriers()).isEqualTo(100);
    }

    @Test
    void theCheckPrintsBothNumbersAndWhatBinds() {
        TerminalCheck check =
                new TerminalCheck(spacing(), new LoadingGate(detachable(), 4), LiftClass.DETACHABLE_CHAIR);
        assertThat(check).hasToString("interval 0:06, window 0:30, neither binding");
    }
}
