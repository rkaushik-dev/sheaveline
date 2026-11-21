package io.github.rkaushikdev.sheaveline.weather;

import static io.github.rkaushikdev.sheaveline.Hill.JOHN_PAUL;
import static io.github.rkaushikdev.sheaveline.Hill.johnPaul;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Temperature;
import io.github.rkaushikdev.sheaveline.measures.Wind;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class HoldLogTest {

    private static final Station SUMMIT = new Station("summit", Length.ofFeet(7965));
    private static final String ACROSS = "SSE";
    private static final String GALE = ACROSS + " 30G60";

    private static LocalDateTime at(int hour, int minute) {
        return LocalDateTime.of(2025, 1, 15, hour, minute);
    }

    private static LocalDateTime at(int hour) {
        return at(hour, 0);
    }

    private static Observation observed(String wind, LocalDateTime taken, Optional<Length> lightning) {
        return new Observation(
                SUMMIT,
                taken,
                Wind.parse(wind),
                Temperature.ofDegreesF(14),
                Observation.UNLIMITED_VISIBILITY,
                Precipitation.NONE,
                false,
                lightning);
    }

    private static Observation observed() {
        return observed("W 12", at(12), Optional.empty());
    }

    private static Observation observed(String wind) {
        return observed(wind, at(12), Optional.empty());
    }

    private static Observation observedAt(LocalDateTime taken) {
        return observed("W 12", taken, Optional.empty());
    }

    private static Observation struckAt(LocalDateTime taken, long lightningFeet) {
        return observed("W 12", taken, Optional.of(Length.ofFeet(lightningFeet)));
    }

    private static WeatherHold decision() {
        return new WeatherHold(johnPaul(), observed());
    }

    private static WeatherHold decision(String wind) {
        return new WeatherHold(johnPaul(), observed(wind));
    }

    private static WeatherHold strike(long lightningFeet) {
        return new WeatherHold(johnPaul(), struckAt(at(12), lightningFeet));
    }

    private static HoldRecord record(HoldReason reason, LocalDateTime started, Optional<LocalDateTime> ended) {
        return new HoldRecord(JOHN_PAUL, reason, started, "K Ostler", "", ended, ended.map(when -> "K Ostler"));
    }

    private static HoldRecord record() {
        return record(HoldReason.GUST, at(10), Optional.empty());
    }

    private static HoldRecord endedAt(LocalDateTime ended) {
        return record(HoldReason.GUST, at(10), Optional.of(ended));
    }

    private static HoldRecord openFor(HoldRegister register, HoldReason reason) {
        return register.openHolds(JOHN_PAUL).stream()
                .filter(one -> one.reason() == reason)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void aRecordIsSignedAndOrdered() {
        assertThatThrownBy(() -> new HoldRecord(JOHN_PAUL, HoldReason.GUST, at(10), "  "))
                .isInstanceOf(WeatherError.class)
                .hasMessage("a hold is signed by whoever called it");
        assertThatThrownBy(() -> record(HoldReason.GUST, at(12), Optional.of(at(10))))
                .isInstanceOf(WeatherError.class)
                .hasMessage("a hold cannot end before it started");
    }

    @Test
    void aRecordIsEitherClosedBySomebodyOrStillOpen() {
        assertThatThrownBy(() -> new HoldRecord(
                        JOHN_PAUL, HoldReason.GUST, at(10), "K Ostler", "", Optional.of(at(11)), Optional.empty()))
                .isInstanceOf(WeatherError.class)
                .hasMessage("a hold is closed by somebody at some time, or neither");
        assertThatThrownBy(() -> new HoldRecord(
                        JOHN_PAUL, HoldReason.GUST, at(10), "K Ostler", "", Optional.empty(), Optional.of("K Ostler")))
                .isInstanceOf(WeatherError.class)
                .hasMessage("a hold is closed by somebody at some time, or neither");
    }

    @Test
    void anOpenHoldIsMeasuredAgainstAMoment() {
        HoldRecord openHold = record();
        assertThat(openHold.isOpen()).isTrue();
        assertThat(openHold.length(at(11))).isEqualTo(Duration.ofHours(1));
        assertThatThrownBy(openHold::length)
                .isInstanceOf(WeatherError.class)
                .hasMessage("an open hold needs a moment to be measured against");
        assertThatThrownBy(() -> openHold.length(at(9)))
                .isInstanceOf(WeatherError.class)
                .hasMessage("that moment is before the hold started");
    }

    @Test
    void aClosedHoldKnowsItsOwnLength() {
        HoldRecord closed = endedAt(at(11, 30));
        assertThat(closed.isOpen()).isFalse();
        assertThat(closed.length()).isEqualTo(Duration.ofMinutes(90));
    }

    @Test
    void closingAHoldMakesANewRecord() {
        HoldRecord openHold = record();
        HoldRecord closed = openHold.closed(at(11), "D Ruiz");
        assertThat(openHold.isOpen()).isTrue();
        assertThat(closed.endedBy()).contains("D Ruiz");
        assertThatThrownBy(() -> closed.closed(at(12), "D Ruiz"))
                .isInstanceOf(WeatherError.class)
                .hasMessage("the gust hold on JP is already closed");
    }

    @Test
    void aRecordPrintsItsState() {
        assertThat(record()).hasToString("JP gust from 10:00, open");
        assertThat(endedAt(at(11, 30))).hasToString("JP gust from 10:00, closed 11:30");
    }

    @Test
    void callingAHoldOpensOneRecordAReason() {
        HoldRegister register = HoldRegister.of().call(decision(GALE), at(11), "K Ostler");
        assertThat(register.size()).isGreaterThanOrEqualTo(2);
        assertThat(register.openReasons(JOHN_PAUL)).contains(HoldReason.GUST);
        assertThat(register.openReasons(JOHN_PAUL)).contains(HoldReason.CROSSWIND);
        assertThat(register.isHeld(JOHN_PAUL)).isTrue();
    }

    @Test
    void callingTheSameHoldTwiceDoesNotDuplicateIt() {
        WeatherHold windy = decision(GALE);
        HoldRegister once = HoldRegister.of().call(windy, at(11), "K Ostler");
        HoldRegister twice = once.call(windy, at(11, 15), "K Ostler");
        assertThat(twice.size()).isEqualTo(once.size());
    }

    @Test
    void aClearReadingOpensNothing() {
        HoldRegister register = HoldRegister.of().call(decision(), at(11), "K Ostler");
        assertThat(register.size()).isZero();
        assertThat(register.isHeld(JOHN_PAUL)).isFalse();
    }

    @Test
    void aWindHoldClearsAsSoonAsTheReadingIsInsideTheLimit() {
        HoldRegister register = HoldRegister.of().call(decision(GALE), at(11), "K Ostler");
        HoldRecord gust = openFor(register, HoldReason.GUST);
        WeatherHold calm = decision("W 10");
        assertThat(register.mayClear(gust, at(11, 20), observed("W 10"), calm)).isTrue();
    }

    @Test
    void aWindHoldDoesNotClearWhileTheWindIsStillOver() {
        HoldRegister register = HoldRegister.of().call(decision(GALE), at(11), "K Ostler");
        HoldRecord gust = openFor(register, HoldReason.GUST);
        WeatherHold stillWindy = decision(GALE);
        assertThat(register.mayClear(gust, at(11, 20), observed(GALE), stillWindy))
                .isFalse();
    }

    @Test
    void aLightningHoldWaitsThirtyMinutesWhateverTheWindIsDoing() {
        HoldRegister register = HoldRegister.of().call(strike(10000), at(11), "K Ostler");
        HoldRecord bolt = openFor(register, HoldReason.LIGHTNING);
        Observation quiet = observedAt(at(11, 5));
        WeatherHold calm = decision();
        assertThat(register.mayClear(bolt, at(11, 20), quiet, calm)).isFalse();
        assertThat(register.mayClear(bolt, at(11, 40), quiet, calm)).isTrue();
    }

    @Test
    void aFreshStrikeRestartsTheLightningClock() {
        HoldRegister register = HoldRegister.of().call(strike(10000), at(11), "K Ostler");
        HoldRecord bolt = openFor(register, HoldReason.LIGHTNING);
        Observation another = struckAt(at(11, 35), 8000);
        assertThat(register.mayClear(bolt, at(12, 30), another, decision())).isFalse();
    }

    @Test
    void aClosedHoldCannotBeClearedAgain() {
        HoldRegister register = HoldRegister.of(List.of(endedAt(at(11))));
        HoldRecord closed = register.records().get(0);
        assertThat(register.mayClear(closed, at(12), observed(), decision())).isFalse();
    }

    @Test
    void clearingAHoldClosesItInTheRegister() {
        HoldRegister register = HoldRegister.of().call(decision(GALE), at(11), "K Ostler");
        HoldRecord gust = openFor(register, HoldReason.GUST);
        HoldRegister after = register.clear(gust, at(11, 40), "D Ruiz");
        assertThat(after.openReasons(JOHN_PAUL)).doesNotContain(HoldReason.GUST);
        assertThat(after.size()).isEqualTo(register.size());
    }

    @Test
    void clearingAHoldFromAnotherRegisterIsRefused() {
        assertThatThrownBy(() -> HoldRegister.of().clear(record(), at(12), "D Ruiz"))
                .isInstanceOf(WeatherError.class)
                .hasMessage("that hold is not in this register");
    }

    @Test
    void theRegisterTotalsAndSorts() {
        HoldRegister register = HoldRegister.of(List.of(
                record(HoldReason.GUST, at(9), Optional.of(at(9, 30))),
                record(HoldReason.LIGHTNING, at(10), Optional.of(at(11))),
                record(HoldReason.CHILL, at(12), Optional.empty())));
        assertThat(register.byReason(HoldReason.LIGHTNING)).hasSize(1);
        assertThat(register.longest().reason()).isEqualTo(HoldReason.LIGHTNING);
        assertThat(register.totalHeld(JOHN_PAUL)).isEqualTo(Duration.ofMinutes(90));
    }

    @Test
    void aRegisterWithNothingClosedHasNoLongest() {
        HoldRegister register = HoldRegister.of(List.of(record()));
        assertThatThrownBy(register::longest)
                .isInstanceOf(WeatherError.class)
                .hasMessage("nothing in this register has been closed yet");
    }
}
