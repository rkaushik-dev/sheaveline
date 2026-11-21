package io.github.rkaushikdev.sheaveline.plant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.MeasureError;
import io.github.rkaushikdev.sheaveline.ParseError;
import io.github.rkaushikdev.sheaveline.RangeError;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IdentityTest {

    @Test
    void aLiftCodeIsUpperCase() {
        assertThat(LiftCode.parse("jp").value()).isEqualTo("JP");
        assertThat(new LiftCode("STRAWBRY")).hasToString("STRAWBRY");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1A", "toolongforalift", "J P", "J-P", "j"})
    void aLiftCodeRefusesWhatIsNotOne(String text) {
        assertThatThrownBy(() -> LiftCode.parse(text)).isInstanceOf(ParseError.class);
    }

    @Test
    void aTowerIsALiftAndANumber() {
        TowerRef tower = new LiftCode("JP").tower(7);
        assertThat(tower.lift()).isEqualTo(new LiftCode("JP"));
        assertThat(tower.number()).isEqualTo(7);
        assertThat(tower).hasToString("JP-T7");
    }

    @ParameterizedTest
    @ValueSource(strings = {"JP-T7", "JPT7", "jp t7", "JP/T7"})
    void aTowerParsesFromTheShapesACrewWrites(String text) {
        assertThat(TowerRef.parse(text)).isEqualTo(new LiftCode("JP").tower(7));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "JP", "T7", "JP-T0", "JP-T100", "JP-TX"})
    void aTowerRefusesWhatIsNotOne(String text) {
        assertThatThrownBy(() -> TowerRef.parse(text)).isInstanceOfAny(ParseError.class, RangeError.class);
    }

    @Test
    void aTowerNumberStaysInsideTheLine() {
        assertThatThrownBy(() -> new LiftCode("JP").tower(0)).isInstanceOf(RangeError.class);
        assertThatThrownBy(() -> new LiftCode("JP").tower(100)).isInstanceOf(RangeError.class);
    }

    @Test
    void towersSortByNumberNotByText() {
        LiftCode lift = new LiftCode("JP");
        List<TowerRef> towers = List.of(lift.tower(10), lift.tower(2), lift.tower(1));
        assertThat(towers.stream().sorted().map(TowerRef::number)).containsExactly(1, 2, 10);
        assertThat(lift.tower(2)).isLessThan(lift.tower(10));
        assertThat(lift.tower(10)).isGreaterThan(lift.tower(2));
        assertThat(lift.tower(2)).isLessThanOrEqualTo(lift.tower(2));
        assertThat(lift.tower(2)).isGreaterThanOrEqualTo(lift.tower(2));
    }

    @Test
    void theNextTowerUpTheLine() {
        assertThat(new LiftCode("JP").tower(7).nextUp()).isEqualTo(new LiftCode("JP").tower(8));
    }

    @Test
    void aRopeSerialCarriesTheMillAndTheYear() {
        RopeSerial rope = RopeSerial.parse("FAT-2019-04412");
        assertThat(rope.mill()).isEqualTo("FAT");
        assertThat(rope.year()).isEqualTo(2019);
        assertThat(rope.serial()).isEqualTo("04412");
        assertThat(rope).hasToString("FAT-2019-04412");
    }

    @Test
    void aRopeSerialIsCaseInsensitiveOnTheMill() {
        assertThat(RopeSerial.parse("fat-2019-04412")).isEqualTo(new RopeSerial("FAT", 2019, "04412"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "FAT-2019", "F-2019-04412", "FATTY-2019-04412", "FAT-19-04412", "FAT-2019-123"})
    void aRopeSerialRefusesWhatIsNotOne(String text) {
        assertThatThrownBy(() -> RopeSerial.parse(text)).isInstanceOf(MeasureError.class);
    }

    @Test
    void aRopeReportsItsAge() {
        RopeSerial rope = RopeSerial.parse("FAT-2019-04412");
        assertThat(rope.ageIn(2025)).isEqualTo(6);
        assertThat(rope.ageIn(2019)).isZero();
        assertThatThrownBy(() -> rope.ageIn(2018)).isInstanceOf(RangeError.class);
    }

    @Test
    void aRopeSerialChecksItsOwnParts() {
        assertThatThrownBy(() -> new RopeSerial("FAT", 1800, "04412")).isInstanceOf(RangeError.class);
        assertThatThrownBy(() -> new RopeSerial("F4T", 2019, "04412")).isInstanceOf(ParseError.class);
        assertThatThrownBy(() -> new RopeSerial("FAT", 2019, "abcd")).isInstanceOf(ParseError.class);
    }

    @Test
    void aFilingNumberPadsItsSequence() {
        FilingNumber filing = new FilingNumber("UT", 2025, 147);
        assertThat(filing).hasToString("UT-2025-0147");
        assertThat(FilingNumber.parse("UT-2025-0147")).isEqualTo(filing);
        assertThat(FilingNumber.parse("ut-2025-0147")).isEqualTo(filing);
    }

    @Test
    void theFirstFilingOfAYearIsNumberOne() {
        assertThat(FilingNumber.firstOf("ut", 2025)).isEqualTo(new FilingNumber("UT", 2025, 1));
        assertThat(FilingNumber.firstOf("UT", 2025)).hasToString("UT-2025-0001");
    }

    @Test
    void filingsRunWithoutGaps() {
        FilingNumber first = FilingNumber.firstOf("UT", 2025);
        FilingNumber second = first.nextInSequence();
        assertThat(second.sequence()).isEqualTo(2);
        assertThat(second.follows(first)).isTrue();
        assertThat(new FilingNumber("UT", 2025, 4).follows(first)).isFalse();
        assertThat(new FilingNumber("CO", 2025, 2).follows(first)).isFalse();
        assertThat(new FilingNumber("UT", 2024, 2).follows(new FilingNumber("UT", 2024, 1)))
                .isTrue();
    }

    @Test
    void aFilingSequenceHasACeiling() {
        assertThatThrownBy(() -> new FilingNumber("UT", 2025, 9999).nextInSequence())
                .isInstanceOf(RangeError.class);
        assertThatThrownBy(() -> new FilingNumber("UT", 2025, 0)).isInstanceOf(RangeError.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "UT-2025", "U-2025-0147", "UT-25-0147", "UT-2025-147"})
    void aFilingNumberRefusesWhatIsNotOne(String text) {
        assertThatThrownBy(() -> FilingNumber.parse(text)).isInstanceOfAny(ParseError.class, RangeError.class);
    }

    @Test
    void filingsSortInIssueOrder() {
        Stream<FilingNumber> numbers = Stream.of(
                new FilingNumber("UT", 2025, 12), new FilingNumber("UT", 2024, 300), new FilingNumber("CO", 2025, 1));
        assertThat(numbers.sorted().map(FilingNumber::toString))
                .containsExactly("CO-2025-0001", "UT-2024-0300", "UT-2025-0012");
    }
}
