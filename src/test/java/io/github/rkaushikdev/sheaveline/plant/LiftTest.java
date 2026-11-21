package io.github.rkaushikdev.sheaveline.plant;

import static io.github.rkaushikdev.sheaveline.Hill.JOHN_PAUL;
import static io.github.rkaushikdev.sheaveline.Hill.STRAWBERRY;
import static io.github.rkaushikdev.sheaveline.Hill.bullwheel;
import static io.github.rkaushikdev.sheaveline.Hill.johnPaul;
import static io.github.rkaushikdev.sheaveline.Hill.jpTowers;
import static io.github.rkaushikdev.sheaveline.Hill.supportAssembly;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.measures.Bearing;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.measures.Speed;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class LiftTest {

    @Test
    void theQuadDescribesItself() {
        Lift lift = johnPaul();
        assertThat(lift.code()).isEqualTo(JOHN_PAUL);
        assertThat(lift.liftClass()).isSameAs(LiftClass.DETACHABLE_CHAIR);
        assertThat(lift).hasToString("JP John Paul, detachable chairlift");
    }

    @Test
    void theLineLengthsComeOffTheTerminalsAndTheTowers() {
        Lift lift = johnPaul();
        assertThat(lift.horizontalLength()).isEqualTo(Length.ofFeet(4800));
        assertThat(lift.verticalRise()).isEqualTo(Length.ofFeet(1540));
        assertThat(lift.slopeLength()).isGreaterThan(lift.horizontalLength());
        assertThat(Decimals.quantize(lift.slopeLength().feet(), 0)).isGreaterThan(new BigDecimal(5000));
    }

    @Test
    void theSpansRunTerminalToTerminalThroughEveryTower() {
        SpanLine spans = johnPaul().spans();
        assertThat(spans.size()).isEqualTo(8);
        assertThat(spans.span(1).lower().label()).isEqualTo("John Paul base");
        assertThat(spans.span(8).upper().label()).isEqualTo("John Paul summit");
    }

    @Test
    void theDriveAndTheTensionAreFoundByRole() {
        Lift lift = johnPaul();
        assertThat(lift.driveTerminal().role()).isSameAs(TerminalRole.DRIVE);
        assertThat(lift.tensionTerminal().role()).isSameAs(TerminalRole.RETURN_TENSION);
        assertThat(lift.driveTerminal().label()).isEqualTo("John Paul base");
    }

    @Test
    void theRopeIsTwiceTheLinePlusTheBullwheels() {
        Lift lift = johnPaul();
        Length expected = lift.expectedRopeLength();
        assertThat(expected).isGreaterThan(lift.slopeLength().scaled(2));
        assertThat(lift.ropeLengthAgrees()).isTrue();
    }

    @Test
    void aRopeOfTheWrongLengthIsCaught() {
        Lift lift = johnPaul();
        HaulRope rope = lift.rope();
        HaulRope shorter = new HaulRope(rope.serial(), rope.spec(), rope.installed(), Length.ofFeet(9000), 1);
        assertThat(johnPaul().withRope(shorter).ropeLengthAgrees()).isFalse();
    }

    @Test
    void theRideTimeFallsOutOfTheSpeedAndTheLength() {
        Lift lift = johnPaul();
        assertThat(lift.rideTime()).isEqualTo(lift.designSpeed().timeToCover(lift.slopeLength()));
        assertThat(lift.rideTime().minutes()).isGreaterThan(new BigDecimal(5));
    }

    @Test
    void theFleetIsCountedByWhatIsOnTheLine() {
        Lift lift = johnPaul();
        assertThat(lift.carriersOnLine()).isEqualTo(84);
        assertThat(lift.seatsOnLine()).isEqualTo(336);
    }

    @Test
    void theLiftReportsItsAge() {
        Lift lift = johnPaul();
        assertThat(lift.ageInYears(LocalDate.of(2025, 1, 15))).isEqualTo(20);
        assertThat(lift.ageInYears(LocalDate.of(2024, 12, 3))).isEqualTo(20);
        assertThat(lift.ageInYears(LocalDate.of(2024, 12, 2))).isEqualTo(19);
        assertThatThrownBy(() -> lift.ageInYears(LocalDate.of(2000, 1, 1)))
                .isInstanceOf(PlantError.class)
                .hasMessage("2000-01-01 is before JP was built");
    }

    @Test
    void theLineRunsUphillOneWayAndDownTheOther() {
        Lift lift = johnPaul();
        assertThat(lift.uphillBearing()).isEqualTo(Bearing.ofDegrees(72));
        assertThat(lift.downhillBearing()).isEqualTo(Bearing.ofDegrees(252));
    }

    @Test
    void aTowerLineFromAnotherLiftIsRefused() {
        TowerLine other = new TowerLine(List.of(new Tower(
                STRAWBERRY.tower(1),
                Length.ofFeet(600),
                Length.ofFeet(6496),
                Length.ofFeet(34),
                supportAssembly(),
                supportAssembly())));
        assertThatThrownBy(() -> johnPaul().withTowers(other))
                .isInstanceOf(PlantError.class)
                .hasMessage("the tower line belongs to SB, not JP");
    }

    @Test
    void aTowerOutsideTheTerminalsIsRefused() {
        Lift lift = johnPaul();
        List<Tower> towers = new ArrayList<>(jpTowers().towers().subList(0, 6));
        towers.add(new Tower(
                JOHN_PAUL.tower(7),
                Length.ofFeet(5200),
                Length.ofFeet(7900),
                Length.ofFeet(28),
                supportAssembly(),
                supportAssembly()));
        TowerLine beyond = new TowerLine(towers);
        assertThatThrownBy(() -> johnPaul().withTowers(beyond))
                .isInstanceOf(PlantError.class)
                .hasMessage("JP-T7 stands above the top terminal");
        assertThat(lift.towers().last().station()).isLessThan(lift.top().station());
    }

    @Test
    void carriersThatDoNotBelongOnTheClassAreRefused() {
        Grip grip = new Grip(GripType.FIXED, "TB41", 2, "green", Load.ofPounds(2100), LocalDate.of(2019, 10, 4));
        CarrierSet fixed = new CarrierSet(IntStream.range(1, 20)
                .mapToObj(number -> new Carrier(
                        number,
                        CarrierType.CHAIR_4,
                        Load.ofPounds(410),
                        Length.ofFeetInches(6, 4),
                        Optional.of(grip),
                        true,
                        true))
                .toList());
        assertThatThrownBy(() -> johnPaul().withCarriers(fixed)).isInstanceOf(PlantError.class);
    }

    @Test
    void aRopeTooThickForTheBullwheelIsRefused() {
        Terminal narrow = new Terminal(
                TerminalRole.DRIVE,
                "John Paul base",
                Length.ofFeet(0),
                Length.ofFeet(6412),
                bullwheel(5, 0),
                johnPaul().bottom().drive(),
                Optional.empty());
        assertThatThrownBy(() -> johnPaul().withBottom(narrow))
                .isInstanceOf(PlantError.class)
                .hasMessage("the rope is too thick for the bullwheel at John Paul base");
    }

    @Test
    void aSpeedAboveWhatTheClassAllowsIsRefused() {
        assertThatThrownBy(() -> johnPaul().withDesignSpeed(Speed.ofFeetPerMinute(1400)))
                .isInstanceOf(PlantError.class)
                .hasMessage("1400 fpm is above what a detachable chairlift may run");
        assertThatThrownBy(() -> johnPaul().withDesignSpeed(Speed.ofFeetPerMinute(0)))
                .isInstanceOf(PlantError.class)
                .hasMessage("JP has no design speed");
        assertThatThrownBy(() -> johnPaul().withLiftClass(LiftClass.FIXED_CHAIR))
                .isInstanceOf(PlantError.class);
    }

    @Test
    void aSurveyThatStopsShortOfATerminalIsRefused() {
        LineProfile shorter = LineProfile.fromSurvey(new long[][] {{0, 6400}, {2000, 6940}});
        assertThatThrownBy(() -> johnPaul().withGround(shorter))
                .isInstanceOf(PlantError.class)
                .hasMessage("the survey does not reach the top terminal");
    }

    @Test
    void aRopeOlderThanTheMachineIsRefused() {
        HaulRope rope = johnPaul().rope();
        HaulRope early = new HaulRope(
                RopeSerial.parse("FAT-2003-00811"), rope.spec(), LocalDate.of(2004, 1, 1), rope.length(), 1);
        assertThatThrownBy(() -> johnPaul().withRope(early))
                .isInstanceOf(PlantError.class)
                .hasMessage("rope FAT-2003-00811 went on before JP was built");
    }

    @Test
    void aLiftCodeIsCarriedThroughToTheTowers() {
        assertThat(johnPaul().towers().lift()).isEqualTo(new LiftCode("JP"));
    }
}
