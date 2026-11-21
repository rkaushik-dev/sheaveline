package io.github.rkaushikdev.sheaveline.ropework;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.measures.Temperature;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TensionTest {

    private static TensionSystem system(TensionKind kind, long carriageFeet, long travelFeet, long pounds) {
        return new TensionSystem(kind, Load.ofPounds(pounds), Length.ofFeet(travelFeet), Length.ofFeet(carriageFeet));
    }

    private static TensionSystem system(TensionKind kind, long carriageFeet) {
        return system(kind, carriageFeet, 16, 34500);
    }

    private static TensionSystem system(TensionKind kind) {
        return system(kind, 6);
    }

    private static TensionSystem system(long carriageFeet) {
        return system(TensionKind.HYDRAULIC, carriageFeet);
    }

    private static TensionSystem system() {
        return system(6);
    }

    private static TensionReading reading(long pounds, long carriageFeet) {
        return new TensionReading(
                LocalDate.of(2025, 1, 15),
                Length.ofFeet(carriageFeet),
                Load.ofPounds(pounds),
                Temperature.ofDegreesF(18));
    }

    private static TensionReading reading(long pounds) {
        return reading(pounds, 7);
    }

    private static TensionReading reading() {
        return reading(34800);
    }

    @Test
    void aCounterweightHoldsOneTensionAndARamDoesNot() {
        assertThat(TensionKind.COUNTERWEIGHT.holdsConstant()).isTrue();
        assertThat(TensionKind.HYDRAULIC.holdsConstant()).isFalse();
        assertThat(TensionKind.COUNTERWEIGHT.isPassive()).isTrue();
        assertThat(TensionKind.SCREW.isPassive()).isTrue();
        assertThat(TensionKind.HYDRAULIC.isPassive()).isFalse();
    }

    @Test
    void aScrewTakeUpIsNotAllowedUnderPassengers() {
        assertThat(TensionKind.SCREW.allowedOnPassengerLifts()).isFalse();
        assertThat(TensionKind.COUNTERWEIGHT.allowedOnPassengerLifts()).isTrue();
        assertThat(TensionKind.HYDRAULIC.allowedOnPassengerLifts()).isTrue();
    }

    @Test
    void aSystemChecksItsOwnNumbers() {
        assertThatThrownBy(() -> system(TensionKind.HYDRAULIC, 6, 16, 0)).isInstanceOf(RopeworkError.class);
        assertThatThrownBy(() -> system(TensionKind.HYDRAULIC, 6, 0, 34500)).isInstanceOf(RopeworkError.class);
        assertThatThrownBy(() -> system(-1)).isInstanceOf(RopeworkError.class);
    }

    @Test
    void theCarriageReportsHowFarItHasLeftToGo() {
        TensionSystem held = system(TensionKind.HYDRAULIC, 6, 16, 34500);
        assertThat(held.remainingOut()).isEqualTo(Length.ofFeet(10));
        assertThat(held.remainingIn()).isEqualTo(Length.ofFeet(6));
        assertThat(held.positionPercent()).isEqualByComparingTo("37.5");
    }

    @Test
    void aCarriageInsideItsTravelWithRoomAtBothEnds() {
        assertThat(system(6).isWithinTravel()).isTrue();
        assertThat(system(6).hasReserve()).isTrue();
    }

    @Test
    void aCarriageWithinATenthOfAStopHasNoReserve() {
        assertThat(system(1).hasReserve()).isFalse();
        assertThat(system(15).hasReserve()).isFalse();
        assertThat(system(1).isWithinTravel()).isTrue();
    }

    @Test
    void aCarriagePastTheStopIsOutOfTravel() {
        assertThat(system(18).isWithinTravel()).isFalse();
    }

    @Test
    void aShorteningMovesTheCarriageByHalfOfWhatCameOut() {
        TensionSystem moved = system(6).afterShortening(Length.ofFeet(40));
        assertThat(moved.carriage()).isEqualTo(Length.ofFeet(26));
        assertThatThrownBy(() -> system().afterShortening(Length.ofFeet(0))).isInstanceOf(RopeworkError.class);
    }

    @Test
    void theCarriageSaysHowManyMoreCutsItHasRoomFor() {
        TensionSystem held = system(TensionKind.HYDRAULIC, 2, 16, 34500);
        assertThat(held.shorteningsLeft(Length.ofFeet(8))).isEqualTo(3);
        assertThat(system(15).shorteningsLeft(Length.ofFeet(8))).isZero();
        assertThatThrownBy(() -> held.shorteningsLeft(Length.ofFeet(0))).isInstanceOf(RopeworkError.class);
    }

    @Test
    void aCounterweightIsHeldToATighterBandThanARam() {
        TensionSystem weight = system(TensionKind.COUNTERWEIGHT);
        TensionSystem ram = system(TensionKind.HYDRAULIC);
        assertThat(weight.tolerance()).isEqualTo(Load.ofPounds(690));
        assertThat(ram.tolerance()).isEqualTo(Load.ofPounds(1725));
        assertThat(weight.tolerance()).isLessThan(ram.tolerance());
    }

    @Test
    void aMeasuredTensionInsideTheBandIsAccepted() {
        TensionSystem ram = system(TensionKind.HYDRAULIC);
        assertThat(ram.accepts(Load.ofPounds(35000))).isTrue();
        assertThat(ram.accepts(Load.ofPounds(33000))).isTrue();
        assertThat(ram.accepts(Load.ofPounds(31000))).isFalse();
    }

    @Test
    void theSameReadingCanPassOnOneSystemAndFailOnAnother() {
        Load reading = Load.ofPounds(35800);
        assertThat(system(TensionKind.HYDRAULIC).accepts(reading)).isTrue();
        assertThat(system(TensionKind.COUNTERWEIGHT).accepts(reading)).isFalse();
    }

    @Test
    void movingTheCarriageMakesANewRecord() {
        TensionSystem before = system(6);
        TensionSystem after = before.movedTo(Length.ofFeet(9));
        assertThat(before.carriage()).isEqualTo(Length.ofFeet(6));
        assertThat(after.carriage()).isEqualTo(Length.ofFeet(9));
        assertThat(after.expectedTension()).isEqualTo(before.expectedTension());
    }

    @Test
    void aSystemPrintsForTheMorningSheet() {
        TensionSystem printed = system(TensionKind.COUNTERWEIGHT, 8);
        assertThat(printed).hasToString("counterweight at 34,500 lb, carriage 50% out");
    }

    @Test
    void aReadingChecksItsOwnNumbers() {
        assertThatThrownBy(() -> reading(0)).isInstanceOf(RopeworkError.class);
        assertThatThrownBy(() -> reading(34800, -2)).isInstanceOf(RopeworkError.class);
    }

    @Test
    void aReadingIsComparedAgainstTheSystemItCameOff() {
        TensionSystem held = system(6);
        assertThat(reading().deviationFrom(held)).isEqualTo(Load.ofPounds(300));
        assertThat(reading().agreesWith(held)).isTrue();
        assertThat(reading(30000).agreesWith(held)).isFalse();
    }

    @Test
    void aReadingSaysHowFarTheCarriageHasTravelledSince() {
        TensionSystem held = system(6);
        assertThat(reading(34800, 7).carriageMoved(held)).isEqualTo(Length.ofFeet(1));
        assertThat(reading(34800, 5).carriageMoved(held)).isEqualTo(Length.ofFeet(-1));
    }

    @Test
    void aReadingPrintsShort() {
        assertThat(reading()).hasToString("34,800 lb on 2025-01-15");
    }
}
