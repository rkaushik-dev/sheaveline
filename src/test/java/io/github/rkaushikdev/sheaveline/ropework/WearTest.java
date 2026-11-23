package io.github.rkaushikdev.sheaveline.ropework;

import static io.github.rkaushikdev.sheaveline.Ropes.ROPE;
import static io.github.rkaushikdev.sheaveline.Ropes.reading;
import static io.github.rkaushikdev.sheaveline.Ropes.walk;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class WearTest {

    private static final Length NOMINAL = ROPE.diameter();

    private static WearSurvey survey(DiameterReading... readings) {
        return readings.length == 0 ? walk(reading(0, "1.378")) : walk(readings);
    }

    @Test
    void aReadingAveragesTheTwoAxes() {
        assertThat(reading(0, "1.370", "1.360").mean()).isEqualTo(Length.ofInches("1.365"));
        assertThat(reading(0, "1.370", "1.360").ovality()).isEqualTo(Length.ofInches("0.010"));
    }

    @Test
    void aRoundRopeHasNoOvality() {
        assertThat(reading(0, "1.378").ovality().isZero()).isTrue();
    }

    @Test
    void aReadingChecksItsOwnNumbers() {
        assertThatThrownBy(() -> new DiameterReading(Length.ofFeet(-1), NOMINAL, NOMINAL))
                .isInstanceOf(RopeworkError.class);
        assertThatThrownBy(() -> new DiameterReading(Length.ofFeet(0), Length.ofInches(0), NOMINAL))
                .isInstanceOf(RopeworkError.class);
    }

    @Test
    void aReadingPrintsTheWayTheSheetReads() {
        assertThat(reading(1200, "1.352")).hasToString("1.352\" at 1200 ft");
    }

    @Test
    void aLayReadingReportsHowFarItHasOpenedOut() {
        Length nominal = ROPE.spec().layLength();
        LayReading stretched = new LayReading(Length.ofFeet(0), nominal.scaled("1.03"));
        assertThat(Decimals.quantize(stretched.stretchAgainst(nominal), 3)).isEqualByComparingTo("0.030");
    }

    @Test
    void aLayReadingHasToBeALength() {
        assertThatThrownBy(() -> new LayReading(Length.ofFeet(0), Length.ofInches(0)))
                .isInstanceOf(RopeworkError.class);
    }

    @Test
    void aSurveyIsSignedAndHasReadings() {
        assertThatThrownBy(() -> WearSurvey.of(ROPE, LocalDate.of(2025, 1, 15), "  ", List.of(reading(0, "1.378"))))
                .isInstanceOf(RopeworkError.class);
        assertThatThrownBy(() -> WearSurvey.of(ROPE, LocalDate.of(2025, 1, 15), "R Halvorsen", List.of()))
                .isInstanceOf(RopeworkError.class);
    }

    @Test
    void aSurveyCannotPredateTheRope() {
        assertThatThrownBy(() ->
                        WearSurvey.of(ROPE, LocalDate.of(2019, 1, 1), "R Halvorsen", List.of(reading(0, "1.378"))))
                .isInstanceOf(RopeworkError.class);
    }

    @Test
    void aReadingPastTheEndOfTheRopeIsRefused() {
        assertThatThrownBy(() -> survey(reading(12000, "1.378"))).isInstanceOf(RopeworkError.class);
    }

    @Test
    void theSmallestReadingIsTheOneThatMatters() {
        WearSurvey walked = survey(reading(0, "1.372"), reading(1200, "1.351"), reading(2400, "1.366"));
        assertThat(walked.smallest().station()).isEqualTo(Length.ofFeet(1200));
        assertThat(Decimals.quantize(walked.percentOfNominal(walked.smallest()), 1))
                .isEqualByComparingTo("98.0");
    }

    @Test
    void theWorstOvalityIsFoundSeparately() {
        WearSurvey walked = survey(reading(0, "1.372", "1.370"), reading(1200, "1.360", "1.300"));
        assertThat(walked.worstOvality().station()).isEqualTo(Length.ofFeet(1200));
    }

    @Test
    void readingsBelowTheCondemningDiameterAreListed() {
        WearSurvey walked = survey(reading(0, "1.372"), reading(1200, "1.270"), reading(2400, "1.240"));
        List<DiameterReading> below = walked.belowCondemning();
        assertThat(below.stream().map(r -> r.station().feet()))
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(BigDecimal.valueOf(1200), BigDecimal.valueOf(2400));
    }

    @Test
    void aSurveyWithNoLayReadingsReportsNoStretch() {
        assertThat(survey(reading(0, "1.376")).worstLayStretch()).isEqualByComparingTo("0");
    }

    @Test
    void theStationsWalkedComeBackInTheOrderRecorded() {
        WearSurvey walked = survey(reading(0, "1.372"), reading(1200, "1.370"), reading(2400, "1.368"));
        assertThat(walked.stations().stream().map(Length::feet))
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(BigDecimal.valueOf(0), BigDecimal.valueOf(1200), BigDecimal.valueOf(2400));
    }
}
