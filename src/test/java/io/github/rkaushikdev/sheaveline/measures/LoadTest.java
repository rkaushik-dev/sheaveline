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

class LoadTest {

    @Test
    void poundsAreTheBaseUnit() {
        assertThat(Load.ofPounds(34500).pounds()).isEqualTo(34500);
        assertThat(Load.ofKips("34.5").pounds()).isEqualTo(34500);
        assertThat(Load.ofTons(2).pounds()).isEqualTo(4000);
    }

    @Test
    void kilogramsReadBackCloseEnoughForAManual() {
        Load load = Load.ofKilograms(1000);
        assertThat(load.pounds()).isEqualTo(2205);
        assertThat(Decimals.quantize(load.kilograms(), 1)).isEqualByComparingTo("1000.2");
    }

    @Test
    void kipsAreTheFaceATensionChartUses() {
        assertThat(Load.ofPounds(34500).kips()).isEqualByComparingTo("34.5");
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "34500 lb|34500",
                "34,500 lb|34500",
                "34500lbs|34500",
                "170 pounds|170",
                "34.5 kip|34500",
                "1000 kg|2205",
                "2 tons|4000",
                "8600 lbf|8600"
            })
    void parseReadsTheShapesASpecSheetUses(String text, long pounds) {
        assertThat(Load.parse(text).pounds()).isEqualTo(pounds);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "heavy", "34500", "5 stone", "1.2.3 lb"})
    void parseRefusesWhatIsNotALoad(String text) {
        assertThatThrownBy(() -> Load.parse(text)).isInstanceOf(ParseError.class);
    }

    @Test
    void aDesignLoadIsOneHundredAndSeventyPounds() {
        assertThat(Load.ofPounds(170)).isEqualTo(Load.PASSENGER_DESIGN_LOAD);
        assertThat(Load.PASSENGER_DESIGN_LOAD.scaled(4)).isEqualTo(Load.ofPounds(680));
    }

    @Test
    void arithmeticAndOrdering() {
        Load a = Load.ofPounds(12000);
        Load b = Load.ofPounds(3400);
        assertThat(a.plus(b)).isEqualTo(Load.ofPounds(15400));
        assertThat(a.minus(b)).isEqualTo(Load.ofPounds(8600));
        assertThat(b.negated()).isEqualTo(Load.ofPounds(-3400));
        assertThat(b).isLessThan(a);
        assertThat(a).isGreaterThan(b);
        assertThat(a).isGreaterThanOrEqualTo(Load.ofPounds(12000));
        assertThat(b).isLessThanOrEqualTo(Load.ofPounds(3400));
        assertThat(a).isNotEqualTo(b);
        assertThat((Object) a).isNotEqualTo(12000L);
        assertThat(new HashSet<>(List.of(a, Load.ofPounds(12000)))).hasSize(1);
    }

    @Test
    void ratioIsDimensionless() {
        assertThat(Load.ofPounds(30000).ratio(Load.ofPounds(10000))).isEqualByComparingTo("3");
        assertThatThrownBy(() -> Load.ofPounds(1).ratio(Load.ZERO)).isInstanceOf(RangeError.class);
    }

    @Test
    void theConstructorRefusesAFloat() {
        Constructors.takeOnly(Load.class, long.class);
        Constructors.takeOnly(LoadPerFoot.class, long.class);
    }

    @Test
    void spreadingAWeightOverALengthGivesARopeQuote() {
        LoadPerFoot rope = Load.ofPounds(9000).over(Length.ofFeet(3000));
        assertThat(rope.poundsPerFoot()).isEqualByComparingTo("3");
        assertThat(rope.millipoundsPerFoot()).isEqualTo(3000);
    }

    @Test
    void spreadingOverNothingIsRefused() {
        assertThatThrownBy(() -> Load.ofPounds(1).over(Length.ofFeet(0))).isInstanceOf(RangeError.class);
    }

    @Test
    void aRunOfRopeWeighsWhatTheQuoteSays() {
        LoadPerFoot rope = LoadPerFoot.ofPoundsPerFoot("3.12");
        assertThat(rope.times(Length.ofFeet(1000))).isEqualTo(Load.ofPounds(3120));
        assertThat(rope.times(Length.ofFeetInches(0, 6))).isEqualTo(Load.ofPounds(2));
    }

    @Test
    void weightPerFootIsNeverNegative() {
        assertThatThrownBy(() -> new LoadPerFoot(-1)).isInstanceOf(RangeError.class);
    }

    @Test
    void weightPerFootComparesAndPrints() {
        LoadPerFoot light = LoadPerFoot.ofPoundsPerFoot("1.85");
        LoadPerFoot heavy = LoadPerFoot.ofPoundsPerFoot("3.12");
        assertThat(light).isLessThan(heavy);
        assertThat(heavy).isGreaterThan(light);
        assertThat(light).isNotEqualTo(heavy);
        assertThat(heavy).hasToString("3.12 lb/ft");
        assertThat(heavy.repr()).isEqualTo("new LoadPerFoot(3120)");
        assertThat(new HashSet<>(List.of(light, new LoadPerFoot(1850)))).hasSize(1);
    }

    @Test
    void formattingGroupsTheThousands() {
        assertThat(Load.ofPounds(34500).formatPounds()).isEqualTo("34,500 lb");
        assertThat(Load.ofPounds(170)).hasToString("170 lb");
        assertThat(Load.ZERO.repr()).isEqualTo("Load.ofPounds(0)");
    }
}
