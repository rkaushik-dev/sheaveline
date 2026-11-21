package io.github.rkaushikdev.sheaveline.ops;

import static io.github.rkaushikdev.sheaveline.Hill.johnPaul;
import static io.github.rkaushikdev.sheaveline.Hill.strawberry;
import static io.github.rkaushikdev.sheaveline.Hill.wildcat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.plant.Lift;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class PreopTest {

    private static final LocalDate DAY = LocalDate.of(2025, 1, 15);

    private static CheckItem item(String code) {
        return PreOpSheet.STANDARD_SHEET.stream()
                .filter(candidate -> candidate.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no item " + code));
    }

    private static CheckResult result(String code, CheckOutcome outcome, String note, String by) {
        return new CheckResult(item(code), outcome, by, note);
    }

    private static CheckResult result(String code, CheckOutcome outcome, String note) {
        return result(code, outcome, note, "M Vetter");
    }

    private static CheckResult result(String code) {
        return result(code, CheckOutcome.PASS, "");
    }

    private static List<CheckResult> allPassing(Lift machine, String mechanic) {
        return PreOpSheet.STANDARD_SHEET.stream()
                .filter(one -> one.appliesTo(machine))
                .map(one -> new CheckResult(one, CheckOutcome.PASS, mechanic))
                .toList();
    }

    private static PreOpSheet sheet(Lift machine, List<CheckResult> results, String mechanic, String operator) {
        return new PreOpSheet(machine, DAY, results, mechanic, operator);
    }

    private static PreOpSheet sheet(Lift machine, List<CheckResult> results) {
        return sheet(machine, results, "M Vetter", "K Ostler");
    }

    private static PreOpSheet sheet(Lift machine) {
        return sheet(machine, allPassing(machine, "M Vetter"));
    }

    private static PreOpSheet sheet() {
        return sheet(johnPaul());
    }

    private static PreOpSheet signedBy(String mechanic, String operator) {
        Lift machine = johnPaul();
        return sheet(machine, allPassing(machine, mechanic), mechanic, operator);
    }

    /** Every line of the machine's sheet passing, except one line with the given outcome. */
    private static List<CheckResult> allPassingBut(Lift machine, String code, CheckOutcome outcome, String note) {
        return PreOpSheet.STANDARD_SHEET.stream()
                .filter(one -> one.appliesTo(machine))
                .map(one -> one.code().equals(code)
                        ? new CheckResult(one, outcome, "M Vetter", note)
                        : new CheckResult(one, CheckOutcome.PASS, "M Vetter", ""))
                .toList();
    }

    private static Set<String> codes(List<CheckItem> items) {
        return items.stream().map(CheckItem::code).collect(Collectors.toSet());
    }

    @Test
    void theSheetAMachineGetsDependsOnWhatItIs() {
        PreOpSheet quad = sheet(johnPaul());
        PreOpSheet triple = sheet(strawberry());
        PreOpSheet tow = sheet(wildcat());
        assertThat(codes(quad.items())).contains("C1");
        assertThat(codes(triple.items())).doesNotContain("C1");
        assertThat(codes(triple.items())).contains("A2");
        assertThat(codes(tow.items())).doesNotContain("A2");
        assertThat(codes(tow.items())).doesNotContain("D3");
    }

    @Test
    void everyMachineStillGetsTheCommonLines() {
        for (Lift machine : List.of(johnPaul(), strawberry(), wildcat())) {
            assertThat(codes(sheet(machine).items())).contains("A1", "B1", "B2", "D1", "D2");
        }
    }

    @Test
    void aWalkedSheetWithEverythingPassingOpensTheLift() {
        PreOpSheet walked = sheet();
        assertThat(walked.isComplete()).isTrue();
        assertThat(walked.mayOpen()).isTrue();
        assertThat(walked.blockers()).isEmpty();
        assertThat(walked).hasToString("JP 2025-01-15, clear to open");
    }

    @Test
    void anUnwalkedLineStopsTheLiftOpening() {
        Lift machine = johnPaul();
        List<CheckResult> partial = PreOpSheet.STANDARD_SHEET.stream()
                .filter(one -> one.appliesTo(machine) && !one.code().equals("B2"))
                .map(one -> new CheckResult(one, CheckOutcome.PASS, "M Vetter"))
                .toList();
        PreOpSheet walked = sheet(machine, partial);
        assertThat(walked.isComplete()).isFalse();
        assertThat(walked.missing()).extracting(CheckItem::code).containsExactly("B2");
        assertThat(walked.mayOpen()).isFalse();
        assertThat(walked.blockers()).containsExactly("B2 not walked");
        assertThat(walked).hasToString("JP 2025-01-15, not clear");
    }

    @Test
    void aCriticalFailureStopsTheLift() {
        Lift machine = johnPaul();
        PreOpSheet walked =
                sheet(machine, allPassingBut(machine, "B2", CheckOutcome.FAIL, "rope out of the train at T4"));
        assertThat(walked.isComplete()).isTrue();
        assertThat(walked.mayOpen()).isFalse();
        assertThat(walked.criticalFailures())
                .extracting(one -> one.item().code())
                .containsExactly("B2");
        assertThat(walked.blockers()).containsExactly("B2 failed");
    }

    @Test
    void aFailureOnSomethingThatIsNotCriticalDoesNotStopIt() {
        Lift machine = johnPaul();
        PreOpSheet walked = sheet(machine, allPassingBut(machine, "E2", CheckOutcome.FAIL, "maze sign blown over"));
        assertThat(walked.failures()).isNotEmpty();
        assertThat(walked.criticalFailures()).isEmpty();
        assertThat(walked.mayOpen()).isTrue();
    }

    @Test
    void aNonCriticalLineCanBeDeferred() {
        CheckResult deferred = result("E1", CheckOutcome.DEFERRED, "cat is on it, will groom at ten");
        assertThat(deferred.outcome()).isSameAs(CheckOutcome.DEFERRED);
        assertThat(deferred.outcome().isSatisfied()).isFalse();
    }

    @Test
    void aCriticalLineCannotBeDeferred() {
        assertThatThrownBy(() -> result("B1", CheckOutcome.DEFERRED, "will ride it later"))
                .isInstanceOf(OperationsError.class)
                .hasMessage("B1 is critical and cannot be deferred");
    }

    @Test
    void anythingThatDidNotPassCarriesANote() {
        assertThatThrownBy(() -> result("E2", CheckOutcome.FAIL, ""))
                .isInstanceOf(OperationsError.class)
                .hasMessage("E2 did not pass and carries no note");
        assertThatThrownBy(() -> result("E1", CheckOutcome.DEFERRED, ""))
                .isInstanceOf(OperationsError.class)
                .hasMessage("E1 did not pass and carries no note");
    }

    @Test
    void aResultIsSigned() {
        assertThatThrownBy(() -> result("A1", CheckOutcome.PASS, "", "  "))
                .isInstanceOf(OperationsError.class)
                .hasMessage("A1 is signed by whoever walked it");
    }

    @Test
    void aSheetCarriesAMechanicAndAnOperator() {
        assertThatThrownBy(() -> signedBy("  ", "K Ostler")).isInstanceOf(OperationsError.class);
        assertThatThrownBy(() -> signedBy("M Vetter", ""))
                .isInstanceOf(OperationsError.class)
                .hasMessage("a sheet carries a mechanic and an operator");
    }

    @Test
    void anItemIsWalkedOnce() {
        assertThatThrownBy(() -> sheet(johnPaul(), List.of(result("A1"), result("A1"))))
                .isInstanceOf(OperationsError.class)
                .hasMessage("an item is walked once");
    }

    @Test
    void anItemThatIsNotOnThisMachineIsRefused() {
        assertThatThrownBy(() -> sheet(strawberry(), List.of(result("C1"))))
                .isInstanceOf(OperationsError.class)
                .hasMessage("C1 is not on a SB sheet");
    }

    @Test
    void aResultCanBeLookedUpByCode() {
        PreOpSheet walked = sheet();
        assertThat(walked.resultFor("B1")).map(found -> found.item().code()).hasValue("B1");
        assertThat(walked.resultFor("Z9")).isEmpty();
    }

    @Test
    void theDeferralsAreListedForTheWorkOrderBook() {
        Lift machine = johnPaul();
        PreOpSheet walked =
                sheet(machine, allPassingBut(machine, "E3", CheckOutcome.DEFERRED, "kit is being restocked"));
        assertThat(walked.deferrals()).extracting(one -> one.item().code()).containsExactly("E3");
        assertThat(walked.mayOpen()).isTrue();
    }

    @Test
    void theBrakeLinesCanBePulledOutOnTheirOwn() {
        assertThat(codes(PreOpSheet.brakeItems())).isEqualTo(Set.of("A1", "A2", "A3"));
    }

    @Test
    void anItemPrintsItsCodeAndWhatItIs() {
        assertThat(item("B1")).hasToString("B1 line ridden top to bottom");
        assertThat(result("B1")).hasToString("B1 pass");
        assertThat(result("E2", CheckOutcome.FAIL, "blown over")).hasToString("E2 fail (blown over)");
    }

    @Test
    void aCustomItemCanBeWrittenForOneClass() {
        CheckItem special =
                new CheckItem("Z1", "tram car doors", true, Set.of(johnPaul().liftClass()));
        assertThat(special.appliesTo(johnPaul())).isTrue();
        assertThat(special.appliesTo(strawberry())).isFalse();
    }
}
