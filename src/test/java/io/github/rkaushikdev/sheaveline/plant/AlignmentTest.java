package io.github.rkaushikdev.sheaveline.plant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.Hill;
import io.github.rkaushikdev.sheaveline.measures.Angle;
import io.github.rkaushikdev.sheaveline.measures.Length;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class AlignmentTest {

    private static final TowerLine LINE = Hill.jpTowers();
    private static final TowerRef T4 = TowerRef.parse("JP-T4");
    private static final TowerRef T5 = TowerRef.parse("JP-T5");
    private static final LocalDate AUTUMN = LocalDate.of(2024, 10, 1);

    private static TowerShot asBuilt(TowerRef ref, String offset, String settled) {
        Tower tower = LINE.towers().stream()
                .filter(one -> one.ref().equals(ref))
                .findFirst()
                .orElseThrow();
        return new TowerShot(
                ref, tower.station(), tower.crossarmElevation().minus(Length.parse(settled)), Length.parse(offset));
    }

    private static TowerShot asBuilt(TowerRef ref) {
        return asBuilt(ref, "0 in", "0 in");
    }

    private static TowerShot offset(TowerRef ref, String offset) {
        return asBuilt(ref, offset, "0 in");
    }

    private static TowerShot settled(TowerRef ref, String settled) {
        return asBuilt(ref, "0 in", settled);
    }

    private static AlignmentSurvey survey(LocalDate taken, String surveyor, TowerShot... shots) {
        List<TowerShot> taking = shots.length > 0
                ? List.of(shots)
                : LINE.towers().stream().map(one -> asBuilt(one.ref())).toList();
        return AlignmentSurvey.of(LINE, taken, surveyor, taking);
    }

    private static AlignmentSurvey survey(TowerShot... shots) {
        return survey(AUTUMN, "H Ogilvie", shots);
    }

    private static AlignmentSurvey surveyOn(LocalDate taken, TowerShot... shots) {
        return survey(taken, "H Ogilvie", shots);
    }

    @Test
    void aTowerShotWhereTheDrawingSaysItIsHasMovedNothing() {
        AlignmentSurvey made = survey();
        assertThat(made.settlement(T4)).isEqualTo(new Length(0));
        assertThat(made.lean(T4)).isEqualTo(new Angle(0));
        assertThat(made.isClean()).isTrue();
        assertThat(made.findings()).isEmpty();
    }

    @Test
    void aSurveyNamesWhoShotIt() {
        assertThatThrownBy(() -> survey(AUTUMN, "  "))
                .isInstanceOf(PlantError.class)
                .hasMessageContaining("names who shot it");
    }

    @Test
    void aTowerIsShotOnce() {
        assertThatThrownBy(() -> new AlignmentSurvey(LINE, AUTUMN, "H Ogilvie", List.of(asBuilt(T4), asBuilt(T4))))
                .isInstanceOf(PlantError.class)
                .hasMessageContaining("shot once");
    }

    @Test
    void aTowerFromAnotherMachineDoesNotBelongInThisSurvey() {
        TowerShot other =
                new TowerShot(TowerRef.parse("SB-T2"), Length.ofFeet(600), Length.ofFeet(6500), new Length(0));
        assertThatThrownBy(() -> survey(other)).isInstanceOf(PlantError.class).hasMessageContaining("not on JP");
    }

    @Test
    void aTowerNobodyShotIsNamed() {
        AlignmentSurvey made = survey(asBuilt(T4));
        assertThat(made.missed()).contains(T5);
        assertThat(made.isClean()).isFalse();
        assertThat(made.findings()).anyMatch(one -> one.contains("was not shot"));
    }

    @Test
    void theOffsetBecomesAnAngleOverTheTowerHeight() {
        AlignmentSurvey made = survey(offset(T4, "6 in"));
        Tower tower = made.tower(T4);
        assertThat(made.lean(T4)).isEqualTo(Angle.ofRiseRun(Length.ofInches(6), tower.height()));
    }

    @Test
    void aShortTowerAndATallOneWithTheSameOffsetAreNotTheSameTrouble() {
        Tower shortest = LINE.towers().stream()
                .min(Comparator.comparingLong(one -> one.height().mils()))
                .orElseThrow();
        Tower tallest = LINE.towers().stream()
                .max(Comparator.comparingLong(one -> one.height().mils()))
                .orElseThrow();
        AlignmentSurvey made = survey(offset(shortest.ref(), "6 in"), offset(tallest.ref(), "6 in"));
        assertThat(made.lean(shortest.ref())).isGreaterThan(made.lean(tallest.ref()));
    }

    @Test
    void aTowerOutOfPlumbIsNamed() {
        AlignmentSurvey made = survey(offset(T4, "18 in"));
        assertThat(made.outOfPlumb()).contains(T4);
        assertThat(made.isClean()).isFalse();
        assertThat(made.findings()).anyMatch(one -> one.contains("leans"));
    }

    @Test
    void aTowerInsideTheToleranceIsLeftAlone() {
        assertThat(survey(offset(T4, "0.5 in")).outOfPlumb()).isEmpty();
    }

    @Test
    void aTowerThatHasSettledTooFarIsNamed() {
        AlignmentSurvey made = survey(settled(T4, "4 in"));
        assertThat(made.settledTooFar()).contains(T4);
        assertThat(made.settlement(T4)).isEqualTo(Length.ofInches(4));
        assertThat(made.findings()).contains("JP-T4 has settled 4.00\"");
    }

    @Test
    void settlingInsideTheToleranceIsLeftAlone() {
        assertThat(survey(settled(T4, "1 in")).settledTooFar()).isEmpty();
        assertThat(Length.ofInches("2.000")).isEqualTo(AlignmentSurvey.SETTLEMENT_TOLERANCE);
    }

    @Test
    void askingAboutATowerNobodyShot() {
        AlignmentSurvey made = survey(asBuilt(T4));
        assertThatThrownBy(() -> made.lean(T5))
                .isInstanceOf(PlantError.class)
                .hasMessage("JP-T5 was not shot on 2024-10-01");
        assertThatThrownBy(() -> made.settlement(T5))
                .isInstanceOf(PlantError.class)
                .hasMessageContaining("was not shot");
    }

    @Test
    void askingAboutATowerThatIsNotOnTheLine() {
        assertThatThrownBy(() -> survey().tower(TowerRef.parse("JP-T9")))
                .isInstanceOf(PlantError.class)
                .hasMessageContaining("there is no");
    }

    @Test
    void aHistoryHasSurveysInIt() {
        assertThatThrownBy(() -> new AlignmentHistory(List.of()))
                .isInstanceOf(PlantError.class)
                .hasMessageContaining("has surveys in it");
    }

    @Test
    void twoSurveysOnTheSameDayCannotBothBeTheTrend() {
        assertThatThrownBy(() -> AlignmentHistory.of(List.of(survey(), survey())))
                .isInstanceOf(PlantError.class)
                .hasMessageContaining("same day");
    }

    @Test
    void oneSurveyCarriesNoRate() {
        AlignmentHistory made = AlignmentHistory.of(List.of(survey(asBuilt(T4))));
        assertThat(made.leanAYear(T4)).isEmpty();
        assertThat(made.settlingAYear(T4)).isEmpty();
        assertThat(made.yearsUntilOutOfPlumb(T4)).isEmpty();
    }

    @Test
    void aTowerGoingOverReadsAsAnAngleAYear() {
        AlignmentHistory made = AlignmentHistory.of(List.of(
                surveyOn(LocalDate.of(2020, 10, 1), offset(T4, "0 in")),
                surveyOn(LocalDate.of(2024, 10, 1), offset(T4, "4 in"))));
        Angle rate = made.leanAYear(T4).orElseThrow();
        assertThat(rate.millidegrees()).isPositive();
        Tower tower = made.latest().tower(T4);
        Angle overFourYears = Angle.ofRiseRun(Length.ofInches(4), tower.height());
        assertThat(Math.abs(rate.millidegrees() * 4 - overFourYears.millidegrees()))
                .isLessThanOrEqualTo(8);
    }

    @Test
    void aFootingGoingDownReadsAsALengthAYear() {
        AlignmentHistory made = AlignmentHistory.of(List.of(
                surveyOn(LocalDate.of(2022, 10, 1), settled(T4, "0 in")),
                surveyOn(LocalDate.of(2024, 10, 1), settled(T4, "4 in"))));
        Length rate = made.settlingAYear(T4).orElseThrow();
        assertThat(Math.abs(rate.mils() - Length.ofInches(2).mils())).isLessThanOrEqualTo(4);
    }

    @Test
    void aTowerThatHasNotMovedCarriesNoProjection() {
        AlignmentHistory made = AlignmentHistory.of(List.of(
                surveyOn(LocalDate.of(2020, 10, 1), asBuilt(T4)), surveyOn(LocalDate.of(2024, 10, 1), asBuilt(T4))));
        assertThat(made.leanAYear(T4)).contains(new Angle(0));
        assertThat(made.yearsUntilOutOfPlumb(T4)).isEmpty();
    }

    @Test
    void whenTheLeanReachesTheTolerance() {
        AlignmentHistory made = AlignmentHistory.of(List.of(
                surveyOn(LocalDate.of(2020, 10, 1), offset(T4, "0 in")),
                surveyOn(LocalDate.of(2024, 10, 1), offset(T4, "4 in"))));
        assertThat(made.yearsUntilOutOfPlumb(T4).orElseThrow()).isPositive();
    }

    @Test
    void aTowerAlreadyOverTheToleranceHasNoYearsLeft() {
        AlignmentHistory made = AlignmentHistory.of(List.of(
                surveyOn(LocalDate.of(2020, 10, 1), offset(T4, "0 in")),
                surveyOn(LocalDate.of(2024, 10, 1), offset(T4, "24 in"))));
        assertThat(made.yearsUntilOutOfPlumb(T4).orElseThrow()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void theTowersGoingOverComeBackSoonestFirst() {
        AlignmentHistory made = AlignmentHistory.of(List.of(
                surveyOn(LocalDate.of(2020, 10, 1), offset(T4, "0 in"), offset(T5, "0 in")),
                surveyOn(LocalDate.of(2024, 10, 1), offset(T4, "1 in"), offset(T5, "5 in"))));
        assertThat(made.moving()).containsExactly(T5, T4);
        assertThat(Angle.ofDegrees("0.50")).isEqualTo(TowerShot.PLUMB_TOLERANCE);
    }
}
