package io.github.rkaushikdev.sheaveline.plant;

import static io.github.rkaushikdev.sheaveline.Hill.JOHN_PAUL;
import static io.github.rkaushikdev.sheaveline.Hill.STRAWBERRY;
import static io.github.rkaushikdev.sheaveline.Hill.WILDCAT;
import static io.github.rkaushikdev.sheaveline.Hill.johnPaul;
import static io.github.rkaushikdev.sheaveline.Hill.strawberry;
import static io.github.rkaushikdev.sheaveline.Hill.theResort;
import static io.github.rkaushikdev.sheaveline.Hill.wildcat;
import static io.github.rkaushikdev.sheaveline.HillCalendar.theSeason;
import static io.github.rkaushikdev.sheaveline.HillCalendar.theSite;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class RegistryTest {

    @Test
    void theResortHoldsItsLiftsInCodeOrder() {
        Resort resort = theResort();
        assertThat(resort.size()).isEqualTo(3);
        assertThat(resort.codes()).containsExactly(JOHN_PAUL, STRAWBERRY, WILDCAT);
        assertThat(resort).hasToString("Ogden Peak, 3 lifts");
    }

    @Test
    void aLiftIsFoundByCodeOrByText() {
        Resort resort = theResort();
        assertThat(resort.lift(JOHN_PAUL).name()).isEqualTo("John Paul");
        assertThat(resort.lift("sb").name()).isEqualTo("Strawberry");
        assertThat(resort.contains(JOHN_PAUL)).isTrue();
        assertThat(resort.contains(new LiftCode("ZZ"))).isFalse();
    }

    @Test
    void askingForALiftThatIsNotThere() {
        assertThatThrownBy(() -> theResort().lift("zz"))
                .isInstanceOf(PlantError.class)
                .hasMessage("Ogden Peak has no lift ZZ");
    }

    @Test
    void aResortNeedsANameAndAtLeastOneLift() {
        assertThatThrownBy(() -> Resort.of("  ", theSite(), theSeason(), List.of(johnPaul())))
                .isInstanceOf(PlantError.class);
        assertThatThrownBy(() -> Resort.of("Ogden Peak", theSite(), theSeason(), List.of()))
                .isInstanceOf(PlantError.class)
                .hasMessage("Ogden Peak has no lifts");
    }

    @Test
    void twoLiftsCannotShareACode() {
        assertThatThrownBy(() -> Resort.of("Ogden Peak", theSite(), theSeason(), List.of(johnPaul(), johnPaul())))
                .isInstanceOf(PlantError.class);
    }

    @Test
    void oneRopeCannotBeOnTwoMachines() {
        Lift borrowed = strawberry().withRope(johnPaul().rope());
        assertThatThrownBy(() -> Resort.of("Ogden Peak", theSite(), theSeason(), List.of(johnPaul(), borrowed)))
                .isInstanceOf(PlantError.class)
                .hasMessage("one rope serial is recorded on two machines");
    }

    @Test
    void theMachinesAreSortedByWhatTheyAre() {
        Resort resort = theResort();
        assertThat(resort.aerial().stream().map(Lift::code)).containsExactly(JOHN_PAUL, STRAWBERRY);
        assertThat(resort.surface().stream().map(Lift::code)).containsExactly(WILDCAT);
        assertThat(resort.detachables().stream().map(Lift::code)).containsExactly(JOHN_PAUL);
        assertThat(resort.ofClass(LiftClass.FIXED_CHAIR)).containsExactly(resort.lift(STRAWBERRY));
    }

    /** John Paul carries six sheave assemblies on towers four and five. */
    @Test
    void theHillAddsUpItsSeatsAndItsSteel() {
        Resort resort = theResort();
        assertThat(resort.carriersOnLine()).isEqualTo(84 + 46 + 22);
        assertThat(resort.seatsOnLine()).isEqualTo(84 * 4 + 46 * 3 + 22 * 2);
        assertThat(resort.towerCount()).isEqualTo(12);
        assertThat(resort.sheaveCount()).isEqualTo(60 + 24 + 8);
    }

    @Test
    void theRopeSerialsComeBackAsASet() {
        assertThat(theResort().ropeSerials())
                .contains(RopeSerial.parse("FAT-2019-04412"))
                .hasSize(3);
    }

    @Test
    void theOldestAndNewestMachinesAreFound() {
        Resort resort = theResort();
        assertThat(resort.oldest().code()).isEqualTo(STRAWBERRY);
        assertThat(resort.newest().code()).isEqualTo(JOHN_PAUL);
    }

    @Test
    void theMachinesBuiltBeforeADate() {
        List<Lift> early = theResort().commissionedBefore(LocalDate.of(2000, 1, 1));
        assertThat(early.stream().map(Lift::code)).containsExactly(STRAWBERRY, WILDCAT);
    }

    @Test
    void onlyTheAerialMachinesNeedAnEvacuationPlan() {
        assertThat(theResort().needingEvacuationPlans().stream().map(Lift::code))
                .containsExactly(JOHN_PAUL, STRAWBERRY);
    }

    @Test
    void addingALiftMakesANewRegistry() {
        Resort resort = theResort();
        Resort smaller = resort.withoutLift(WILDCAT);
        assertThat(smaller.size()).isEqualTo(2);
        assertThat(resort.size()).isEqualTo(3);
        assertThat(smaller.withLift(wildcat()).codes()).isEqualTo(resort.codes());
    }

    @Test
    void removingALiftThatIsNotThere() {
        assertThatThrownBy(() -> theResort().withoutLift("zz")).isInstanceOf(PlantError.class);
    }

    @Test
    void addingALiftThatIsAlreadyThereIsRefused() {
        assertThatThrownBy(() -> theResort().withLift(johnPaul())).isInstanceOf(PlantError.class);
    }

    @Test
    void theResortCarriesTheSiteAndTheSeason() {
        Resort resort = theResort();
        assertThat(resort.site().name()).isEqualTo("Ogden Peak");
        assertThat(resort.season().label()).isEqualTo("2024/25");
        assertThat(resort.season().isOperating(LocalDate.of(2025, 1, 15))).isTrue();
    }

    @Test
    void theSurfaceTowHasNoGripsAndStillBelongs() {
        Lift tow = theResort().lift(WILDCAT);
        assertThat(tow.carriers().kind().isSurface()).isTrue();
        assertThat(StreamSupport.stream(tow.carriers().spliterator(), false))
                .allMatch(one -> one.grip().isEmpty());
        assertThat(tow.liftClass().needsEvacuationPlan()).isFalse();
    }
}
