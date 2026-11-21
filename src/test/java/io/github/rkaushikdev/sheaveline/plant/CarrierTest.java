package io.github.rkaushikdev.sheaveline.plant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class CarrierTest {

    private static final LocalDate INSTALLED = LocalDate.of(2019, 9, 12);

    private static Grip grip(GripType kind, String model, int jaws, Load ratedSlip) {
        return new Grip(kind, model, jaws, "blue", ratedSlip, INSTALLED);
    }

    private static Grip grip(GripType kind) {
        return grip(kind, "AK6", 1, Load.ofPounds(2600));
    }

    private static Grip grip() {
        return grip(GripType.DETACHABLE);
    }

    private static Carrier carrier(
            int number, CarrierType kind, Load tare, Length hanger, Optional<Grip> grip, boolean bar) {
        return new Carrier(number, kind, tare, hanger, grip, bar, true);
    }

    private static Carrier carrier(int number, CarrierType kind, Optional<Grip> grip, boolean bar) {
        return carrier(number, kind, Load.ofPounds(480), Length.ofFeetInches(6, 4), grip, bar);
    }

    private static Carrier carrier(int number, CarrierType kind) {
        boolean surface = kind.isSurface();
        return carrier(number, kind, surface ? Optional.empty() : Optional.of(grip()), !kind.isEnclosed() && !surface);
    }

    private static Carrier carrier(int number) {
        return carrier(number, CarrierType.CHAIR_6);
    }

    private static Carrier carrier() {
        return carrier(1);
    }

    private static CarrierSet buildSet() {
        return new CarrierSet(
                IntStream.rangeClosed(1, 6).mapToObj(CarrierTest::carrier).toList());
    }

    @Test
    void aGripHoldsUpToItsRatedSlip() {
        assertThat(grip().holds(Load.ofPounds(2400))).isTrue();
        assertThat(grip().holds(Load.ofPounds(2600))).isTrue();
        assertThat(grip().holds(Load.ofPounds(2700))).isFalse();
    }

    @Test
    void aTowHangerIsNotAGrip() {
        assertThatThrownBy(() -> grip(GripType.TOW))
                .isInstanceOf(PlantError.class)
                .hasMessage("a tow hanger is not a grip");
    }

    @Test
    void aGripChecksItsOwnDescription() {
        assertThatThrownBy(() -> grip(GripType.DETACHABLE, "AK6", 3, Load.ofPounds(2600)))
                .isInstanceOf(PlantError.class)
                .hasMessage("a grip has one or two jaws, not 3");
        assertThatThrownBy(() -> grip(GripType.DETACHABLE, "AK6", 1, Load.ofPounds(0)))
                .isInstanceOf(PlantError.class);
        assertThatThrownBy(() -> grip(GripType.DETACHABLE, "   ", 1, Load.ofPounds(2600)))
                .isInstanceOf(PlantError.class);
    }

    @Test
    void aGripReportsItsAgeAndRefusesADateBeforeItWentOn() {
        assertThat(grip().ageInDays(LocalDate.of(2020, 9, 11))).isEqualTo(365);
        assertThatThrownBy(() -> grip().ageInDays(LocalDate.of(2019, 1, 1))).isInstanceOf(PlantError.class);
    }

    @Test
    void bothGripKindsAreSlipTested() {
        assertThat(grip().isSlipTested()).isTrue();
        assertThat(grip(GripType.FIXED).isSlipTested()).isTrue();
        assertThat(grip()).hasToString("AK6 detachable grip");
    }

    @Test
    void aCarrierCarriesAHundredAndSeventyPoundsASeat() {
        assertThat(carrier().seats()).isEqualTo(6);
        assertThat(carrier().designLoad()).isEqualTo(Load.ofPounds(1020));
        assertThat(carrier().gross()).isEqualTo(Load.ofPounds(1500));
    }

    @Test
    void aCarrierOffTheRopeNeedsAGripAndASurfaceOneDoesNot() {
        assertThatThrownBy(() -> carrier(1, CarrierType.CHAIR_4, Optional.empty(), true))
                .isInstanceOf(PlantError.class)
                .hasMessage("carrier 1 hangs off the rope and needs a grip");
        assertThatThrownBy(() -> carrier(1, CarrierType.TBAR, Optional.of(grip()), false))
                .isInstanceOf(PlantError.class);
        assertThat(carrier(1, CarrierType.TBAR, Optional.empty(), false).grip()).isEmpty();
    }

    @Test
    void anEnclosedCarrierHasNoBar() {
        assertThatThrownBy(() -> carrier(1, CarrierType.CABIN_8, Optional.of(grip()), true))
                .isInstanceOf(PlantError.class);
        assertThat(carrier(1, CarrierType.CABIN_8, Optional.of(grip()), false)
                        .kind()
                        .isEnclosed())
                .isTrue();
    }

    @Test
    void aCarrierChecksItsOwnNumbers() {
        Optional<Grip> held = Optional.of(grip());
        assertThatThrownBy(() -> carrier(0)).isInstanceOf(PlantError.class);
        assertThatThrownBy(
                        () -> carrier(1, CarrierType.CHAIR_6, Load.ofPounds(0), Length.ofFeetInches(6, 4), held, true))
                .isInstanceOf(PlantError.class);
        assertThatThrownBy(() -> carrier(1, CarrierType.CHAIR_6, Load.ofPounds(480), Length.ofFeet(0), held, true))
                .isInstanceOf(PlantError.class);
    }

    @Test
    void askingASurfaceCarrierForItsGrip() {
        assertThatThrownBy(() ->
                        carrier(1, CarrierType.PLATTER, Optional.empty(), false).gripOf())
                .isInstanceOf(PlantError.class)
                .hasMessage("carrier 1 has no grip");
        assertThat(carrier().gripOf().model()).isEqualTo("AK6");
    }

    @Test
    void aCarrierBelongsOnAClassThatTakesItWithAMatchingGrip() {
        Carrier six = carrier(1, CarrierType.CHAIR_6);
        assertThat(six.suits(LiftClass.DETACHABLE_CHAIR)).isTrue();
        assertThat(six.suits(LiftClass.FIXED_CHAIR)).isFalse();
        assertThatThrownBy(() -> six.requireSuits(LiftClass.FIXED_CHAIR))
                .isInstanceOf(PlantError.class)
                .hasMessage("carrier 1 does not belong on a fixed grip chairlift");
    }

    @Test
    void theRightCarrierWithTheWrongGripStillDoesNotBelong() {
        Carrier quad = carrier(1, CarrierType.CHAIR_4, Optional.of(grip(GripType.DETACHABLE)), true);
        assertThat(quad.suits(LiftClass.FIXED_CHAIR)).isFalse();
        assertThat(quad.suits(LiftClass.DETACHABLE_CHAIR)).isTrue();
        Carrier fixed = carrier(1, CarrierType.CHAIR_4, Optional.of(grip(GripType.FIXED)), true);
        assertThat(fixed.suits(LiftClass.FIXED_CHAIR)).isTrue();
    }

    @Test
    void aSurfaceCarrierBelongsOnlyOnASurfaceLift() {
        Carrier bar = carrier(1, CarrierType.TBAR, Optional.empty(), false);
        assertThat(bar.suits(LiftClass.SURFACE_TOW)).isTrue();
        assertThat(bar.suits(LiftClass.FIXED_CHAIR)).isFalse();
    }

    @Test
    void aCarrierComesOffTheLineWithoutBeingDeleted() {
        Carrier off = carrier().withdrawn();
        assertThat(off.inService()).isFalse();
        assertThat(off.number()).isEqualTo(1);
        assertThat(off).hasToString("carrier 1, six place chair (off line)");
        assertThat(carrier()).hasToString("carrier 1, six place chair");
    }

    @Test
    void aSetSortsAndAnswersByNumber() {
        CarrierSet fleet = new CarrierSet(List.of(carrier(3), carrier(1), carrier(2)));
        assertThat(StreamSupport.stream(fleet.spliterator(), false).map(Carrier::number))
                .containsExactly(1, 2, 3);
        assertThat(fleet.carrier(2).number()).isEqualTo(2);
        assertThat(fleet.size()).isEqualTo(3);
        assertThat(fleet.kind()).isSameAs(CarrierType.CHAIR_6);
    }

    @Test
    void askingForACarrierThatIsNotInTheSet() {
        assertThatThrownBy(() -> buildSet().carrier(99))
                .isInstanceOf(PlantError.class)
                .hasMessage("there is no carrier 99");
    }

    @Test
    void aSetRefusesDuplicatesAnEmptyFleetAndAMixedOne() {
        assertThatThrownBy(() -> new CarrierSet(List.of(carrier(1), carrier(1))))
                .isInstanceOf(PlantError.class);
        assertThatThrownBy(() -> new CarrierSet(List.of())).isInstanceOf(PlantError.class);
        assertThatThrownBy(() -> new CarrierSet(List.of(carrier(1), carrier(2, CarrierType.CHAIR_4))))
                .isInstanceOf(PlantError.class);
    }

    @Test
    void withdrawnCarriersDoNotCountTowardTheLoad() {
        CarrierSet fleet =
                new CarrierSet(List.of(carrier(1), carrier(2), carrier(3).withdrawn()));
        assertThat(fleet.onLine()).hasSize(2);
        assertThat(fleet.withdrawn()).hasSize(1);
        assertThat(fleet.seatsOnLine()).isEqualTo(12);
        assertThat(fleet.designLoad()).isEqualTo(Load.ofPounds(2040));
        assertThat(fleet.gross()).isEqualTo(Load.ofPounds(3000));
    }

    @Test
    void aWholeSetCanBeCheckedAgainstAClass() {
        assertThatCode(() -> buildSet().requireSuits(LiftClass.DETACHABLE_CHAIR))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> buildSet().requireSuits(LiftClass.FIXED_CHAIR)).isInstanceOf(PlantError.class);
    }

    @Test
    void gapsInThePaintedNumbersAreReported() {
        CarrierSet fleet = new CarrierSet(List.of(carrier(1), carrier(2), carrier(5)));
        assertThat(fleet.missingNumbers()).containsExactly(3, 4);
        assertThat(buildSet().missingNumbers()).isEmpty();
    }
}
