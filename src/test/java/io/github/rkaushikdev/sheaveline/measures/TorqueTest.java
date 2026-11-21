package io.github.rkaushikdev.sheaveline.measures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.Constructors;
import io.github.rkaushikdev.sheaveline.ParseError;
import io.github.rkaushikdev.sheaveline.RangeError;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TorqueTest {

    @Test
    void inchPoundsAreTheBaseUnit() {
        assertThat(Torque.ofPoundFeet(900).inchPounds()).isEqualTo(10800);
        assertThat(Torque.ofInchPounds(450).poundFeet()).isEqualByComparingTo("37.5");
    }

    @Test
    void newtonMetresReadBackCloseEnoughForAManual() {
        assertThat(Torque.ofNewtonMetres(100).inchPounds()).isEqualTo(885);
        assertThat(Decimals.quantize(Torque.ofInchPounds(885).newtonMetres(), 1))
                .isEqualByComparingTo("100");
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "450 in lb|450",
                "450 inch pounds|450",
                "900 lb ft|10800",
                "900 ft lb|10800",
                "900 pound feet|10800",
                "100 Nm|885",
                "100 newton metres|885"
            })
    void parseReadsTheFacesTheManualsUse(String text, long inchPounds) {
        assertThat(Torque.parse(text).inchPounds()).isEqualTo(inchPounds);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "tight", "450", "450 kg", "1.2.3 in lb"})
    void parseRefusesWhatIsNotATorque(String text) {
        assertThatThrownBy(() -> Torque.parse(text)).isInstanceOf(ParseError.class);
    }

    @Test
    void aTorqueIsNeverNegativeAndNeverAFloat() {
        assertThatThrownBy(() -> new Torque(-1)).isInstanceOf(RangeError.class);
        Constructors.takeOnly(Torque.class, long.class);
    }

    @Test
    void aReadingInsideABandOfTheSetFigure() {
        Torque target = Torque.ofInchPounds(450);
        assertThat(Torque.ofInchPounds(430).within(target, "0.10")).isTrue();
        assertThat(Torque.ofInchPounds(495).within(target, "0.10")).isTrue();
        assertThat(Torque.ofInchPounds(390).within(target, "0.10")).isFalse();
    }

    @Test
    void aTighterBandRejectsWhatALooserOneAccepts() {
        Torque target = Torque.ofInchPounds(450);
        Torque reading = Torque.ofInchPounds(415);
        assertThat(reading.within(target, "0.10")).isTrue();
        assertThat(reading.within(target, "0.05")).isFalse();
    }

    @Test
    void subtractionGivesTheGapEitherWayRound() {
        assertThat(Torque.ofInchPounds(450).minus(Torque.ofInchPounds(400))).isEqualTo(new Torque(50));
        assertThat(Torque.ofInchPounds(400).minus(Torque.ofInchPounds(450))).isEqualTo(new Torque(50));
    }

    @Test
    void orderingAndEquality() {
        Torque light = Torque.ofInchPounds(400);
        Torque heavy = Torque.ofInchPounds(450);
        assertThat(light).isLessThan(heavy);
        assertThat(heavy).isGreaterThan(light);
        assertThat(light).isLessThanOrEqualTo(new Torque(400));
        assertThat(light).isGreaterThanOrEqualTo(new Torque(400));
        assertThat(light).isNotEqualTo(heavy);
        assertThat((Object) light).isNotEqualTo(400L);
        assertThat(new HashSet<>(List.of(light, new Torque(400)))).hasSize(1);
    }

    @Test
    void aSlackBoltReadsZero() {
        assertThat(new Torque(0).isSlack()).isTrue();
        assertThat(new Torque(1).isSlack()).isFalse();
    }

    @Test
    void scalingATorque() {
        assertThat(Torque.ofInchPounds(450).scaled("0.5")).isEqualTo(new Torque(225));
    }

    @Test
    void formattingInBothFaces() {
        assertThat(Torque.ofPoundFeet(900).formatPoundFeet()).isEqualTo("900 lb ft");
        assertThat(Torque.ofInchPounds(450).formatInchPounds()).isEqualTo("450 in lb");
        assertThat(Torque.ofInchPounds(450)).hasToString("450 in lb");
        assertThat(Torque.ofInchPounds(450).repr()).isEqualTo("new Torque(450)");
    }
}
