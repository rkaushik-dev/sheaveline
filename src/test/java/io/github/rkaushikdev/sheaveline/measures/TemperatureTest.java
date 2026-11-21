package io.github.rkaushikdev.sheaveline.measures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.Constructors;
import io.github.rkaushikdev.sheaveline.ParseError;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TemperatureTest {

    @Test
    void tenthsOfADegreeAreTheBaseUnit() {
        assertThat(Temperature.ofDegreesF(-12).decidegreesF()).isEqualTo(-120);
        assertThat(Temperature.ofDegreesF("18.4").decidegreesF()).isEqualTo(184);
        assertThat(new Temperature(184).degreesF()).isEqualByComparingTo("18.4");
    }

    @Test
    void celsiusIsTheFaceTheDriveManualUses() {
        assertThat(Temperature.ofDegreesC(0)).isEqualTo(Temperature.FREEZING);
        assertThat(Temperature.ofDegreesC(-40)).isEqualTo(Temperature.ofDegreesF(-40));
        assertThat(Temperature.ofDegreesF(212).degreesC()).isEqualByComparingTo("100");
    }

    @Test
    void celsiusRoundTripsWithinATenth() {
        for (long reading : new long[] {-24, -12, 0, 18, 45}) {
            Temperature temperature = Temperature.ofDegreesF(reading);
            Temperature again = Temperature.ofDegreesC(temperature.degreesC());
            assertThat(Math.abs(again.decidegreesF() - temperature.decidegreesF()))
                    .isLessThanOrEqualTo(1);
        }
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "-12|-120",
                "-12 F|-120",
                "18F|180",
                "18 fahrenheit|180",
                "0 C|320",
                "-24 degC|-112",
                "'  5.5  '|55"
            })
    void parseReadsWhatTheStationSends(String text, long decidegrees) {
        assertThat(Temperature.parse(text).decidegreesF()).isEqualTo(decidegrees);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "cold", "12 K", "1.2.3"})
    void parseRefusesWhatIsNotATemperature(String text) {
        assertThatThrownBy(() -> Temperature.parse(text)).isInstanceOf(ParseError.class);
    }

    @Test
    void twoReadingsSubtractToADifference() {
        Temperature warm = Temperature.ofDegreesF(20);
        Temperature cold = Temperature.ofDegreesF(-4);
        assertThat(warm.minus(cold)).isEqualTo(TemperatureDelta.ofDegreesF(24));
        assertThat(cold.minus(warm)).isEqualTo(TemperatureDelta.ofDegreesF(-24));
    }

    /** Adding two readings is refused before the program runs: no method takes two readings. */
    @Test
    void twoReadingsDoNotAdd() {
        assertThat(Arrays.stream(Temperature.class.getMethods())
                        .filter(method -> method.getReturnType() == Temperature.class)
                        .filter(method -> method.getParameterCount() == 1)
                        .map(method -> method.getParameterTypes()[0]))
                .isNotEmpty()
                .doesNotContain(Temperature.class);
    }

    @Test
    void aReadingPlusADifferenceIsAReading() {
        Temperature start = Temperature.ofDegreesF(10);
        TemperatureDelta drop = TemperatureDelta.ofDegreesF(15);
        assertThat(start.minus(drop)).isEqualTo(Temperature.ofDegreesF(-5));
        assertThat(start.plus(drop)).isEqualTo(Temperature.ofDegreesF(25));
    }

    @Test
    void aDifferenceInCelsiusCarriesNoOffset() {
        assertThat(TemperatureDelta.ofDegreesF(9).degreesC()).isEqualByComparingTo("5");
        assertThat(TemperatureDelta.ofDegreesC(5)).isEqualTo(TemperatureDelta.ofDegreesF(9));
    }

    @Test
    void differencesAddAndNegate() {
        TemperatureDelta a = TemperatureDelta.ofDegreesF(5);
        TemperatureDelta b = TemperatureDelta.ofDegreesF(7);
        assertThat(a.plus(b)).isEqualTo(TemperatureDelta.ofDegreesF(12));
        assertThat(a.negated()).isEqualTo(TemperatureDelta.ofDegreesF(-5));
        assertThat(a.negated().abs()).isEqualTo(a);
        assertThat(a).isLessThan(b);
        assertThat(b).isGreaterThan(a);
        assertThat(a).isLessThanOrEqualTo(TemperatureDelta.ofDegreesF(5));
        assertThat(a).isGreaterThanOrEqualTo(TemperatureDelta.ofDegreesF(5));
        assertThat(a).isNotEqualTo(b);
        assertThat(new HashSet<>(List.of(a, new TemperatureDelta(50)))).hasSize(1);
    }

    @Test
    void freezingIsThirtyTwoAndAnswersForItself() {
        assertThat(Temperature.FREEZING.degreesF()).isEqualByComparingTo("32");
        assertThat(Temperature.FREEZING.isFreezing()).isTrue();
        assertThat(Temperature.ofDegreesF(31).isFreezing()).isTrue();
        assertThat(Temperature.ofDegreesF(33).isFreezing()).isFalse();
    }

    @Test
    void orderingAndEquality() {
        Temperature cold = Temperature.ofDegreesF(-15);
        Temperature mild = Temperature.ofDegreesF(28);
        assertThat(cold).isLessThan(mild);
        assertThat(mild).isGreaterThan(cold);
        assertThat(cold).isLessThanOrEqualTo(Temperature.ofDegreesF(-15));
        assertThat(cold).isGreaterThanOrEqualTo(Temperature.ofDegreesF(-15));
        assertThat(cold).isNotEqualTo(mild);
        assertThat((Object) cold).isNotEqualTo(-15L);
        assertThat(new HashSet<>(List.of(cold, new Temperature(-150)))).hasSize(1);
    }

    @Test
    void theConstructorsRefuseAFloat() {
        Constructors.takeOnly(Temperature.class, long.class);
        Constructors.takeOnly(TemperatureDelta.class, long.class);
    }

    @Test
    void formatting() {
        assertThat(Temperature.ofDegreesF("-12.4").formatF()).isEqualTo("-12.4 F");
        assertThat(Temperature.FREEZING.formatC()).isEqualTo("0.0 C");
        assertThat(Temperature.ofDegreesF(18)).hasToString("18.0 F");
        assertThat(Temperature.ofDegreesF(18).repr()).isEqualTo("new Temperature(180)");
        assertThat(TemperatureDelta.ofDegreesF("2.5")).hasToString("2.5 F");
        assertThat(TemperatureDelta.ofDegreesF(3).repr()).isEqualTo("new TemperatureDelta(30)");
    }
}
