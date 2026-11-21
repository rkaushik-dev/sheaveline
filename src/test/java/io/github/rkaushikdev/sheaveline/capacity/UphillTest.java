package io.github.rkaushikdev.sheaveline.capacity;

import static io.github.rkaushikdev.sheaveline.Hill.johnPaul;
import static io.github.rkaushikdev.sheaveline.Hill.strawberry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Speed;
import io.github.rkaushikdev.sheaveline.plant.FilingNumber;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class UphillTest {

    private static final FilingNumber FILING = FilingNumber.parse("UT-2004-0311");

    private static UphillCapacity capacity(int carriers, int seats, String occupancy) {
        LineSpacing line = new LineSpacing(Length.ofFeet(10000), carriers, Speed.ofFeetPerMinute(1000));
        return new UphillCapacity(line, seats, Decimals.of(occupancy));
    }

    private static UphillCapacity capacity(int carriers) {
        return capacity(carriers, 4, "0.85");
    }

    private static UphillCapacity seated(int seats) {
        return capacity(100, seats, "0.85");
    }

    private static UphillCapacity occupied(String occupancy) {
        return capacity(100, 4, occupancy);
    }

    private static UphillCapacity capacity() {
        return capacity(100);
    }

    private static CapacityCertificate certificate(int pph, int carriers, LocalDate issued) {
        return new CapacityCertificate(FILING, issued, pph, carriers);
    }

    private static CapacityCertificate certificate(int pph) {
        return certificate(pph, 100, LocalDate.of(2004, 12, 3));
    }

    private static CapacityCertificate ratedWith(int carriers) {
        return certificate(2000, carriers, LocalDate.of(2004, 12, 3));
    }

    private static CapacityCertificate issued(LocalDate issued) {
        return certificate(2000, 100, issued);
    }

    @Test
    void carriersAnHourFallsOutOfTheInterval() {
        // A hundred foot spacing at a thousand feet a minute is six seconds.
        assertThat(capacity().spacing().interval().seconds()).isEqualTo(6);
        assertThat(capacity().carriersPerHour()).isEqualTo(600);
    }

    @Test
    void seatsAnHourIsEveryPlaceFilled() {
        assertThat(seated(4).seatsPerHour()).isEqualTo(2400);
        assertThat(seated(6).seatsPerHour()).isEqualTo(3600);
    }

    @Test
    void personsAnHourTakesTheLoadingEfficiencyOff() {
        assertThat(capacity(100, 4, "0.85").personsPerHour()).isEqualTo(2040);
        assertThat(capacity(100, 4, "1.0").personsPerHour()).isEqualTo(2400);
    }

    @Test
    void verticalFeetAnHourIsTheFigureThatComparesTwoLifts() {
        UphillCapacity quick = seated(4);
        assertThat(quick.verticalFeetPerHour(Length.ofFeet(1500))).isEqualTo(3_060_000);
        assertThatThrownBy(() -> quick.verticalFeetPerHour(Length.ofFeet(0))).isInstanceOf(CapacityError.class);
    }

    @Test
    void aCapacityChecksItsOwnNumbers() {
        assertThatThrownBy(() -> seated(0)).isInstanceOf(CapacityError.class);
        assertThatThrownBy(() -> occupied("0")).isInstanceOf(CapacityError.class);
        assertThatThrownBy(() -> occupied("1.2")).isInstanceOf(CapacityError.class);
    }

    @Test
    void aCapacityBuiltFromALiftReadsItsCarrierType() {
        UphillCapacity built = UphillCapacity.of(johnPaul());
        assertThat(built.seats()).isEqualTo(4);
        assertThat(built.personsPerHour()).isGreaterThan(1500);
        assertThat(UphillCapacity.of(strawberry()).seats()).isEqualTo(3);
    }

    @Test
    void aWorseLoadingEfficiencyMovesOnlyThePersonsFigure() {
        UphillCapacity good = occupied("0.90");
        UphillCapacity poor = good.atOccupancy("0.60");
        assertThat(poor.seatsPerHour()).isEqualTo(good.seatsPerHour());
        assertThat(poor.personsPerHour()).isLessThan(good.personsPerHour());
    }

    @Test
    void thinningTheLineCostsCapacity() {
        UphillCapacity full = capacity(100);
        UphillCapacity thinned = full.withSpacing(full.spacing().withCarriers(50));
        assertThat(thinned.personsPerHour()).isLessThan(full.personsPerHour());
    }

    @Test
    void theCarriersNeededToReachATarget() {
        UphillCapacity line = capacity(100, 4, "0.85");
        assertThat(line.personsPerHour()).isEqualTo(2040);
        assertThat(line.carriersForPersonsPerHour(2040)).isEqualTo(100);
        assertThat(line.carriersForPersonsPerHour(1020)).isEqualTo(50);
        assertThat(line.carriersForPersonsPerHour(1021)).isEqualTo(51);
    }

    @Test
    void aTargetOfNothingHasNoAnswer() {
        assertThatThrownBy(() -> capacity().carriersForPersonsPerHour(0)).isInstanceOf(CapacityError.class);
    }

    @Test
    void aCapacityPrintsBothFigures() {
        assertThat(capacity()).hasToString("2,040 people an hour, 2,400 seats");
    }

    @Test
    void aCertificateChecksItsOwnNumbers() {
        assertThatThrownBy(() -> certificate(0)).isInstanceOf(CapacityError.class);
        assertThatThrownBy(() -> ratedWith(0)).isInstanceOf(CapacityError.class);
    }

    @Test
    void aMachineRunningAtItsFiledFigureAgreesWithTheCertificate() {
        CapacityCertificate filed = certificate(2040);
        assertThat(filed.agreesWith(capacity())).isTrue();
        assertThat(filed.exceededBy(capacity())).isFalse();
    }

    @Test
    void theBandIsTwoPercentEitherSide() {
        assertThat(certificate(2000).band()).isEqualTo(new CapacityCertificate.Band(1960, 2040));
    }

    @Test
    void aMachineRunningAboveTheBandIsAMatterForTheBoard() {
        CapacityCertificate filed = certificate(1800);
        assertThat(filed.agreesWith(capacity())).isFalse();
        assertThat(filed.exceededBy(capacity())).isTrue();
    }

    @Test
    void aMachineRunningUnderTheBandDisagreesWithoutExceeding() {
        CapacityCertificate filed = certificate(2600);
        assertThat(filed.agreesWith(capacity())).isFalse();
        assertThat(filed.exceededBy(capacity())).isFalse();
    }

    @Test
    void theCertificateNoticesCarriersAddedSinceItWasIssued() {
        CapacityCertificate filed = ratedWith(84);
        assertThat(filed.carriersChanged(capacity(100))).isEqualTo(16);
        assertThat(filed.carriersChanged(capacity(70))).isEqualTo(-14);
    }

    @Test
    void aCertificateReportsItsAge() {
        CapacityCertificate filed = issued(LocalDate.of(2004, 12, 3));
        assertThat(filed.ageInYears(LocalDate.of(2025, 1, 15))).isEqualTo(20);
        assertThat(filed.ageInYears(LocalDate.of(2024, 12, 2))).isEqualTo(19);
        assertThatThrownBy(() -> filed.ageInYears(LocalDate.of(2000, 1, 1))).isInstanceOf(CapacityError.class);
    }

    @Test
    void aCertificatePrintsItsFilingNumber() {
        assertThat(certificate(2040)).hasToString("UT-2004-0311, 2,040 an hour");
    }
}
