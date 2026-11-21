package io.github.rkaushikdev.sheaveline.plant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.measures.LoadPerFoot;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.time.LocalDate;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class RopeTest {

    private static final RopeSerial SERIAL = RopeSerial.parse("FAT-2019-04412");

    private static RopeSpec spec(RopeGrade grade, Length diameter, Load strength, LoadPerFoot weight) {
        return new RopeSpec(
                RopeConstruction.SIX_BY_TWENTY_FIVE, grade, LayDirection.RIGHT_REGULAR, diameter, strength, weight);
    }

    private static RopeSpec spec() {
        return spec(
                RopeGrade.EIP, Length.ofInches("1.378"), Load.ofPounds(196000), LoadPerFoot.ofPoundsPerFoot("3.12"));
    }

    private static HaulRope rope(LocalDate installed, Length length, int splices) {
        return new HaulRope(SERIAL, spec(), installed, length, splices);
    }

    private static HaulRope rope() {
        return rope(LocalDate.of(2019, 10, 4), Length.ofFeet(9800), 1);
    }

    @Test
    void constructionDecidesHowManyWiresCanBeSeen() {
        assertThat(RopeConstruction.SIX_BY_TWENTY_FIVE.strands()).isEqualTo(6);
        assertThat(RopeConstruction.SIX_BY_TWENTY_FIVE.outerWiresPerStrand()).isEqualTo(12);
        assertThat(RopeConstruction.SIX_BY_TWENTY_FIVE.outerWires()).isEqualTo(72);
        assertThat(RopeConstruction.EIGHT_BY_NINETEEN.outerWires()).isEqualTo(72);
        assertThat(RopeConstruction.SIX_BY_THIRTY_SIX.outerWires()).isEqualTo(84);
    }

    @Test
    void everyConstructionNamesItsWires() {
        for (RopeConstruction construction : RopeConstruction.values()) {
            assertThat(construction.outerWires()).isPositive();
        }
    }

    @Test
    void langLayIsToldFromRegular() {
        assertThat(LayDirection.RIGHT_LANG.isLang()).isTrue();
        assertThat(LayDirection.LEFT_LANG.isLang()).isTrue();
        assertThat(LayDirection.RIGHT_REGULAR.isLang()).isFalse();
    }

    @Test
    void aSpecChecksItsOwnNumbers() {
        assertThatThrownBy(() -> spec(
                        RopeGrade.EIP, Length.ofInches(0), Load.ofPounds(196000), LoadPerFoot.ofPoundsPerFoot("3.12")))
                .isInstanceOf(PlantError.class)
                .hasMessage("a rope has a diameter");
        assertThatThrownBy(() -> spec(
                        RopeGrade.EIP, Length.ofInches("1.378"), Load.ofPounds(0), LoadPerFoot.ofPoundsPerFoot("3.12")))
                .isInstanceOf(PlantError.class);
        assertThatThrownBy(() -> spec(
                        RopeGrade.EIP, Length.ofInches("1.378"), Load.ofPounds(196000), LoadPerFoot.ofPoundsPerFoot(0)))
                .isInstanceOf(PlantError.class);
    }

    @Test
    void theLayLengthIsSixAndAHalfDiameters() {
        assertThat(spec().layLength()).isEqualTo(Length.ofInches("8.957"));
    }

    @Test
    void theCondemningDiameterIsSevenPercentDown() {
        assertThat(spec().condemningDiameter()).isEqualTo(Length.ofInches("1.282"));
        assertThat(spec().condemningDiameter()).isLessThan(spec().nominalDiameter());
    }

    @Test
    void aRunOfRopeWeighsWhatTheSpecSays() {
        assertThat(spec().weightOf(Length.ofFeet(1000))).isEqualTo(Load.ofPounds(3120));
    }

    @Test
    void theDesignFactorIsBreakingStrengthOverTension() {
        assertThat(spec().designFactorAt(Load.ofPounds(49000))).isEqualByComparingTo("4");
        assertThat(spec().designFactorAt(Load.ofPounds(28000))).isEqualByComparingTo("7");
    }

    @Test
    void fourToOneIsTheLineBetweenSafeAndNot() {
        assertThat(spec().safeAt(Load.ofPounds(49000))).isTrue();
        assertThat(spec().safeAt(Load.ofPounds(40000))).isTrue();
        assertThat(spec().safeAt(Load.ofPounds(50000))).isFalse();
    }

    @Test
    void theHighestTensionTheSpecAllows() {
        assertThat(spec().maximumTension()).isEqualTo(Load.ofPounds(49000));
    }

    @Test
    void aDesignFactorNeedsARealTension() {
        assertThatThrownBy(() -> spec().designFactorAt(Load.ofPounds(0))).isInstanceOf(PlantError.class);
    }

    @Test
    void aSpecPrintsTheWayAReelTagReads() {
        assertThat(spec()).hasToString("1.378\" 6x25 filler wire");
    }

    @Test
    void aHaulRopeReadsItsDiameterThroughItsSpec() {
        assertThat(rope().diameter()).isEqualTo(Length.ofInches("1.378"));
        assertThat(rope().weight()).isEqualTo(Load.ofPounds(30576));
    }

    @Test
    void aRopeReportsItsAgeInDaysAndInSeasons() {
        assertThat(rope().ageInDays(LocalDate.of(2020, 10, 3))).isEqualTo(365);
        assertThat(rope().seasonsInService(LocalDate.of(2025, 10, 4))).isEqualTo(6);
        assertThat(rope().seasonsInService(LocalDate.of(2020, 1, 1))).isZero();
    }

    @Test
    void aDateBeforeTheRopeWentOnIsRefused() {
        assertThatThrownBy(() -> rope().ageInDays(LocalDate.of(2019, 1, 1)))
                .isInstanceOf(PlantError.class)
                .hasMessage("2019-01-01 is before rope FAT-2019-04412 went on");
    }

    @Test
    void aRopeCannotGoOnBeforeItWasDrawn() {
        assertThatThrownBy(() -> rope(LocalDate.of(2018, 5, 1), Length.ofFeet(9800), 1))
                .isInstanceOf(PlantError.class);
    }

    @Test
    void aRopeChecksItsOwnLengthAndSplices() {
        assertThatThrownBy(() -> rope(LocalDate.of(2019, 10, 4), Length.ofFeet(0), 1))
                .isInstanceOf(PlantError.class);
        assertThatThrownBy(() -> rope(LocalDate.of(2019, 10, 4), Length.ofFeet(9800), -1))
                .isInstanceOf(PlantError.class);
    }

    @Test
    void aLoopHasASpliceAndATramRopeDoesNot() {
        assertThat(rope().isEndless()).isTrue();
        assertThat(rope(LocalDate.of(2019, 10, 4), Length.ofFeet(9800), 0).isEndless())
                .isFalse();
    }

    @Test
    void aNewSpliceMakesANewRecord() {
        HaulRope spliced = rope().withSplice();
        assertThat(spliced.splices()).isEqualTo(2);
        assertThat(rope().splices()).isEqualTo(1);
        assertThat(spliced.serial()).isEqualTo(rope().serial());
    }

    @Test
    void aRopePrintsForTheRopeFile() {
        assertThat(rope()).hasToString("FAT-2019-04412 1.378\" 6x25 filler wire, 9800 ft");
    }

    @Test
    void theGradesAreOrderedTheWayTheMillNamesThem() {
        assertThat(RopeGrade.EEIP.value()).startsWith("extra extra");
        assertThat(Arrays.stream(RopeGrade.values()).map(Enum::name)).containsExactlyInAnyOrder("IPS", "EIP", "EEIP");
    }

    @Test
    void aHeavierGradeDoesNotChangeTheGeometry() {
        RopeSpec heavy = spec(
                RopeGrade.EEIP, Length.ofInches("1.378"), Load.ofPounds(216000), LoadPerFoot.ofPoundsPerFoot("3.12"));
        assertThat(heavy.layLength()).isEqualTo(spec().layLength());
        assertThat(heavy.maximumTension()).isGreaterThan(spec().maximumTension());
        assertThat(Decimals.quantize(heavy.designFactorAt(Load.ofPounds(49000)), 2))
                .isEqualByComparingTo("4.41");
    }
}
