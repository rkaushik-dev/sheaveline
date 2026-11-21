package io.github.rkaushikdev.sheaveline.upkeep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import io.github.rkaushikdev.sheaveline.plant.LiftClass;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LibraryTest {

    @Test
    void everyCodeInTheLibraryIsUnique() {
        List<String> codes =
                Library.STANDARD_LIBRARY.stream().map(Requirement::code).toList();
        assertThat(new HashSet<>(codes)).hasSameSizeAs(codes);
    }

    @Test
    void everyJobBelongsToAtLeastOneClass() {
        for (Requirement job : Library.STANDARD_LIBRARY) {
            assertThat(job.classes()).isNotEmpty();
        }
    }

    @Test
    void everyCodeStartsWithTheLetterForItsInterval() {
        for (Requirement job : Library.STANDARD_LIBRARY) {
            Interval interval = job.interval();
            switch (job.code().charAt(0)) {
                case 'D' -> assertThat(interval.days()).contains(1);
                case 'W' -> assertThat(interval.days()).contains(7);
                case 'M' -> assertThat(interval.days()).contains(30);
                case 'A' -> assertThat(interval.days()).contains(365);
                case 'F' -> assertThat(interval.days()).contains(1826);
                case 'H' -> assertThat(interval.runsOn(Basis.HOURS)).isTrue();
                case 'C' -> assertThat(interval.runsOn(Basis.CYCLES)).isTrue();
                default -> fail(job.code() + " does not follow the naming");
            }
        }
    }

    @Test
    void aJobCanBeFetchedByCodeInEitherCase() {
        assertThat(Library.requirement("A1").description()).startsWith("annual inspection");
        assertThat(Library.requirement("a1")).isEqualTo(Library.requirement("A1"));
    }

    @Test
    void askingForAJobThatIsNotInTheLibrary() {
        assertThatThrownBy(() -> Library.requirement("Z9"))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("there is no job Z9");
    }

    @Test
    void aSurfaceTowCarriesFarFewerJobsThanADetachable() {
        List<String> tow = Library.codesFor(LiftClass.SURFACE_TOW);
        List<String> quad = Library.codesFor(LiftClass.DETACHABLE_CHAIR);
        assertThat(tow.size()).isLessThan(quad.size());
        Set<String> towCodes = new HashSet<>(tow);
        Set<String> quadCodes = new HashSet<>(quad);
        assertThat(quadCodes).containsAll(towCodes).isNotEqualTo(towCodes);
    }

    @Test
    void theEvacuationJobsAreOnlyOnAerialMachines() {
        for (String code : List.of("M3", "A4")) {
            assertThat(Library.codesFor(LiftClass.FIXED_CHAIR)).contains(code);
            assertThat(Library.codesFor(LiftClass.SURFACE_TOW)).doesNotContain(code);
        }
    }

    @Test
    void theGripJobsAreOnlyOnDetachables() {
        for (String code : List.of("W2", "C1")) {
            assertThat(Library.codesFor(LiftClass.DETACHABLE_CHAIR)).contains(code);
            assertThat(Library.codesFor(LiftClass.FIXED_CHAIR)).doesNotContain(code);
        }
    }

    @Test
    void theDailyJobsAreOnEveryMachine() {
        for (LiftClass liftClass : LiftClass.values()) {
            assertThat(Library.codesFor(liftClass)).contains("D1", "D2", "D3");
        }
    }

    @Test
    void theLibraryForAClassMatchesWhatAppliesToIt() {
        for (LiftClass liftClass : LiftClass.values()) {
            for (Requirement job : Library.libraryFor(liftClass)) {
                assertThat(job.appliesTo(liftClass)).isTrue();
            }
        }
    }

    @Test
    void onlyThePaintJobIsNotCritical() {
        for (LiftClass liftClass : LiftClass.values()) {
            Set<String> soft = new HashSet<>(Library.codesFor(liftClass));
            soft.removeAll(Library.criticalCodesFor(liftClass));
            assertThat(soft).isEqualTo(Set.of("A5"));
        }
    }

    @Test
    void theTwoClockJobsCarryBothACalendarAndAMeter() {
        for (String code : List.of("H1", "H2", "C1")) {
            Requirement job = Library.requirement(code);
            assertThat(job.interval().runsOn(Basis.CALENDAR)).isTrue();
            assertThat(job.interval().bases()).hasSize(2);
        }
    }

    @Test
    void theHourBasedJobsAreTheOnesWithHoursOnThem() {
        List<String> hourly = Library.STANDARD_LIBRARY.stream()
                .filter(job -> job.interval().runsOn(Basis.HOURS))
                .map(Requirement::code)
                .toList();
        assertThat(hourly).containsExactly("H1", "H2");
    }

    @Test
    void theFiveYearlyJobsAreTheShaftTests() {
        List<String> five = Library.STANDARD_LIBRARY.stream()
                .filter(job -> job.interval().days().equals(Optional.of(1826)))
                .map(Requirement::code)
                .toList();
        assertThat(five).containsExactly("F1", "F2");
    }

    @Test
    void aDailyJobHasNoGraceAndAnAnnualOneDoes() {
        assertThat(Library.requirement("D1").interval().graceDays()).isZero();
        assertThat(Library.requirement("A1").interval().graceDays()).isEqualTo(30);
    }

    @Test
    void everyDescriptionReadsAsAnInstruction() {
        for (Requirement job : Library.STANDARD_LIBRARY) {
            assertThat(job.description()).isEqualTo(job.description().strip());
            assertThat(Character.isLowerCase(job.description().charAt(0))).isTrue();
        }
    }
}
