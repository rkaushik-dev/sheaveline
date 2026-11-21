package io.github.rkaushikdev.sheaveline.plant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.measures.Angle;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class TowerTest {

    private static final LiftCode JP = new LiftCode("JP");

    private static SheaveAssembly support(int sheaves, String degrees) {
        return new SheaveAssembly(AssemblyKind.SUPPORT, sheaves, Angle.ofDegrees(degrees));
    }

    private static SheaveAssembly support() {
        return support(4, "1.5");
    }

    private static SheaveAssembly holdDown(String degrees) {
        return new SheaveAssembly(AssemblyKind.HOLD_DOWN, 4, Angle.ofDegrees(degrees));
    }

    private static SheaveAssembly holdDown() {
        return holdDown("-1.2");
    }

    private static Tower tower(
            int number, long stationFeet, SheaveAssembly up, SheaveAssembly down, long base, long height) {
        return new Tower(
                JP.tower(number), Length.ofFeet(stationFeet), Length.ofFeet(base), Length.ofFeet(height), up, down);
    }

    private static Tower tower(int number, long stationFeet, SheaveAssembly up, SheaveAssembly down) {
        return tower(number, stationFeet, up, down, 7400, 32);
    }

    private static Tower tower(int number, long stationFeet) {
        return tower(number, stationFeet, support(), support());
    }

    @Test
    void aSupportBendsTheRopeDownAndAHoldDownBendsItUp() {
        assertThat(support().kind().carriesDownForce()).isTrue();
        assertThat(support().kind().carriesUpForce()).isFalse();
        assertThat(holdDown().kind().carriesUpForce()).isTrue();
        assertThat(holdDown().kind().carriesDownForce()).isFalse();
    }

    @Test
    void aCombinationCarriesBoth() {
        SheaveAssembly both = new SheaveAssembly(AssemblyKind.COMBINATION, 8, Angle.ofDegrees("0.4"));
        assertThat(both.kind().carriesUpForce()).isTrue();
        assertThat(both.kind().carriesDownForce()).isTrue();
    }

    @Test
    void theSignOfTheDeviationHasToMatchTheKind() {
        assertThatThrownBy(() -> new SheaveAssembly(AssemblyKind.SUPPORT, 4, Angle.ofDegrees("-1.5")))
                .isInstanceOf(PlantError.class);
        assertThatThrownBy(() -> new SheaveAssembly(AssemblyKind.HOLD_DOWN, 4, Angle.ofDegrees("1.5")))
                .isInstanceOf(PlantError.class)
                .hasMessage("a hold down assembly bends the rope upward");
        assertThatThrownBy(() -> new SheaveAssembly(AssemblyKind.COMBINATION, 4, Angle.ofDegrees(0)))
                .isInstanceOf(PlantError.class);
    }

    @Test
    void aSupportWithNoBendIsRefused() {
        assertThatThrownBy(() -> new SheaveAssembly(AssemblyKind.SUPPORT, 4, Angle.ofDegrees(0)))
                .isInstanceOf(PlantError.class);
    }

    @Test
    void sheavesComeInEvenPairs() {
        assertThatThrownBy(() -> support(5, "1.5"))
                .isInstanceOf(PlantError.class)
                .hasMessage("a sheave assembly has an even count of at least two, not 5");
        assertThatThrownBy(() -> support(1, "1.5")).isInstanceOf(PlantError.class);
        assertThat(support(8, "1.5").sheaves()).isEqualTo(8);
    }

    @Test
    void theLineLoadSplitsAcrossTheSheaves() {
        SheaveAssembly assembly = support(4, "1.5");
        assertThat(assembly.perSheave(Load.ofPounds(4800))).isEqualTo(Load.ofPounds(1200));
        assertThat(support(8, "1.5").perSheave(Load.ofPounds(4800))).isEqualTo(Load.ofPounds(600));
    }

    @Test
    void oneAssemblyCanBeComparedAgainstAnother() {
        assertThat(support(4, "3.0").isHeavierThan(support(4, "1.5"))).isTrue();
        assertThat(holdDown("-3.0").isHeavierThan(support(4, "1.5"))).isTrue();
    }

    @Test
    void anAssemblyPrintsForTheTowerSchedule() {
        assertThat(support(6, "1.5")).hasToString("6s support at 1.50 deg");
    }

    @Test
    void aTowerCarriesItsPlaceOnTheLine() {
        Tower seven = tower(7, 2400);
        assertThat(seven.number()).isEqualTo(7);
        assertThat(seven.lift()).isEqualTo(JP);
        assertThat(seven.station()).isEqualTo(Length.ofFeet(2400));
        assertThat(seven).hasToString("JP-T7 at station 2400");
    }

    @Test
    void theCrossarmSitsAboveTheBase() {
        Tower seven = tower(7, 2400, support(), support(), 7400, 32);
        assertThat(seven.crossarmElevation()).isEqualTo(Length.ofFeet(7432));
    }

    @Test
    void aTowerAddsUpTheBendOnBothSides() {
        Tower seven = tower(7, 2400, support(4, "1.5"), support(4, "1.0"));
        assertThat(seven.totalDeviation()).isEqualTo(Angle.ofDegrees("2.5"));
    }

    @Test
    void aTowerWithAHoldDownOnEitherSideSaysSo() {
        assertThat(tower(7, 2400, support(), holdDown()).isHoldDown()).isTrue();
        assertThat(tower(7, 2400, holdDown(), support()).isHoldDown()).isTrue();
        assertThat(tower(7, 2400).isHoldDown()).isFalse();
    }

    @Test
    void aTowerCountsItsSheaves() {
        assertThat(tower(7, 2400, support(6, "1.5"), support(4, "1.5")).sheaveCount())
                .isEqualTo(10);
    }

    @Test
    void aTowerBeforeTheTerminalOrWithNoHeightIsRefused() {
        assertThatThrownBy(() -> tower(1, -10))
                .isInstanceOf(PlantError.class)
                .hasMessage("JP-T1 stands before the drive terminal");
        assertThatThrownBy(() -> tower(1, 100, support(), support(), 7400, 0)).isInstanceOf(PlantError.class);
    }

    @Test
    void aLineOrdersItsTowersAndAnswersByNumber() {
        TowerLine line = new TowerLine(List.of(tower(2, 1200), tower(1, 600), tower(3, 2000)));
        assertThat(line.size()).isEqualTo(3);
        assertThat(StreamSupport.stream(line.spliterator(), false).map(Tower::number))
                .containsExactly(1, 2, 3);
        assertThat(line.tower(2).station()).isEqualTo(Length.ofFeet(1200));
        assertThat(line.first().number()).isEqualTo(1);
        assertThat(line.last().number()).isEqualTo(3);
    }

    @Test
    void aLineNeedsTowersNumberedFromOneWithoutGaps() {
        assertThatThrownBy(() -> new TowerLine(List.of(tower(1, 600), tower(3, 2000))))
                .isInstanceOf(PlantError.class)
                .hasMessage("tower numbers run from one without gaps, found JP-T3");
        assertThatThrownBy(() -> new TowerLine(List.of(tower(2, 600), tower(3, 2000))))
                .isInstanceOf(PlantError.class);
    }

    @Test
    void aLineNeedsStationsThatClimb() {
        assertThatThrownBy(() -> new TowerLine(List.of(tower(1, 1200), tower(2, 600))))
                .isInstanceOf(PlantError.class)
                .hasMessage("JP-T2 is not further up the line than JP-T1");
        assertThatThrownBy(() -> new TowerLine(List.of(tower(1, 600), tower(2, 600))))
                .isInstanceOf(PlantError.class);
    }

    @Test
    void aLineBelongsToOneLift() {
        Tower other = new Tower(
                new LiftCode("SB").tower(2),
                Length.ofFeet(1200),
                Length.ofFeet(7400),
                Length.ofFeet(30),
                support(),
                support());
        assertThatThrownBy(() -> new TowerLine(List.of(tower(1, 600), other))).isInstanceOf(PlantError.class);
    }

    @Test
    void anEmptyLineIsRefused() {
        assertThatThrownBy(() -> new TowerLine(List.of())).isInstanceOf(PlantError.class);
    }

    @Test
    void askingForATowerThatIsNotThere() {
        TowerLine line = new TowerLine(List.of(tower(1, 600), tower(2, 1200)));
        assertThatThrownBy(() -> line.tower(3)).isInstanceOf(PlantError.class).hasMessage("JP has no tower 3");
        assertThatThrownBy(() -> line.tower(0)).isInstanceOf(PlantError.class);
    }

    @Test
    void aLinePicksOutItsHoldDownsAndItsWorstTower() {
        TowerLine line = new TowerLine(List.of(
                tower(1, 600),
                tower(2, 1200, holdDown("-2.5"), holdDown("-2.5")),
                tower(3, 2000, support(4, "3.0"), support())));
        assertThat(line.holdDowns().stream().map(Tower::number)).containsExactly(2);
        assertThat(line.steepest().number()).isEqualTo(2);
        assertThat(line.sheaveCount()).isEqualTo(24);
        assertThat(line.lift()).isEqualTo(JP);
        assertThat(line.spanStations().get(1)).isEqualTo(Length.ofFeet(1200));
    }
}
