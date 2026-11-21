package io.github.rkaushikdev.sheaveline.reports;

import static io.github.rkaushikdev.sheaveline.Hill.JOHN_PAUL;
import static io.github.rkaushikdev.sheaveline.Hill.STRAWBERRY;
import static io.github.rkaushikdev.sheaveline.Hill.WILDCAT;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.upkeep.Priority;
import io.github.rkaushikdev.sheaveline.upkeep.ServiceStatus;
import io.github.rkaushikdev.sheaveline.upkeep.WorkOrder;
import io.github.rkaushikdev.sheaveline.weather.Hold;
import io.github.rkaushikdev.sheaveline.weather.HoldReason;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class MorningTest {

    private static final LocalDate DAY = LocalDate.of(2025, 1, 15);

    private static WorkOrder order() {
        return order(1, JOHN_PAUL, Priority.IMMEDIATE);
    }

    private static WorkOrder order(int number, LiftCode lift, Priority priority) {
        return new WorkOrder(
                number,
                lift,
                DAY,
                "M Vetter",
                "crossarm bolt sheared",
                priority,
                Optional.of("JP-T7"),
                Optional.empty(),
                Optional.empty());
    }

    private static Machine machine() {
        return new Machine();
    }

    private static Morning morning(Machine... machines) {
        List<MachineLine> lines = Stream.of(machines).map(Machine::line).toList();
        return Morning.of(
                DAY, "Ogden Peak", "46 in", lines.isEmpty() ? List.of(machine().line()) : lines);
    }

    @Test
    void aMachineWithNothingAgainstItIsOpening() {
        MachineLine one = machine().line();
        assertThat(one.opening()).isTrue();
        assertThat(one.verdict()).isEqualTo("opening");
        assertThat(one.reasons()).isEmpty();
    }

    @Test
    void anUnsignedMorningWalkStopsIt() {
        MachineLine one = machine().unwalked().line();
        assertThat(one.opening()).isFalse();
        assertThat(one.verdict()).isEqualTo("not opening");
        assertThat(one.reasons()).contains("morning walk not signed");
    }

    @Test
    void aBlockerStopsItAndIsNamed() {
        MachineLine one = machine().blockers("B2 failed").line();
        assertThat(one.opening()).isFalse();
        assertThat(one.reasons()).contains("B2 failed");
    }

    @Test
    void aWeatherHoldIsToldApartFromABlocker() {
        MachineLine held = machine()
                .holds(new Hold(HoldReason.GUST, "52 mph against a limit of 45 mph"))
                .line();
        assertThat(held.verdict()).isEqualTo("held");
        assertThat(held.reasons()).contains("gust hold");
        MachineLine both = machine()
                .holds(new Hold(HoldReason.GUST, "52 mph"))
                .blockers("B2 failed")
                .line();
        assertThat(both.verdict()).isEqualTo("not opening");
    }

    @Test
    void aMachineOutOfServiceIsNotOpeningWhateverElseIsTrue() {
        MachineLine one = machine().status(ServiceStatus.OUT_OF_SERVICE).line();
        assertThat(one.opening()).isFalse();
        assertThat(one.reasons()).contains("out of service");
    }

    @Test
    void aRestrictedMachineStillOpens() {
        MachineLine one = machine().status(ServiceStatus.RESTRICTED).line();
        assertThat(one.opening()).isTrue();
    }

    @Test
    void theReportCountsWhatIsOpening() {
        Morning page = morning(
                machine().lift(JOHN_PAUL),
                machine().lift(STRAWBERRY).name("Strawberry").unwalked(),
                machine().lift(WILDCAT).name("Wildcat"));
        assertThat(page.opening()).hasSize(2);
        assertThat(page.held()).hasSize(1);
        String text = page.text();
        assertThat(text).contains("machines opening         2 of 3");
    }

    @Test
    void thePageHeadsWithTheResortAndTheDay() {
        String text = morning().text();
        assertThat(text).contains("OGDEN PEAK MORNING REPORT");
        assertThat(text).contains("day                      2025-01-15");
        assertThat(text).contains("settled snow             46 in");
    }

    @Test
    void everyMachineGetsARow() {
        Morning page =
                morning(machine().lift(JOHN_PAUL), machine().lift(STRAWBERRY).name("Strawberry"));
        String text = page.text();
        assertThat(text).contains("JP    John Paul");
        assertThat(text).contains("SB    Strawberry");
    }

    @Test
    void theReasonsArePrintedUnderTheMachineTheyBelongTo() {
        String text = morning(
                        machine().lift(JOHN_PAUL).blockers("B2 failed", "D1 not walked"),
                        machine().lift(STRAWBERRY).name("Strawberry"))
                .text();
        assertThat(text).contains("what is stopping them");
        assertThat(text).contains("JP John Paul");
        assertThat(text).contains("    B2 failed");
        assertThat(text).contains("    D1 not walked");
    }

    @Test
    void aCleanMorningPrintsNoStoppingSection() {
        assertThat(morning().text()).doesNotContain("what is stopping them");
    }

    @Test
    void anOrderThatStopsAMachineGetsItsOwnSection() {
        String text = morning(machine().orders(order())).text();
        assertThat(text).contains("work orders that stop a machine");
        assertThat(text).contains("crossarm bolt sheared");
    }

    @Test
    void anOrdinaryOrderDoesNotReachThatSection() {
        String text = morning(machine().orders(order(1, JOHN_PAUL, Priority.SCHEDULED)))
                .text();
        assertThat(text).doesNotContain("work orders that stop a machine");
    }

    @Test
    void theJobsFallingDueAreListedWithTheirMachine() {
        String text = morning(machine().due("A1", "H1")).text();
        assertThat(text).contains("falling due");
        assertThat(text).contains("JP    A1");
        assertThat(text).contains("JP    H1");
    }

    @Test
    void nothingFallingDuePrintsNoSection() {
        assertThat(morning().text()).doesNotContain("falling due");
    }

    @Test
    void theWholePageFitsEightyColumns() {
        Morning page = morning(
                machine().lift(JOHN_PAUL).blockers("B2 failed").orders(order()).due("A1"),
                machine().lift(STRAWBERRY).name("Strawberry").status(ServiceStatus.RESTRICTED),
                machine().lift(WILDCAT).name("Wildcat").holds(new Hold(HoldReason.CHILL, "-31 F")));
        assertThat(page.render().lines())
                .allSatisfy(one -> assertThat(one.length()).isLessThanOrEqualTo(Forms.PAGE_WIDTH));
    }

    @Test
    void thePageSaysWhatItWasPrintedFor() {
        assertThat(morning().text().stripTrailing()).endsWith("printed for Ogden Peak, 2025-01-15");
    }

    @Test
    void theUrgentOrdersAreGatheredAcrossEveryMachine() {
        Morning page = morning(
                machine().lift(JOHN_PAUL).orders(order(1, JOHN_PAUL, Priority.IMMEDIATE)),
                machine().lift(STRAWBERRY).name("Strawberry").orders(order(2, STRAWBERRY, Priority.IMMEDIATE)));
        assertThat(page.urgentOrders()).extracting(WorkOrder::number).containsExactly(1, 2);
    }

    /** One machine's morning, built the way a test wants it. */
    private static final class Machine {

        private LiftCode lift = JOHN_PAUL;
        private String name = "John Paul";
        private ServiceStatus status = ServiceStatus.IN_SERVICE;
        private boolean walked = true;
        private List<String> blockers = List.of();
        private List<Hold> holds = List.of();
        private List<WorkOrder> orders = List.of();
        private List<String> due = List.of();

        Machine lift(LiftCode value) {
            lift = value;
            return this;
        }

        Machine name(String value) {
            name = value;
            return this;
        }

        Machine status(ServiceStatus value) {
            status = value;
            return this;
        }

        Machine unwalked() {
            walked = false;
            return this;
        }

        Machine blockers(String... values) {
            blockers = List.of(values);
            return this;
        }

        Machine holds(Hold... values) {
            holds = List.of(values);
            return this;
        }

        Machine orders(WorkOrder... values) {
            orders = List.of(values);
            return this;
        }

        Machine due(String... values) {
            due = List.of(values);
            return this;
        }

        MachineLine line() {
            return new MachineLine(lift, name, status, walked, blockers, holds, orders, due);
        }
    }
}
