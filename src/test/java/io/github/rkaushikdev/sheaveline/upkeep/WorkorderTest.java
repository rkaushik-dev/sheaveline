package io.github.rkaushikdev.sheaveline.upkeep;

import static io.github.rkaushikdev.sheaveline.Hill.JOHN_PAUL;
import static io.github.rkaushikdev.sheaveline.Hill.STRAWBERRY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkorderTest {

    private static final LocalDate RAISED = LocalDate.of(2025, 1, 15);

    private static Defect defect(DefectLevel level) {
        return new Defect("JP-T4", level, "sheave liner cracked", RAISED);
    }

    private static Defect defect() {
        return defect(DefectLevel.DEFECT);
    }

    private static WorkOrder order(int number, Priority priority, Optional<LocalDate> closed, LiftCode lift) {
        return new WorkOrder(
                number,
                lift,
                RAISED,
                "M Vetter",
                "sheave liner cracked",
                priority,
                Optional.of("JP-T4"),
                closed,
                closed.map(any -> "M Vetter"));
    }

    private static WorkOrder order(int number, Priority priority, LiftCode lift) {
        return order(number, priority, Optional.empty(), lift);
    }

    private static WorkOrder order(int number, Priority priority) {
        return order(number, priority, JOHN_PAUL);
    }

    private static WorkOrder order(int number, LiftCode lift) {
        return order(number, Priority.SCHEDULED, lift);
    }

    private static WorkOrder order(int number) {
        return order(number, Priority.SCHEDULED);
    }

    private static WorkOrder order(Priority priority) {
        return order(1, priority);
    }

    private static WorkOrder order() {
        return order(1);
    }

    private static WorkOrder closed(Priority priority, LocalDate on) {
        return order(1, priority, Optional.of(on), JOHN_PAUL);
    }

    private static WorkOrder closed(LocalDate on) {
        return closed(Priority.SCHEDULED, on);
    }

    private static List<Integer> numbers(List<WorkOrder> orders) {
        return orders.stream().map(WorkOrder::number).toList();
    }

    @Test
    void aDefectLevelDecidesThePriority() {
        assertThat(Priority.forLevel(DefectLevel.SERIOUS)).isSameAs(Priority.IMMEDIATE);
        assertThat(Priority.forLevel(DefectLevel.DEFECT)).isSameAs(Priority.SCHEDULED);
        assertThat(Priority.forLevel(DefectLevel.OBSERVATION)).isSameAs(Priority.ROUTINE);
    }

    @Test
    void eachPriorityCarriesItsOwnTarget() {
        assertThat(Priority.IMMEDIATE.targetDays()).isZero();
        assertThat(Priority.URGENT.targetDays()).isEqualTo(3);
        assertThat(Priority.SCHEDULED.targetDays()).isEqualTo(30);
        assertThat(Priority.ROUTINE.targetDays()).isEqualTo(180);
    }

    @Test
    void onlyAnImmediateOrderStopsTheMachine() {
        assertThat(Priority.IMMEDIATE.stopsTheMachine()).isTrue();
        for (Priority priority : List.of(Priority.URGENT, Priority.SCHEDULED, Priority.ROUTINE)) {
            assertThat(priority.stopsTheMachine()).isFalse();
        }
    }

    @Test
    void anOrderRaisedFromADefectTakesItsPriorityFromIt() {
        WorkOrder made = WorkOrder.fromDefect(1, JOHN_PAUL, defect(DefectLevel.SERIOUS), "M Vetter");
        assertThat(made.priority()).isSameAs(Priority.IMMEDIATE);
        assertThat(made.where()).contains("JP-T4");
        assertThat(made.raised()).isEqualTo(RAISED);
    }

    @Test
    void anOrderChecksItsOwnRecord() {
        assertThatThrownBy(() -> order(0))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("work orders are numbered from one");
        assertThatThrownBy(() -> new WorkOrder(1, JOHN_PAUL, RAISED, " ", "a thing", Priority.ROUTINE))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("work order 1 is signed by whoever raised it");
        assertThatThrownBy(() -> new WorkOrder(1, JOHN_PAUL, RAISED, "M Vetter", "  ", Priority.ROUTINE))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("work order 1 says nothing");
    }

    @Test
    void anOrderCannotCloseBeforeItWasRaised() {
        assertThatThrownBy(() -> closed(LocalDate.of(2025, 1, 1)))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("work order 1 closed before it was raised");
    }

    @Test
    void anOrderIsClosedBySomebodyOnSomeDayOrNeither() {
        assertThatThrownBy(() -> new WorkOrder(
                        1,
                        JOHN_PAUL,
                        RAISED,
                        "M Vetter",
                        "a thing",
                        Priority.ROUTINE,
                        Optional.empty(),
                        Optional.of(RAISED),
                        Optional.empty()))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("a work order is closed by somebody on some day, or neither");
    }

    @Test
    void theDueDateFallsOutOfThePriority() {
        assertThat(order(Priority.IMMEDIATE).dueBy()).isEqualTo(RAISED);
        assertThat(order(Priority.URGENT).dueBy()).isEqualTo(LocalDate.of(2025, 1, 18));
        assertThat(order(Priority.SCHEDULED).dueBy()).isEqualTo(LocalDate.of(2025, 2, 14));
    }

    @Test
    void anOpenOrderGoesOverdueWhenItsDatePasses() {
        WorkOrder urgent = order(Priority.URGENT);
        assertThat(urgent.isOverdue(LocalDate.of(2025, 1, 18))).isFalse();
        assertThat(urgent.isOverdue(LocalDate.of(2025, 1, 19))).isTrue();
    }

    @Test
    void aClosedOrderIsOverdueOnlyIfItWasClosedLate() {
        WorkOrder onTime = closed(Priority.URGENT, LocalDate.of(2025, 1, 17));
        WorkOrder late = closed(Priority.URGENT, LocalDate.of(2025, 1, 25));
        assertThat(onTime.isOverdue(LocalDate.of(2025, 3, 1))).isFalse();
        assertThat(late.isOverdue(LocalDate.of(2025, 3, 1))).isTrue();
    }

    @Test
    void anOrderReportsItsAge() {
        assertThat(order().ageInDays(LocalDate.of(2025, 2, 14))).isEqualTo(30);
        assertThat(closed(LocalDate.of(2025, 1, 20)).ageInDays(LocalDate.of(2025, 3, 1)))
                .isEqualTo(5);
        assertThatThrownBy(() -> order().ageInDays(LocalDate.of(2025, 1, 1)))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("that day is before the order was raised");
    }

    @Test
    void closingAnOrderMakesANewOne() {
        WorkOrder openOrder = order();
        WorkOrder done = openOrder.close(LocalDate.of(2025, 1, 20), "M Vetter");
        assertThat(openOrder.isOpen()).isTrue();
        assertThat(done.isOpen()).isFalse();
        assertThatThrownBy(() -> done.close(LocalDate.of(2025, 1, 25), "M Vetter"))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("work order 1 is already closed");
        assertThatThrownBy(() -> openOrder.close(LocalDate.of(2025, 1, 20), "  "))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("a work order is closed by somebody");
    }

    @Test
    void anOrderPrintsAOneLiner() {
        assertThat(order()).hasToString("1 scheduled on JP, sheave liner cracked (open)");
    }

    @Test
    void aBookHandsOutNumbersFromOne() {
        WorkOrderBook book = WorkOrderBook.of();
        assertThat(book.nextNumber()).isEqualTo(1);
        book = book.raiseFrom(JOHN_PAUL, defect(), "M Vetter");
        assertThat(book.nextNumber()).isEqualTo(2);
        assertThat(book.size()).isEqualTo(1);
    }

    @Test
    void twoOrdersCannotShareANumber() {
        assertThatThrownBy(() -> WorkOrderBook.of(List.of(order(1), order(1))))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("two work orders share a number");
    }

    @Test
    void anOrderCanBeFetchedOrRefusedByNumber() {
        WorkOrderBook book = WorkOrderBook.of(List.of(order(1)));
        assertThat(book.get(1).number()).isEqualTo(1);
        assertThatThrownBy(() -> book.get(9)).isInstanceOf(UpkeepError.class).hasMessage("there is no work order 9");
    }

    @Test
    void closingThroughTheBookKeepsTheOrder() {
        WorkOrderBook book = WorkOrderBook.of(List.of(order(1), order(2)));
        WorkOrderBook after = book.close(1, LocalDate.of(2025, 1, 20), "M Vetter");
        assertThat(numbers(after.orders())).containsExactly(1, 2);
        assertThat(after.get(1).isOpen()).isFalse();
        assertThat(after.get(2).isOpen()).isTrue();
    }

    @Test
    void theOpenOrdersCanBeNarrowedToOneMachine() {
        WorkOrderBook book = WorkOrderBook.of(List.of(order(1, JOHN_PAUL), order(2, STRAWBERRY)));
        assertThat(book.openOrders()).hasSize(2);
        assertThat(book.openOrders(JOHN_PAUL)).hasSize(1);
    }

    @Test
    void theOverdueOrdersCanBeNarrowedTheSameWay() {
        WorkOrderBook book =
                WorkOrderBook.of(List.of(order(1, Priority.URGENT, JOHN_PAUL), order(2, Priority.ROUTINE, STRAWBERRY)));
        List<WorkOrder> late = book.overdue(LocalDate.of(2025, 2, 1));
        assertThat(numbers(late)).containsExactly(1);
        assertThat(book.overdue(LocalDate.of(2025, 2, 1), STRAWBERRY)).isEmpty();
    }

    @Test
    void anOpenImmediateOrderHoldsTheMachine() {
        WorkOrderBook book = WorkOrderBook.of(List.of(order(1, Priority.IMMEDIATE)));
        assertThat(book.holdingMachines()).contains(JOHN_PAUL);
        WorkOrderBook after = book.close(1, LocalDate.of(2025, 1, 16), "M Vetter");
        assertThat(after.holdingMachines()).isEmpty();
    }

    @Test
    void ordersCanBePulledOutByPriorityAndByPlace() {
        WorkOrderBook book = WorkOrderBook.of(List.of(order(1, Priority.URGENT), order(2, Priority.ROUTINE)));
        assertThat(numbers(book.ofPriority(Priority.URGENT))).containsExactly(1);
        assertThat(book.at("JP-T4")).hasSize(2);
        assertThat(book.at("JP-T9")).isEmpty();
    }

    @Test
    void aSeriousDefectRaisedAsAnOrderHoldsTheMachineAtOnce() {
        WorkOrderBook book = WorkOrderBook.of().raiseFrom(JOHN_PAUL, defect(DefectLevel.SERIOUS), "M Vetter");
        assertThat(book.holdingMachines()).isEqualTo(Set.of(JOHN_PAUL));
        assertThat(book.get(1).isOverdue(LocalDate.of(2025, 1, 16))).isTrue();
    }
}
